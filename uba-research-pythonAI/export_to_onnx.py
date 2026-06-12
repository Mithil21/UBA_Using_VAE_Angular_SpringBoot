import torch
import json
from vae_model import VAE

def export_to_onnx_and_json(model_path='user_behavior_vae.pth', onnx_path='user_behavior_vae.onnx', config_path='vae_config.json'):
    print(f"Loading PyTorch checkpoint from {model_path}...")
    
    checkpoint = torch.load(model_path, map_location='cpu', weights_only=False)

    input_dim = checkpoint['input_dim']
    threshold = float(checkpoint['threshold'])
    scaler    = checkpoint['scaler']

    # Read dims from checkpoint instead of hardcoding
    hidden_dim = checkpoint.get('hidden_dim', 64)   # falls back to 64 if not stored
    latent_dim = checkpoint.get('latent_dim', 6)    # falls back to 6 if not stored

    model = VAE(input_dim=input_dim, hidden_dim=hidden_dim, latent_dim=latent_dim)
    model.load_state_dict(checkpoint['model_state_dict'])
    
    # CRITICAL: Set model to evaluation mode before exporting
    model.eval()
    
    # 3. Create a dummy input tensor matching the expected shape
    dummy_input = torch.randn(1, input_dim)
    
    # 4. Export to ONNX format
    print(f"Exporting Neural Network to {onnx_path}...")
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        export_params=True,
        opset_version=14,                  
        do_constant_folding=True,          
        input_names=['input_features'],    
        output_names=['reconstructed', 'mu', 'logvar'], 
        dynamic_axes={                     
            'input_features': {0: 'batch_size'}, 
            'reconstructed': {0: 'batch_size'}
        }
    )
    
    # 5. Export the Scaler logic and Threshold for Java
    print(f"Exporting Scaler and Threshold to {config_path}...")
    config = {
        'input_dim': input_dim,
        'threshold': threshold,
        'scaler_mean': scaler.mean_.tolist(),
        'scaler_scale': scaler.scale_.tolist()
    }
    
    with open(config_path, 'w') as f:
        json.dump(config, f, indent=4)
        
    print("\n[Success] Export complete!")
    print("Next Steps: Move 'user_behavior_vae.onnx' and 'vae_config.json'")
    print("into your Spring Boot 'src/main/resources/' folder.")

if __name__ == "__main__":
    export_to_onnx_and_json()