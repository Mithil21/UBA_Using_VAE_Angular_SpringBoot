# VAE Anomaly Detection Model

This directory contains the VAE (Variational Autoencoder) model for detecting malicious inputs in the UBA Research application.

## Setup Instructions

1. **Install Python Dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

2. **Test the VAE Model:**
   ```bash
   python test_vae.py
   ```

3. **Model Files:**
   - `vae_model.py` - Main VAE model script called by Spring Boot
   - `requirements.txt` - Python dependencies
   - `test_vae.py` - Test script to verify model functionality

## How it Works

1. Spring Boot receives a request from the frontend
2. The `RequestInterceptor` extracts request data and timestamp
3. The `VAEService` calls the Python VAE model with JSON data
4. The VAE model analyzes the data and returns anomaly score
5. If the score exceeds the threshold, a `MaliciousInputException` is thrown
6. The request is blocked and an error response is returned

## Configuration

The VAE model path and Python executable can be configured in `application.properties`:

```properties
vae.model.path=C:/Users/mithi/OneDrive/Desktop/UBA Research/uba-research-pythonAI
vae.python.executable=python
vae.enabled=true
```

## Model Training

The system uses your trained PyTorch VAE model:
1. `user_behaviour_vae.pth` - Your trained PyTorch VAE model
2. `scaler.pkl` - Feature scaler (optional)
3. Update the threshold value in the model initialization if needed

The VAE model architecture includes:
- Encoder: Linear -> ReLU -> mu/logvar layers
- Decoder: Linear -> ReLU -> Linear -> Sigmoid
- Reconstruction loss calculation for anomaly detection