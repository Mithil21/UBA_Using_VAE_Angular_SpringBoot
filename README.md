# UBA-VAE — User Behaviour Analytics with Variational Autoencoder

> MSc Advanced Computer Science Dissertation Project  
> University of Manchester, 2025–2026  
> Real-time bot detection at form submission boundaries using unsupervised deep learning and behavioural biometrics.

---

## What This System Does

Traditional bot detection relies on CAPTCHAs, IP blacklists, and rate limiting — all bypassable. This system instead analyses **how** a user interacts with a form, not just what they submit.

A Variational Autoencoder (VAE) is trained exclusively on normal human interaction data. At registration and login, a 28-feature behavioural fingerprint is extracted from the user's keystrokes, mouse movements, and session context. The VAE reconstructs this fingerprint — normal users produce low reconstruction error and are accepted; bots produce high error and are rejected.

**Key results from end-to-end evaluation:**

| Profile | MSE | Probability | Decision |
|---|---|---|---|
| Real human (live test) | 1.93 | 0.9351 | ✅ Accepted |
| Normal — average typist | 1.26 | 0.9699 | ✅ Accepted |
| Normal — slow/elderly typist | 1.90 | 0.9375 | ✅ Accepted |
| Dumb bot (10ms flight time) | 2.38 | 0.3380 | ❌ Rejected |
| Smart bot (jitter added) | 2.33 | 0.3649 | ❌ Rejected |
| Headless browser (Selenium) | 2.34 | 0.3627 | ❌ Rejected |
| Human-mimicking bot | 2.38 | 0.3383 | ❌ Rejected |
| Credential stuffing tool | 2.99 | 0.1070 | ❌ Rejected |

**True Positive Rate (bot detection): 100% | True Negative Rate (human acceptance): 100%**

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Angular Frontend                         │
│  UbaTrackerService — captures keystrokes, mouse, session    │
│  AES-256 + RSA-OAEP payload encryption before transmission  │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS encrypted payload
┌──────────────────────────▼──────────────────────────────────┐
│                   Spring Boot Backend                        │
│                                                              │
│  UbaDecryptionFilter   — decrypts AES+RSA payload           │
│  VAEAnalysis           — extracts 28 features, runs ONNX    │
│  AuthController        — register / login endpoints         │
│  TestVAEController     — debug endpoint (dev profile only)  │
└──────────────────────────┬──────────────────────────────────┘
                           │ 28-feature float[]
┌──────────────────────────▼──────────────────────────────────┐
│              ONNX Runtime — VAE Inference                    │
│  user_behavior_vae.onnx  — exported from PyTorch            │
│  Reconstruction error → normalised sigmoid → probability    │
└─────────────────────────────────────────────────────────────┘

Python (offline training pipeline)
  data_generator.py  — synthetic data (CMU + BALABIT + Fitts + Curvature)
  vae_model.py       — VAE training, ROC evaluation, ONNX export
