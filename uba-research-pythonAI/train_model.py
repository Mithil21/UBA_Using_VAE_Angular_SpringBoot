from data_generator import UserBehaviorDataGenerator
from vae_model import UserBehaviorVAE
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix
import matplotlib.pyplot as plt

def train_and_evaluate():
    print("Generating dataset...")
    generator = UserBehaviorDataGenerator()
    
    # Generate larger dataset for better training
    X, y = generator.generate_dataset(num_normal=5000, num_malicious=1000)
    
    print(f"Dataset shape: {X.shape}")
    print(f"Normal samples: {np.sum(y == 0)}, Malicious samples: {np.sum(y == 1)}")
    
    # Initialize VAE
    vae = UserBehaviorVAE(input_dim=X.shape[1], hidden_dim=128, latent_dim=32)
    
    # Train only on normal samples (unsupervised anomaly detection)
    X_normal = X[y == 0]
    print(f"\nTraining VAE on {len(X_normal)} normal samples...")
    
    vae.train(X_normal, epochs=150, batch_size=64, lr=0.001)
    
    # Evaluate on test set
    print("\nEvaluating model...")
    
    # Test on all samples
    predictions = vae.predict(X)
    
    # Calculate metrics
    print("\nClassification Report:")
    print(classification_report(y, predictions, target_names=['Normal', 'Malicious']))
    
    print("\nConfusion Matrix:")
    print(confusion_matrix(y, predictions))
    
    # Calculate reconstruction errors for visualization
    normal_errors = []
    malicious_errors = []
    
    for i in range(len(X)):
        error = vae.get_reconstruction_error(X[i])
        if y[i] == 0:
            normal_errors.append(error)
        else:
            malicious_errors.append(error)
    
    # Plot reconstruction error distribution
    plt.figure(figsize=(10, 6))
    plt.hist(normal_errors, bins=50, alpha=0.7, label='Normal', color='blue')
    plt.hist(malicious_errors, bins=50, alpha=0.7, label='Malicious', color='red')
    plt.axvline(vae.threshold, color='black', linestyle='--', label=f'Threshold: {vae.threshold:.4f}')
    plt.xlabel('Reconstruction Error')
    plt.ylabel('Frequency')
    plt.title('Reconstruction Error Distribution')
    plt.legend()
    plt.savefig('reconstruction_errors.png')
    plt.show()
    
    # Save the trained model
    vae.save_model('user_behavior_vae.pth')
    
    print(f"\nModel saved successfully!")
    print(f"Threshold: {vae.threshold:.4f}")
    
    return vae

if __name__ == "__main__":
    trained_vae = train_and_evaluate()