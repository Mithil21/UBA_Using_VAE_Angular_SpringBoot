"""
UBA Variational Autoencoder — Full Version
===========================================
Architecture:
  Encoder: input_dim -> 64 -> 32 -> latent_dim (6)
  Decoder: latent_dim -> 32 -> 64 -> input_dim

Key design decisions:
  - latent_dim=6 for 28 input features (forces aggressive compression)
  - KL annealing: beta ramps 0.0 -> 1.0 over first 100 epochs
    prevents KL term from dominating early training
  - Dropout(0.1) in encoder only — regularisation without hurting reconstruction
  - Threshold set at 95th percentile of normal reconstruction errors,
    then overridden with ROC-optimal (Youden J) threshold after evaluation
  - ONNX export for Java Spring Boot inference via ONNX Runtime
  - Scaler constants printer so Java VAEAnalysis.java stays in sync

Usage:
  1. Run data_generator.py first to produce .npy files
  2. python vae_model.py
  3. Paste printed SCALER_MEAN / SCALER_SCALE / THRESHOLD into VAEAnalysis.java
"""

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
import matplotlib
matplotlib.use('Agg')  # headless — no display needed
import matplotlib.pyplot as plt
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    roc_curve, auc,
    classification_report,
    confusion_matrix,
)


# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------