```

---

## Repository Structure

```
UBA_Using_VAE_Angular_SpringBoot/
│
├── uba_research_ui/                    # Angular 17 frontend
│   └── src/app/core/services/
│       └── uba-tracker.service.ts      # Keystroke + mouse telemetry collector
│
├── uba-research-backend/               # Spring Boot 3 backend
│   └── src/main/java/com/forensic/audit/
│       ├── analysis/
│       │   └── VAEAnalysis.java        # 28-feature extraction + ONNX inference
│       ├── commons/
│       │   └── Metadata.java           # Telemetry deserialization model
│       ├── controller/
│       │   ├── AuthController.java     # /api/auth/register + /api/auth/login
│       │   └── TestVAEController.java  # /api/debug/vae-test (dev only)
│       └── filter/
│           └── UbaDecryptionFilter.java # AES-256/RSA-OAEP decryption
│
├── uba-research-pythonAI/              # Python ML pipeline
│   ├── data_generator.py               # Synthetic dataset generation
│   ├── vae_model.py                    # VAE training + evaluation + ONNX export
│   ├── test_vae_endpoint.py            # End-to-end bot detection test suite
│   └── balabit_loader.py               # BALABIT dataset preprocessor
│
└── uba-analytics-library/              # Shared Angular UBA components
```

---

## The 28 Behavioural Features

| # | Feature | Source | Human signal |
|---|---|---|---|
| 0 | avgFlightTime | CMU dataset | Time between keystrokes |
| 1 | stdFlightTime | CMU dataset | Variance in typing rhythm |
| 2 | backspaceRatio | Keystrokes | Humans make mistakes |
| 3 | keystrokeCount | Keystrokes | Form length indicator |
| 4 | medianFlightTime | CMU dataset | Robust timing measure |
| 5 | meanClickInterval | Click events | Time between clicks |
| 6 | clickCount | Click events | Interaction count |
| 7 | meanMouseDistance | BALABIT dataset | Step size distribution |
| 8 | stdMouseDistance | BALABIT dataset | Path variation |
| 9 | mouseEventCount | Mouse events | Movement frequency |
| 10 | meanMouseInterval | BALABIT dataset | Inter-event timing |
| 11 | pageDwellSeconds | Session | Time on page |
| 12 | tabSwitchCount | Session | Human multitasking |
| 13 | windowBlurCount | Session | Focus changes |
| 14 | navigationCount | Session | Page history |
| 15 | timeBeforeFirstInput | Session | Reading time |
| 16 | formCompletionTime | Session | Total typing duration |
| 17 | fieldSwitchCount | Session | Tab/click between fields |
| 18 | keystrokeCount2 | Keystrokes | Duplicate for VAE weighting |
| 19 | avgKeyHoldTime | Keystrokes | Key press duration |
| 20 | typingSpeed | Derived | Keystrokes per second |
| 21 | backspaceCount | Keystrokes | Raw correction count |
| 22 | specialKeyCount | Keystrokes | Non-alpha key usage |
| 23 | mouseDistance | BALABIT dataset | Total path length |
| 24 | avgMouseSpeed | BALABIT dataset | Movement velocity |
| 25 | maxMouseSpeed | BALABIT dataset | Peak speed (Fitts' Law) |
| 26 | clickFrequency | Derived | Clicks per second |
| 27 | idleTimeRatio | Session | Fraction of idle gaps >3s |

---

## Data Generation Pipeline

Training data is synthetic but anchored to two real-world datasets:

**CMU Keystroke Dynamics Dataset** (Killourhy & Maxion, 2009)
- 51 subjects typing the same password repeatedly
- Provides statistically grounded hold/flight time distributions
- Download: https://www.cs.cmu.edu/~keystroke/

**BALABIT Mouse Dynamics Challenge Dataset** (Fülöp et al., 2016)
- 10 users, real desktop sessions over 3 months
- Provides mouse speed, distance, and interval distributions
- Download: https://github.com/balabit/Mouse-Dynamics-Challenge

**Biomechanical validation:**
- **Fitts' Law** — movement duration validated against `MT = a + b·log₂(2D/W)`
- **Curvature index** — path shape enforced in range 1.05–3.0 (human arcs vs bot straight lines)
- **Velocity bell curve** — ease-in/out timing via sin modulation

**6 human personas** (weighted by real-world population):
`fast_typist, average_typist, slow_typist, hunt_and_peck, elderly, power_user`

**4 bot profiles:**
`dumb_bot, smart_bot, headless, human_mimicking`

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Node.js | 18+ | Angular frontend |
| Angular CLI | 17+ | `npm install -g @angular/cli` |
| Java | 17+ | Spring Boot backend |
| Maven | 3.6+ | Backend build |
| Python | 3.10+ | ML training pipeline |
| pip packages | — | See below |

```bash
pip install torch numpy pandas scikit-learn matplotlib joblib requests
```

---

## Setup & Run

### 1. Train the VAE model (run once)

```bash
cd uba-research-pythonAI

# Download datasets
# CMU:     https://www.cs.cmu.edu/~keystroke/ → DSL-StrongPasswordData.csv
# BALABIT: git clone https://github.com/balabit/Mouse-Dynamics-Challenge
#          python balabit_loader.py → produces balabit_mouse.csv

# Generate training data
python data_generator.py
# Produces: normal_behavior_features.npy
#           user_behavior_features.npy
#           user_behavior_labels.npy

# Train VAE + export ONNX
python vae_model.py
# Produces: user_behavior_vae.onnx  ← copy to Spring Boot
#           user_behavior_vae.pth
#           roc_curve.png
#           training_loss.png
# Also prints SCALER_MEAN, SCALER_SCALE, THRESHOLD → paste into VAEAnalysis.java
```

### 2. Configure Spring Boot

```bash
# Copy ONNX model
cp uba-research-pythonAI/user_behavior_vae.onnx \
   uba-research-backend/src/main/resources/models/

# Paste printed scaler constants into:
# uba-research-backend/src/main/java/com/forensic/audit/analysis/VAEAnalysis.java
# — SCALER_MEAN[], SCALER_SCALE[], THRESHOLD
```

Update `application.properties`:
```properties
spring.profiles.active=dev
spring.datasource.url=jdbc:postgresql://localhost:5432/uba_db
spring.datasource.username=your_user
spring.datasource.password=your_password
```

### 3. Start the backend

```bash
cd uba-research-backend
mvn spring-boot:run
# Starts on http://localhost:8080
# Look for: [VAE] Model loaded — input_dim=28  threshold=X.XXXXXXX
```

### 4. Start the frontend

```bash
cd uba_research_ui
npm install
ng serve
# Opens http://localhost:4200
```

---

## Testing

### Manual test — register as a human

1. Open `http://localhost:4200/register`
2. Wait ~2 seconds before typing (natural reading time)
3. Type email and password at normal pace
4. Submit

