from data_generator import UserBehaviorDataGenerator
from vae_model import UserBehaviorVAE
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix
import matplotlib.pyplot as plt

def train_and_evaluate():
    print("Generating datasets...")
    generator = UserBehaviorDataGenerator()
    
    # Generate larger normal dataset for better training
    X_normal = generator.generate_normal_only_dataset(6000)
    print(f"Normal training dataset shape: {X_normal.shape}")
    
    # Generate labeled dataset for evaluation
    X_test, y_test = generator.generate_dataset(num_normal=800, num_malicious=200)
    print(f"Test dataset shape: {X_test.shape}")
    print(f"Test - Normal: {np.sum(y_test == 0)}, Malicious: {np.sum(y_test == 1)}")
    
    # Initialize VAE with optimized parameters
    vae = UserBehaviorVAE(input_dim=X_normal.shape[1], hidden_dim=96, latent_dim=24)
    
    # Train with optimized parameters
    print(f"\nTraining VAE on {len(X_normal)} normal samples...")
    vae.train(X_normal, epochs=250, batch_size=32, lr=3e-4)
    
    # Evaluate on test set
    print("\nEvaluating model...")
    predictions = vae.predict(X_test)
    
    # Calculate metrics
    print("\nClassification Report:")
    print(classification_report(y_test, predictions, target_names=['Normal', 'Malicious']))
    
    print("\nConfusion Matrix:")
    print(confusion_matrix(y_test, predictions))
    
    # Calculate reconstruction errors for visualization
    normal_errors = []
    malicious_errors = []
    
    for i in range(len(X_test)):
        error = vae.get_reconstruction_error(X_test[i])
        if y_test[i] == 0:
            normal_errors.append(error)
        else:
            malicious_errors.append(error)
    
    # Plot reconstruction error distribution
    plt.figure(figsize=(10, 6))
    plt.hist(normal_errors, bins=30, alpha=0.7, label='Normal', color='blue')
    plt.hist(malicious_errors, bins=30, alpha=0.7, label='Malicious', color='red')
    plt.axvline(vae.threshold, color='black', linestyle='--', label=f'Threshold: {vae.threshold:.6f}')
    plt.xlabel('Reconstruction Error')
    plt.ylabel('Frequency')
    plt.title('Reconstruction Error Distribution')
    plt.legend()
    plt.savefig('reconstruction_errors.png')
    plt.show()
    
    # Save the trained model
    vae.save_model('user_behavior_vae.pth')
    
    print(f"\nModel saved successfully!")
    print(f"Threshold: {vae.threshold:.6f}")
    
    return vae

if __name__ == "__main__":
    trained_vae = train_and_evaluate()