class VAE(nn.Module):
    """
    Variational Autoencoder for user behaviour anomaly detection.
    Compact latent space (dim=6) ensures anomalous inputs cannot be
    reconstructed accurately, producing high MSE reconstruction error.
    """

    def __init__(self, input_dim: int, hidden_dim: int = 64, latent_dim: int = 6):
        super().__init__()

        # Encoder — dropout only here to regularise latent representation
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(hidden_dim, hidden_dim // 2),
            nn.BatchNorm1d(hidden_dim // 2),
            nn.ReLU(),
            nn.Dropout(0.1),
        )

        self.mu_layer     = nn.Linear(hidden_dim // 2, latent_dim)
        self.logvar_layer = nn.Linear(hidden_dim // 2, latent_dim)

        # Decoder — no dropout; we want clean reconstruction for normal inputs
        self.decoder = nn.Sequential(
            nn.Linear(latent_dim, hidden_dim // 2),
            nn.BatchNorm1d(hidden_dim // 2),
            nn.ReLU(),
            nn.Linear(hidden_dim // 2, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, input_dim),
        )

    def encode(self, x):
        h      = self.encoder(x)
        mu     = self.mu_layer(h)
        logvar = self.logvar_layer(h)
        return mu, logvar

    def reparameterize(self, mu, logvar):
        std = torch.exp(0.5 * logvar)
        eps = torch.randn_like(std)
        return mu + eps * std

    def decode(self, z):
        return self.decoder(z)

    def forward(self, x):
        mu, logvar = self.encode(x)
        z          = self.reparameterize(mu, logvar)
        recon_x    = self.decode(z)
        return recon_x, mu, logvar


# ---------------------------------------------------------------------------
# Loss
# ---------------------------------------------------------------------------

def vae_loss(recon_x, x, mu, logvar, beta: float = 1.0):
    """
    ELBO loss = reconstruction loss + beta * KL divergence.
    beta is annealed from 0 to 1 during training so reconstruction
    quality is established before the KL term kicks in.
    """
    recon_loss = F.mse_loss(recon_x, x, reduction='sum')
    kld_loss   = -0.5 * torch.sum(1 + logvar - mu.pow(2) - logvar.exp())
    return recon_loss + beta * kld_loss


# ---------------------------------------------------------------------------
# Trainer / predictor wrapper
# ---------------------------------------------------------------------------

class UserBehaviorVAE:

    def __init__(self, input_dim: int, hidden_dim: int = 64, latent_dim: int = 6):
        self.device    = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model     = VAE(input_dim, hidden_dim, latent_dim).to(self.device)
        self.scaler    = StandardScaler()
        self.threshold = None
        self.input_dim = input_dim
        print(f"[VAE] input_dim={input_dim}  hidden_dim={hidden_dim}  "
              f"latent_dim={latent_dim}  device={self.device}")

    # -----------------------------------------------------------------------
    # Training
    # -----------------------------------------------------------------------

    def train(self,
              X: np.ndarray,
              epochs: int      = 300,
              batch_size: int  = 64,
              lr: float        = 1e-3,
              kl_anneal_epochs: int = 100):
        """
        Train on normal-only data (unsupervised).
        KL beta ramps linearly from 0 to 1 over kl_anneal_epochs.
        Early stopping with patience=40.
        """
        X_scaled = self.scaler.fit_transform(X)
        X_tensor = torch.FloatTensor(X_scaled).to(self.device)

        optimizer  = torch.optim.Adam(
            self.model.parameters(), lr=lr, weight_decay=1e-4
        )
        scheduler  = torch.optim.lr_scheduler.ReduceLROnPlateau(
            optimizer, patience=20, factor=0.5, min_lr=1e-6
        )
        dataset    = torch.utils.data.TensorDataset(X_tensor)
        dataloader = torch.utils.data.DataLoader(
            dataset, batch_size=batch_size, shuffle=True, drop_last=False
        )

        best_loss        = float('inf')
        best_state       = None
        patience_counter = 0
        train_losses     = []

        self.model.train()

        for epoch in range(epochs):
            # KL annealing — beta ramps 0 -> 1 over kl_anneal_epochs
            beta       = min(1.0, epoch / max(kl_anneal_epochs, 1))
            epoch_loss = 0.0

            for (data,) in dataloader:
                optimizer.zero_grad()
                recon, mu, logvar = self.model(data)
                loss = vae_loss(recon, data, mu, logvar, beta=beta)
                loss.backward()
                # Gradient clipping — prevents exploding gradients
                nn.utils.clip_grad_norm_(self.model.parameters(), max_norm=1.0)
                optimizer.step()
                epoch_loss += loss.item()

            avg_loss = epoch_loss / len(X_scaled)
            train_losses.append(avg_loss)
            scheduler.step(avg_loss)

            if avg_loss < best_loss:
                best_loss    = avg_loss
                best_state   = {k: v.clone() for k, v in self.model.state_dict().items()}
                patience_counter = 0
            else:
                patience_counter += 1

            if epoch % 25 == 0:
                print(f"  Epoch {epoch:>4}  loss={avg_loss:.4f}  "
                      f"beta={beta:.3f}  "
                      f"lr={optimizer.param_groups[0]['lr']:.6f}")

            if patience_counter >= 40:
                print(f"  Early stopping at epoch {epoch}")
                break

        # Restore best weights
        if best_state:
            self.model.load_state_dict(best_state)

        self._calculate_threshold(X_scaled)
        self._plot_training_loss(train_losses)
        print(f"[VAE] Training complete — best loss: {best_loss:.4f}")

    # -----------------------------------------------------------------------
    # Threshold calibration
    # -----------------------------------------------------------------------

    def _calculate_threshold(self, X_normal_scaled: np.ndarray):
        """
        Sets anomaly threshold at 95th percentile of normal reconstruction errors.
        This is overridden later by ROC-optimal threshold if evaluation data exists.
        """
        self.model.eval()
        with torch.no_grad():
            X_t    = torch.FloatTensor(X_normal_scaled).to(self.device)
            recon, _, _ = self.model(X_t)
            errors = torch.mean((X_t - recon) ** 2, dim=1).cpu().numpy()

        p95            = np.percentile(errors, 95)
        self.threshold = float(p95)

        print(f"[Threshold] 95th percentile = {p95:.6f}  "
              f"(mean={np.mean(errors):.6f}  std={np.std(errors):.6f})")

    # -----------------------------------------------------------------------
    # Prediction
    # -----------------------------------------------------------------------

    def get_reconstruction_error(self, X: np.ndarray) -> np.ndarray:
        X_scaled = self.scaler.transform(
            X.reshape(1, -1) if X.ndim == 1 else X
        )
        self.model.eval()
        with torch.no_grad():
            X_t         = torch.FloatTensor(X_scaled).to(self.device)
            recon, _, _ = self.model(X_t)
            errors      = torch.mean((X_t - recon) ** 2, dim=1).cpu().numpy()
        return errors[0] if X.ndim == 1 else errors

    def predict(self, X: np.ndarray) -> np.ndarray:
        """Returns 1 for anomaly (bot), 0 for normal."""
        errors = self.get_reconstruction_error(X)
        return (errors > self.threshold).astype(int)

    # -----------------------------------------------------------------------
    # ROC evaluation + optimal threshold
    # -----------------------------------------------------------------------

    def evaluate_with_roc(self,
                          X_test: np.ndarray,
                          y_test: np.ndarray,
                          save_path: str = 'roc_curve.png'):
        """
        Plots ROC curve, computes AUC, finds optimal threshold via Youden J.
        Updates self.threshold to the ROC-optimal value.
        Returns (roc_auc, optimal_threshold).
        """
        errors              = self.get_reconstruction_error(X_test)
        fpr, tpr, thresholds = roc_curve(y_test, errors)
        roc_auc             = auc(fpr, tpr)

        # Youden J statistic: maximises TPR - FPR
        j_scores    = tpr - fpr
        optimal_idx = int(np.argmax(j_scores))
        opt_thresh  = float(thresholds[optimal_idx])
        opt_tpr     = float(tpr[optimal_idx])
        opt_fpr     = float(fpr[optimal_idx])

        print(f"\n[ROC] AUC = {roc_auc:.4f}")
        print(f"[ROC] Optimal threshold (Youden J) = {opt_thresh:.6f}")
        print(f"[ROC] At optimal — TPR={opt_tpr:.3f}  FPR={opt_fpr:.3f}")

        # Update threshold
        self.threshold = opt_thresh
        print(f"[Threshold] Updated to ROC-optimal: {self.threshold:.6f}")

        # Evaluate at optimal threshold
        predictions = (errors > self.threshold).astype(int)
        print("\n[Classification Report]")
        print(classification_report(y_test, predictions,
                                    target_names=['Normal', 'Bot']))
        print("[Confusion Matrix]")
        cm = confusion_matrix(y_test, predictions)
        print(cm)
        tn, fp, fn, tp = cm.ravel()
        print(f"  True Negatives  (normal correctly accepted): {tn}")
        print(f"  False Positives (normal wrongly flagged):    {fp}")
        print(f"  False Negatives (bot wrongly accepted):      {fn}")
        print(f"  True Positives  (bot correctly flagged):     {tp}")

        # Plot
        fig, axes = plt.subplots(1, 2, figsize=(14, 5))

        # ROC curve
        axes[0].plot(fpr, tpr, color='darkorange', lw=2,
                     label=f'ROC AUC = {roc_auc:.4f}')
        axes[0].plot([0, 1], [0, 1], color='navy', lw=1.5, linestyle='--')
        axes[0].scatter(opt_fpr, opt_tpr, marker='o', color='red', s=80, zorder=5,
                        label=f'Optimal (thresh={opt_thresh:.4f})')
        axes[0].set_xlabel('False Positive Rate')
        axes[0].set_ylabel('True Positive Rate')
        axes[0].set_title('ROC Curve — UBA VAE')
        axes[0].legend(loc='lower right')
        axes[0].grid(alpha=0.3)

        # Reconstruction error distribution
        normal_errors    = errors[y_test == 0]
        malicious_errors = errors[y_test == 1]
        axes[1].hist(normal_errors,    bins=60, alpha=0.6,
                     color='steelblue', label='Normal',    density=True)
        axes[1].hist(malicious_errors, bins=60, alpha=0.6,
                     color='tomato',   label='Bot',        density=True)
        axes[1].axvline(self.threshold, color='black', linestyle='--', lw=2,
                        label=f'Threshold = {self.threshold:.4f}')
        axes[1].set_xlabel('Reconstruction Error (MSE)')
        axes[1].set_ylabel('Density')
        axes[1].set_title('Error Distribution — Normal vs Bot')
        axes[1].legend()
        axes[1].grid(alpha=0.3)

        plt.tight_layout()
        plt.savefig(save_path, dpi=150, bbox_inches='tight')
        print(f"[Plot] Saved to {save_path}")
        plt.close()

        return roc_auc, opt_thresh

    # -----------------------------------------------------------------------
    # Save / load
    # -----------------------------------------------------------------------

    def save_model(self, filepath: str = 'user_behavior_vae.pth'):
        torch.save({
            'model_state_dict': self.model.state_dict(),
            'scaler':           self.scaler,
            'threshold':        self.threshold,
            'input_dim':        self.input_dim,
        }, filepath)
        print(f"[Save] PyTorch model saved to {filepath}")

    def load_model(self, filepath: str):
        checkpoint  = torch.load(filepath, map_location=self.device)
        input_dim   = checkpoint['input_dim']
        self.model  = VAE(input_dim).to(self.device)
        self.model.load_state_dict(checkpoint['model_state_dict'])
        self.scaler    = checkpoint['scaler']
        self.threshold = checkpoint['threshold']
        self.input_dim = input_dim
        print(f"[Load] Model loaded from {filepath}")

    # -----------------------------------------------------------------------
    # ONNX export
    # -----------------------------------------------------------------------

    def export_onnx(self, filepath: str = 'user_behavior_vae.onnx'):
        """
        Exports the decoder path used by Java VAEAnalysis for reconstruction.
        Java only needs input -> reconstruction, not mu/logvar.
        We wrap the model to return only the reconstruction output.
        """
        self.model.eval()

        class ReconstructionOnlyWrapper(nn.Module):
            def __init__(self, vae):
                super().__init__()
                self.vae = vae

            def forward(self, x):
                recon, _, _ = self.vae(x)
                return recon

        wrapper     = ReconstructionOnlyWrapper(self.model).to(self.device)
        dummy_input = torch.randn(1, self.input_dim).to(self.device)

        torch.onnx.export(
            wrapper,
            dummy_input,
            filepath,
            input_names=['input'],
            output_names=['reconstruction'],
            dynamic_axes={
                'input':          {0: 'batch_size'},
                'reconstruction': {0: 'batch_size'},
            },
            opset_version=14,
        )
        print(f"[ONNX] Exported to {filepath}")

    # -----------------------------------------------------------------------
    # Java constant printer
    # -----------------------------------------------------------------------

    def print_java_scaler_constants(self):
        """
        Prints the SCALER_MEAN, SCALER_SCALE, and THRESHOLD constants
        to paste directly into VAEAnalysis.java.
        Run this after evaluate_with_roc() so threshold is ROC-optimal.
        """
        mean  = self.scaler.mean_
        scale = self.scaler.scale_

        print("\n" + "=" * 60)
        print("// Paste into VAEAnalysis.java")
        print("=" * 60)

        mean_vals  = ", ".join(f"{v:.10f}f" for v in mean)
        scale_vals = ", ".join(f"{v:.10f}f" for v in scale)

        print(f"private static final float[] SCALER_MEAN = {{\n    {mean_vals}\n}};")
        print(f"private static final float[] SCALER_SCALE = {{\n    {scale_vals}\n}};")
        print(f"private static final float THRESHOLD = {self.threshold:.7f}f;")
        print("=" * 60 + "\n")

    # -----------------------------------------------------------------------
    # Helpers
    # -----------------------------------------------------------------------

    def _plot_training_loss(self, losses: list, save_path: str = 'training_loss.png'):
        plt.figure(figsize=(8, 4))
        plt.plot(losses, color='steelblue', lw=1.5)
        plt.xlabel('Epoch')
        plt.ylabel('Average Loss')
        plt.title('VAE Training Loss')
        plt.grid(alpha=0.3)
        plt.tight_layout()
        plt.savefig(save_path, dpi=150, bbox_inches='tight')
        print(f"[Plot] Training loss saved to {save_path}")
        plt.close()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":

    # --- 1. Load normal-only dataset for unsupervised training ---
    try:
        X_normal = np.load('normal_behavior_features.npy')
        print(f"[Data] Normal dataset: {X_normal.shape}")
    except FileNotFoundError:
        print("[Error] normal_behavior_features.npy not found.")
        print("        Run data_generator.py first.")
        exit(1)

    input_dim = X_normal.shape[1]  # should be 28

    # --- 2. Train ---
    vae = UserBehaviorVAE(input_dim=input_dim, hidden_dim=64, latent_dim=6)

    print(f"\n[Train] Starting on {len(X_normal)} normal samples...")
    vae.train(
    X_normal,
    epochs=300,
    batch_size=64,
    lr=1e-3,
    kl_anneal_epochs=200,  # slower ramp
)

    # --- 3. Evaluate on labeled dataset + set ROC-optimal threshold ---
    try:
        X_test = np.load('user_behavior_features.npy')
        y_test = np.load('user_behavior_labels.npy')
        print(f"\n[Eval] Test dataset: {X_test.shape}  "
              f"(normal={np.sum(y_test==0)}  bot={np.sum(y_test==1)})")

        roc_auc, optimal_threshold = vae.evaluate_with_roc(
            X_test, y_test, save_path='roc_curve.png'
        )

    except FileNotFoundError:
        print("[Eval] user_behavior_features.npy not found — skipping evaluation.")
        print("       Threshold remains at 95th percentile of training errors.")

    # --- 4. Save ---
    vae.save_model('user_behavior_vae.pth')
    vae.export_onnx('user_behavior_vae.onnx')

    # --- 5. Print Java constants ---
    vae.print_java_scaler_constants()

    print("\n[Done] Files produced:")
    print("  user_behavior_vae.pth   — PyTorch checkpoint")
    print("  user_behavior_vae.onnx  — ONNX model for Java")
    print("  roc_curve.png           — ROC + error distribution plot")
    print("  training_loss.png       — Training loss curve")