Expected Spring Boot log:
```
[VAE] mse=~1.5  normalizedMse=~0.65  probability=~0.90  accepted=true
```

### Automated test suite — 8 profiles (human + bot)

Requires the debug endpoint (dev profile must be active):

```bash
cd uba-research-pythonAI
python test_vae_endpoint.py
```

Expected output:
```
✅ PASS  Normal — Average typist          MSE=1.26  Prob=0.9699  ACCEPT
✅ PASS  Normal — Slow/careful typist     MSE=1.90  Prob=0.9375  ACCEPT
✅ PASS  Attack — Dumb bot                MSE=2.38  Prob=0.3380  REJECT
✅ PASS  Attack — Smart bot               MSE=2.33  Prob=0.3649  REJECT
✅ PASS  Attack — Headless browser        MSE=2.34  Prob=0.3627  REJECT
✅ PASS  Attack — Human-mimicking bot     MSE=2.38  Prob=0.3383  REJECT
✅ PASS  Attack — Credential stuffing     MSE=2.99  Prob=0.1070  REJECT

True Positive Rate (bot detection):    100.0%
True Negative Rate (human acceptance): 100.0%
```

---

## How the VAE Decision Works

```
Raw features (28 floats)
    ↓
StandardScaler normalisation
    ↓
ONNX VAE inference → reconstruction
    ↓
MSE = mean((original - reconstruction)²)
    ↓
normalizedMse = mse / THRESHOLD
    ↓
probability = 1 / (1 + exp(5 × (normalizedMse - 1)))
    ↓
probability ≥ 0.50 → ACCEPT
probability <  0.50 → REJECT
```

The sigmoid is centred at the threshold — probability is exactly 0.50 when MSE equals THRESHOLD, approaching 1.0 for clearly normal inputs and 0.0 for clear anomalies.

---

## Threshold Tuning

The default threshold (`2.10f` in `VAEAnalysis.java`) was calibrated via ROC curve using Youden's J statistic on the synthetic evaluation dataset. After any retrain:

1. Run `python vae_model.py`
2. Check the printed `THRESHOLD` value from `print_java_scaler_constants()`
3. Update `VAEAnalysis.java` — `SCALER_MEAN`, `SCALER_SCALE`, `THRESHOLD`
4. Rebuild and restart Spring Boot

To adjust sensitivity manually:
- **Lower threshold** → catches more bots, more false positives on edge-case humans
- **Higher threshold** → fewer false positives, may miss sophisticated bots

---

## Security Design

| Layer | Mechanism |
|---|---|
| Transport | HTTPS |
| Payload encryption | AES-256-GCM + RSA-OAEP (Angular → Spring Boot) |
| Bot detection | VAE reconstruction error (unsupervised) |
| Password fields | Masked in telemetry (`MASKED` literal) |
| Clipboard | Blocked and logged |
| DevTools | F12 / Ctrl+Shift+I blocked |
| Debug endpoints | `@Profile("dev")` — not available in production |

---

## Academic References

- Killourhy, K. S., & Maxion, R. A. (2009). *Comparing anomaly-detection algorithms for keystroke dynamics.* DSN.
- Fülöp, Á., Kovács, L., Kurics, T., & Windhager-Pokol, E. (2016). *Balabit Mouse Dynamics Challenge data set.*
- Fitts, P. M. (1954). *The information capacity of the human motor system in controlling the amplitude of movement.* Journal of Experimental Psychology, 47(6), 381–391.
- Antal, M., & Egyed-Zsigmond, E. (2019). *Intrusion detection using mouse dynamics.* IET Biometrics, 8(5), 285–294.
- Kingma, D. P., & Welling, M. (2013). *Auto-encoding variational Bayes.* arXiv:1312.6114.

---

## Limitations

- VAE trained on synthetic data anchored to CMU/BALABIT — threshold requires recalibration on real production traffic
- Mouse feature discrimination is weaker when raw event lists are empty (pre-computed scalars used as fallback)
- 51 CMU subjects is a narrow demographic — population diversity is a known limitation of the dataset
- AUC of 1.0 on synthetic evaluation reflects controlled bot patterns; real adversarial bots will require ongoing threshold tuning

---

## Planned: Hyperledger Fabric Integration

Upcoming addition — tamper-evident audit trail using Hyperledger Fabric:

- Every accepted registration commits `SHA-256(user_record || metadata_record)` to the Fabric ledger
- UUID used as the ledger key, correlating DB rows and ledger entries
- Scheduled verification job re-hashes DB rows and compares against ledger
- Any manual DB modification detected as hash mismatch → tamper alert raised

---

## License

MIT License. See `LICENSE` for details.
