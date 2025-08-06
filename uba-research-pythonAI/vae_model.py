import torch
import torch.nn as nn
import torch.nn.functional as F
import numpy as np
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
import joblib

class VAE(nn.Module):
    def __init__(self, input_dim, hidden_dim=128, latent_dim=32):
        super(VAE, self).__init__()
        
        # Encoder with dropout for regularization
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(hidden_dim, hidden_dim // 2),
            nn.BatchNorm1d(hidden_dim // 2),
            nn.ReLU(),
            nn.Dropout(0.2)
        )
        
        self.mu_layer = nn.Linear(hidden_dim // 2, latent_dim)
        self.logvar_layer = nn.Linear(hidden_dim // 2, latent_dim)
        
        # Decoder
        self.decoder = nn.Sequential(
            nn.Linear(latent_dim, hidden_dim // 2),
            nn.BatchNorm1d(hidden_dim // 2),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(hidden_dim // 2, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(hidden_dim, input_dim)
        )
        
    def encode(self, x):
        h = self.encoder(x)
        mu = self.mu_layer(h)
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
        z = self.reparameterize(mu, logvar)
        recon_x = self.decode(z)
        return recon_x, mu, logvar

def vae_loss(recon_x, x, mu, logvar, beta=0.3):
    # Reconstruction loss (focus more on reconstruction)
    recon_loss = F.mse_loss(recon_x, x, reduction='sum')
    
    # KL divergence loss (regularization)
    kld_loss = -0.5 * torch.sum(1 + logvar - mu.pow(2) - logvar.exp())
    
    # Lower beta = focus more on reconstruction accuracy
    return recon_loss + beta * kld_loss

class UserBehaviorVAE:
    def __init__(self, input_dim, hidden_dim=128, latent_dim=32):
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model = VAE(input_dim, hidden_dim, latent_dim).to(self.device)
        self.scaler = StandardScaler()
        self.threshold = None
        print(f"Initialized VAE with {input_dim} input features on {self.device}")
        
    def train(self, X, epochs=200, batch_size=32, lr=5e-4):
        # Normalize features
        X_scaled = self.scaler.fit_transform(X)
        
        # Convert to tensor
        X_tensor = torch.FloatTensor(X_scaled).to(self.device)
        
        # Training setup with lower learning rate
        optimizer = torch.optim.Adam(self.model.parameters(), lr=lr, weight_decay=1e-4)
        scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, patience=15, factor=0.7)
        dataset = torch.utils.data.TensorDataset(X_tensor)
        dataloader = torch.utils.data.DataLoader(dataset, batch_size=batch_size, shuffle=True)
        
        self.model.train()
        best_loss = float('inf')
        patience_counter = 0
        
        for epoch in range(epochs):
            total_loss = 0
            for batch_idx, (data,) in enumerate(dataloader):
                optimizer.zero_grad()
                recon_batch, mu, logvar = self.model(data)
                loss = vae_loss(recon_batch, data, mu, logvar, beta=0.3)  # Lower beta
                loss.backward()
                optimizer.step()
                total_loss += loss.item()
            
            avg_loss = total_loss / len(dataloader.dataset)
            scheduler.step(avg_loss)
            
            if avg_loss < best_loss:
                best_loss = avg_loss
                patience_counter = 0
            else:
                patience_counter += 1
            
            if epoch % 25 == 0:
                print(f'Epoch {epoch}, Loss: {avg_loss:.4f}, LR: {optimizer.param_groups[0]["lr"]:.6f}')
            
            # Early stopping
            if patience_counter > 30:
                print(f'Early stopping at epoch {epoch}')
                break
        
        # Calculate threshold for anomaly detection
        self._calculate_threshold(X_scaled)
        print(f'Training completed. Best loss: {best_loss:.4f}')
    
    def _calculate_threshold(self, X_normal):
        self.model.eval()
        with torch.no_grad():
            X_tensor = torch.FloatTensor(X_normal).to(self.device)
            recon_X, _, _ = self.model(X_tensor)
            reconstruction_errors = torch.mean((X_tensor - recon_X) ** 2, dim=1).cpu().numpy()
            
            # Use 99.5th percentile + margin for less false positives
            base_threshold = np.percentile(reconstruction_errors, 99.5)
            margin = np.std(reconstruction_errors) * 0.5
            self.threshold = base_threshold + margin
            
            print(f"Anomaly threshold set to: {self.threshold:.6f}")
            print(f"Base (99.5th percentile): {base_threshold:.6f}")
            print(f"Reconstruction error stats - Mean: {np.mean(reconstruction_errors):.6f}, Std: {np.std(reconstruction_errors):.6f}")
    
    def predict(self, X):
        # Normalize input
        X_scaled = self.scaler.transform(X.reshape(1, -1) if X.ndim == 1 else X)
        
        self.model.eval()
        with torch.no_grad():
            X_tensor = torch.FloatTensor(X_scaled).to(self.device)
            recon_X, _, _ = self.model(X_tensor)
            reconstruction_error = torch.mean((X_tensor - recon_X) ** 2, dim=1).cpu().numpy()
            
            # Return 1 for anomaly (malicious), 0 for normal
            return (reconstruction_error > self.threshold).astype(int)
    
    def get_reconstruction_error(self, X):
        X_scaled = self.scaler.transform(X.reshape(1, -1) if X.ndim == 1 else X)
        
        self.model.eval()
        with torch.no_grad():
            X_tensor = torch.FloatTensor(X_scaled).to(self.device)
            recon_X, _, _ = self.model(X_tensor)
            reconstruction_error = torch.mean((X_tensor - recon_X) ** 2, dim=1).cpu().numpy()
            
            return reconstruction_error[0] if X.ndim == 1 else reconstruction_error
    
    def save_model(self, filepath):
        torch.save({
            'model_state_dict': self.model.state_dict(),
            'scaler': self.scaler,
            'threshold': self.threshold,
            'input_dim': self.model.encoder[0].in_features
        }, filepath)
        print(f"Model saved to {filepath}")
    
    def load_model(self, filepath):
        checkpoint = torch.load(filepath, map_location=self.device)
        
        # Recreate model with correct dimensions
        input_dim = checkpoint['input_dim']
        self.model = VAE(input_dim).to(self.device)
        self.model.load_state_dict(checkpoint['model_state_dict'])
        
        self.scaler = checkpoint['scaler']
        self.threshold = checkpoint['threshold']
        print(f"Model loaded from {filepath}")

if __name__ == "__main__":
    # Load normal dataset for unsupervised training
    try:
        X_normal = np.load('normal_behavior_features.npy')
        print(f"Loaded normal dataset: {X_normal.shape}")
    except FileNotFoundError:
        print("Normal dataset not found. Run data_generator.py first.")
        exit()
    
    # Initialize and train VAE
    vae = UserBehaviorVAE(input_dim=X_normal.shape[1], hidden_dim=128, latent_dim=32)
    
    print(f"Training VAE on {len(X_normal)} normal samples...")
    vae.train(X_normal, epochs=200, batch_size=64, lr=0.001)
    
    # Test on labeled dataset if available
    try:
        X_test = np.load('user_behavior_features.npy')
        y_test = np.load('user_behavior_labels.npy')
        
        print("\nEvaluating on test set...")
        predictions = vae.predict(X_test)
        
        from sklearn.metrics import classification_report, confusion_matrix
        print("\nClassification Report:")
        print(classification_report(y_test, predictions, target_names=['Normal', 'Malicious']))
        
        print("\nConfusion Matrix:")
        print(confusion_matrix(y_test, predictions))
        
    except FileNotFoundError:
        print("Test dataset not found. Skipping evaluation.")
    
    # Save model
    vae.save_model('user_behavior_vae.pth')
    print("\nModel training completed and saved!")