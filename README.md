<div align="center">

# UBA-VAE — Zero Trust Forensics Platform
### Behavioural Biometrics for Automated Penetration Attempt Detection

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-17-red?style=flat-square&logo=angular)](https://angular.io/)
[![Python](https://img.shields.io/badge/Python-3.10+-blue?style=flat-square&logo=python)](https://python.org/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5-black?style=flat-square&logo=apachekafka)](https://kafka.apache.org/)
[![Fabric](https://img.shields.io/badge/Hyperledger%20Fabric-2.5-blue?style=flat-square)](https://hyperledger-fabric.readthedocs.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)

**MSc Advanced Computer Science Dissertation · University of Manchester · 2025–2026**

*Supervisor: Professor Richard Banach*

</div>

---

## What This Is

A real-time bot and penetration attempt detection system that operates at web application form boundaries. When a user fills in a registration form, the system silently captures 28 behavioural signals from their keystrokes, mouse movements, and session context, compresses them through a Variational Autoencoder, and decides whether the interaction was human or automated.

**No CAPTCHA. No challenge. No friction.** The user never knows the analysis is happening. The attacker never knows they were detected.

Every decision — accepted, borderline, or rejected — is committed to a Hyperledger Fabric ledger as an immutable hash. Even a database admin cannot delete evidence of an attack after the fact.

---

## The Problem Being Solved

Traditional bot defences have a shared weakness: they analyse **what** is submitted, not **how** the user behaves.

| Defence | How it is bypassed |
|---|---|
| CAPTCHA | Solving services cost fractions of a penny per solve |
| IP blacklisting | IPs rotate via proxies and botnets |
| Rate limiting | Slows attackers, does not stop them |
| Device fingerprinting | Headless browsers mimic real browsers |

A credential stuffing tool submitting `email=test@test.com&password=Test@1234` looks identical to a human submitting the same values. The payload is the same. The headers are the same. Only the behaviour differs.

**This system analyses the interaction, not the data.** A bot filling a form in 380ms with zero mouse movement, no backspaces, and instant field switching is detectable regardless of what credentials it submits.

---

## Why VAE and Not a Classifier

The core technical choice is unsupervised anomaly detection via Variational Autoencoder rather than a supervised classifier.

A supervised classifier requires labelled attack data. It can only detect attack patterns it has been trained on. New bot tooling, new evasion techniques, novel attack patterns — all bypass a classifier until the training set is updated.

The VAE is trained on normal human behaviour only. It learns what a human looks like. Anything that deviates from normality is anomalous — including attacks the model has never seen. This is zero-shot detection of unknown attack patterns.

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                       Angular Frontend                            │
│                                                                   │
│  UbaTrackerService                                                │
│  ├── Captures keydown/keyup with precise timestamps              │
│  ├── Captures mousemove/click with x, y coordinates             │
│  ├── Computes 28 behavioural features client-side               │
│  ├── Blocks clipboard — copy/paste cannot bypass telemetry      │
│  ├── Masks password keystrokes as MASKED — never transmitted    │
│  └── resetPageTimer() on every route navigation                 │
│                                                                   │
│  CryptoService                                                    │
│  ├── AES-256-GCM encrypts the telemetry payload                 │
│  └── RSA-OAEP encrypts the AES key with the server public key   │
└───────────────────────────────┬──────────────────────────────────┘
                                │ Encrypted envelope — cannot be
                                │ tampered without server private key
┌───────────────────────────────▼──────────────────────────────────┐
│                     Spring Boot Backend                           │
│                                                                   │
│  UbaDecryptionFilter                                              │
│  └── Decrypts AES+RSA payload on every request                  │
│                                                                   │
│  AuthController                                                   │
│  ├── POST /api/auth/register — email validation, duplicate       │
│  │   check, returns 202 + requestId immediately                  │
│  └── POST /api/auth/login — credential verification, 200/401    │
│                                                                   │
│  VaeRequestProducer                                               │
│  ├── Saves RECEIVED state to analysis_requests table            │
│  └── Publishes VaeAnalysisMessage to Kafka topic                │
└───────────────────────────────┬──────────────────────────────────┘
                                │ Async — HTTP thread free immediately
┌───────────────────────────────▼──────────────────────────────────┐
│                      Apache Kafka                                 │
│                                                                   │
│  Topic: uba-vae-requests    (3 partitions, 3 parallel consumers) │
│  Topic: uba-vae-dead-letter (overflow after 3 failed retries)   │
│                                                                   │
│  AckMode: MANUAL_IMMEDIATE                                        │
│  └── Offset only advances after ack.acknowledge() is called     │
│      If consumer crashes, Kafka redelivers automatically         │
└───────────────────────────────┬──────────────────────────────────┘
                                │ VaeRequestConsumer
┌───────────────────────────────▼──────────────────────────────────┐
│                  VAEAnalysis — ONNX Runtime                       │
│                                                                   │
│  1. extractFeatures()   28 float values from Metadata object    │
│  2. sanitize()          Clamp to mean ±5σ — one corrupted       │
│                          feature cannot dominate the MSE         │
│  3. scale()             StandardScaler using trained constants   │
│  4. runInference()      ONNX Runtime — self-contained model      │
│  5. mse()               Mean squared error: input vs recon      │
│  6. sigmoid             prob = 1 / (1 + exp(5 × (mse/T − 1)))  │
│                                                                   │
│  Three-zone decision (empirically derived from evaluation):      │
│  ├── prob > 0.65  → ACCEPTED   (normal users scored 0.88–0.97) │
│  ├── 0.40–0.65    → REVIEW     (borderline — human review)      │
│  └── prob < 0.40  → REJECTED   (bots scored 0.11–0.40)         │
└────────────┬───────────────────────┬─────────────────────────────┘
             │                       │                    │
         ACCEPTED                 REVIEW              REJECTED
             │                       │                    │
┌────────────▼────────┐  ┌───────────▼───────┐  ┌────────▼────────┐
│   uba_accepted      │  │   uba_review      │  │  uba_rejected   │
│                     │  │                   │  │                 │
│ Full telemetry      │  │ Full telemetry    │  │ Full telemetry  │
│ fabricHash          │  │ reviewLabel       │  │ fabricHash      │
│ fabricCommittedAt   │  │ reviewNotes       │  │ rejectionReason │
│                     │  │ reviewedBy        │  │                 │
│ → VAE retraining    │  │ → Human labelling │  │ → Forensic audit│
│ → Welcome email     │  │ → On-hold email   │  │ → Vague email   │
└────────────┬────────┘  └───────────────────┘  └────────┬────────┘
             │                                            │
             └──────────────────┬─────────────────────────┘
                                │ SHA-256 hash committed
┌───────────────────────────────▼──────────────────────────────────┐
│                  Hyperledger Fabric Ledger                        │
│                                                                   │
│  Channel: auditchannel                                           │
│  Chaincode: auditcontract (Go, CCAAS deployment)                │
│                                                                   │
│  CommitRecord(uuid, email, decision, hash, vaeScore, mseScore)  │
│  └── Append-only — cannot be modified or deleted                │
│                                                                   │
│  VerifyRecord(uuid) → stored hash                               │
│  └── Called hourly by TamperDetectionScheduler                  │
│      currentHash ≠ ledgerHash → TAMPER ALERT                    │
└──────────────────────────────────────────────────────────────────┘

Dashboard: GET /api/dashboard/* → Angular audit UI
  ├── Stat cards (accepted, review, rejected, dead letter, on-chain)
  ├── Decision donut chart
  ├── Retry distribution bar chart
  ├── VAE probability scatter plot with threshold lines
  └── Three data tables with ledger status indicators
```

---

## The 28 Behavioural Features

The system captures behaviour across three dimensions. Each feature was chosen because it has a distinct human signature that automated tools cannot naturally replicate.

### Keystroke Dynamics — anchored to CMU Dataset (Killourhy & Maxion, 2009)

| Feature | What it measures | Why bots fail |
|---|---|---|
| avgFlightTime | Mean gap between consecutive keydowns | Bots have near-zero or constant gaps |
| stdFlightTime | Variance in flight times | Bots have zero variance — robotic precision |
| backspaceRatio | Correction rate (backspaces / keystrokes) | Bots never mistype |
| keystrokeCount | Total keydown events | Bots submit with minimal keystrokes |
| medianFlightTime | Robust timing measure | Robust to outliers |
| avgKeyHoldTime | Key press duration | Bots have near-zero hold times |
| typingSpeed | Keystrokes per second | Bots type at inhuman speed |
| backspaceCount | Raw correction count | Always zero for bots |
| specialKeyCount | Tab, Shift, Enter usage | Bots skip natural navigation keys |

### Mouse Dynamics — anchored to BALABIT Dataset & Fitts' Law

| Feature | What it measures | Why bots fail |
|---|---|---|
| meanMouseDistance | Average step size between events | Zero for bots that don't move the mouse |
| stdMouseDistance | Path variation | Bots move in straight lines — zero variation |
| mouseEventCount | Number of mouse events | Bots generate minimal or zero events |
| meanMouseInterval | Time between mouse events | Constant for bots |
| mouseDistance | Total cumulative path length | Near-zero for bots |
| avgMouseSpeed | Average velocity | Constant for bots — no bell curve |
| maxMouseSpeed | Peak speed | Equals avgMouseSpeed for bots (no Fitts acceleration peak) |
| meanClickInterval | Time between clicks | Near-zero or constant for bots |
| clickCount | Number of clicks | Always minimal for bots |
| clickFrequency | Clicks per second | Extremely high for automated tools |

### Session Context

| Feature | What it measures | Why bots fail |
|---|---|---|
| pageDwellSeconds | Total time on page | Too short for bots |
| timeBeforeFirstInput | Reading time before typing | Near-zero for bots — no reading |
| formCompletionTime | First to last keystroke duration | Too short for bots |
| fieldSwitchCount | Tab/click between fields | Zero or one for bots |
| tabSwitchCount | Browser tab switches | Always zero for bots |
| windowBlurCount | Window focus changes | Always zero for bots |
| navigationCount | Page navigation events | Zero for bots going straight to submit |
| idleTimeRatio | Fraction of dwell time with gaps >3s | Always zero for bots |
| keystrokeCount2 | Intentional duplicate of keystrokeCount | Additional VAE weighting for critical feature |

---

## VAE Model Architecture

```
Input (28 features, StandardScaled)
        │
        ▼
Encoder: Linear(28→64) → ReLU → Linear(64→32) → ReLU
        │
        ▼
Latent space: μ (6), log_σ² (6)
  └── latent_dim=6 chosen deliberately — forces aggressive compression
      Large latent dim (e.g. 32) allows the VAE to reconstruct anomalies
      6 dimensions means the model can only reconstruct what it truly learned
        │
   Reparameterisation: z = μ + σ·ε,  ε ~ N(0,I)
        │
        ▼
Decoder: Linear(6→32) → ReLU → Linear(32→64) → ReLU → Linear(64→28)
        │
        ▼
Reconstruction  →  MSE(input, reconstruction)
        │
        ▼
Normalised sigmoid:  prob = 1 / (1 + exp(5 × (mse/THRESHOLD − 1)))
  └── Normalised form prevents float overflow that occurred with raw MSE
      Original formula exp(mse − threshold) overflowed at threshold=4.14
      Steepness factor=5 gives clean transition around the threshold
```

**Training details:**
- Training data: 15,000 synthetic normal samples only — VAE never sees bot data
- KL annealing: beta ramps 0→1 over 100 epochs to prevent posterior collapse
- Optimiser: Adam, lr=1e-3
- Epochs: 200
- Loss: Reconstruction MSE + β·KL divergence

**Synthetic data generation:**
- Keystroke distributions anchored to CMU dataset (51 subjects)
- Mouse distributions anchored to BALABIT dataset (10 users)
- Movement timing validated against Fitts' Law: MT = 50 + 150·log₂(2D/W)
- Mouse paths validated against curvature index: 1.05–3.0 (human arcs, not straight lines)
- 6 human personas weighted by population: fast (15%), average (40%), slow (20%), hunt-and-peck (10%), elderly (8%), power (7%)

**Key finding — 49% threshold discrepancy:**
Threshold calibrated on synthetic data: 4.14. After live endpoint calibration: 2.10. This 49% gap confirms that synthetic data underestimates real human variance and is documented as a research finding rather than a failure. The `uba_accepted` table accumulates real user data for future retraining to close this gap.

---

## Evaluation Results

| Profile | Type | MSE | Probability | Decision |
|---|---|---|---|---|
| Real human (live test) | Normal | 1.932 | 0.9351 | ✅ ACCEPTED |
| Average typist (synthetic) | Normal | 1.262 | 0.9699 | ✅ ACCEPTED |
| Slow/elderly typist | Normal | 1.901 | 0.9375 | ✅ ACCEPTED |
| Distracted human | Edge case | 2.289 | 0.3897 | ⚠️ REVIEW |
| Dumb bot (10ms flight) | Attack | 2.382 | 0.3380 | ❌ REJECTED |
| Smart bot (jitter added) | Attack | 2.333 | 0.3649 | ❌ REJECTED |
| Headless browser (Selenium) | Attack | 2.337 | 0.3627 | ❌ REJECTED |
| Human-mimicking bot | Attack | 2.382 | 0.3383 | ❌ REJECTED |
| Credential stuffing tool | Attack | 2.991 | 0.1070 | ❌ REJECTED |

**TPR (bot detection): 100% · TNR (human acceptance): 100%**

The distracted human at 0.39 correctly lands in the REVIEW zone rather than being auto-rejected — demonstrating the three-zone model handling edge cases exactly as designed.

---

## Kafka Async Pipeline — Why and How

### Why async at all

VAE inference takes ~10ms. Synchronous processing with Tomcat's default 200-thread pool gives theoretical throughput of 20,000 req/s. But under real-world concurrent load with database writes and email sending, threads exhaust and requests timeout. Kafka decouples the HTTP layer from processing — the controller returns 202 in milliseconds regardless of VAE throughput.

### Why MANUAL_IMMEDIATE ack mode

Default auto-commit advances the Kafka offset when the message is received, regardless of whether processing succeeded. If the consumer crashes mid-inference, the message is lost permanently. MANUAL_IMMEDIATE means `ack.acknowledge()` must be called explicitly. If the consumer crashes, Kafka redelivers the message automatically — the message is never lost.

### The retry mechanism

```
Message received by consumer
        │
        ▼
try {
    VAE inference + DB save + email
    ack.acknowledge()  ← success, offset advances, done
}
catch (Exception e) {
    request.incrementRetry()

    if retryCount < 3:
        // No ack — Kafka redelivers the same message
        // retryCount saved to DB for visibility

    if retryCount >= 3:
        // Send to uba-vae-dead-letter topic
        // Send on-hold email to user
        ack.acknowledge()  ← done, no more attempts
}
```

The retry mechanism handles **infrastructure failures only** — ONNX crashes, database outages, network timeouts. It never retries a VAE rejection. A rejection is a decision, not an error. Retrying a rejection would be a security flaw.

The 3-retry limit follows Nygard (2018, Release It!): beyond 3 attempts, the failure is almost certainly systemic rather than transient and requires human intervention.

---

## Hyperledger Fabric — Tamper-Evident Audit Trail

### The problem Fabric solves

PostgreSQL is mutable. An admin with database access can run:
```sql
DELETE FROM uba_rejected WHERE email = 'attacker@bot.com';
```
And the attack never happened. The audit trail has no memory of it.

Fabric provides an **independent, append-only ledger**. When a VAE decision is made, a SHA-256 hash of the database row is committed to the Fabric ledger. The ledger cannot be modified. If someone later changes or deletes the PostgreSQL row, re-hashing the current row produces a different hash that mismatches the original on the ledger.

### How tamper detection works

```
Registration accepted
        │
        ▼
hash = SHA-256(recordId + email + decision + createdAt + vaeScore + mseScore)
        │
        ├── Fabric.commitRecord(uuid, email, decision, hash, ...)
        │   └── Stored permanently on immutable ledger
        │
        └── PostgreSQL: fabricHash = hash, fabricCommittedAt = now

Every hour (TamperDetectionScheduler):
        │
        ▼
For each record with fabricHash IS NOT NULL:
    currentHash = SHA-256(current DB row)
    ledgerHash  = Fabric.verifyRecord(uuid).combinedHash

    if currentHash ≠ ledgerHash:
        LOG ERROR: TAMPER ALERT — DB row modified after Fabric commit
```

### Why Hyperledger Fabric specifically

| Requirement | Why Fabric |
|---|---|
| Data privacy | Permissioned — only authorised nodes see the data |
| No fees | No cryptocurrency, no gas costs |
| Speed | Transaction finality in seconds |
| Enterprise grade | IBM Food Trust, Walmart supply chain, major banks |
| Auditability | Every transaction has full provenance trail |

### CCAAS deployment

The chaincode runs as a standalone external process (Chaincode as a Service) rather than inside a Docker container. This bypasses the Docker-in-Docker problem encountered on Mac with Docker Desktop, where the peer's internal Docker client (API v1.25) conflicts with modern Docker Desktop requirements. CCAAS is also the recommended approach for production Fabric deployments.

---

## Three-Table Schema — Human-in-the-Loop Retraining

The three output tables are not just storage — they form a retraining pipeline.

```
uba_accepted
└── Confirmed normal interactions accumulate over time
    SELECT * FROM uba_accepted → clean training data for VAE retrain
    After sufficient real data: retrain VAE on real users, not just synthetic

uba_review
└── Borderline cases routed here instead of auto-rejected
    Human reviewer labels each record: LEGITIMATE or BOT
    Labelled records provide supervised fine-tuning signal
    Enables empirical threshold recalibration

uba_rejected
└── Bot signatures with MSE values
    MSE distribution of rejected requests → calibrate threshold empirically
    Attack patterns (IP, email, timing) → forensic analysis
    All committed to Fabric → evidence cannot be deleted
```

Every record in all three tables stores the complete `UbaTelemetrySnapshot`:
- All 28 computed feature values
- Raw event counts (keystrokes, mouse events, clicks)
- VAE reconstruction error and probability score
- Session metadata (IP, sessionId)
- Fabric hash and commit timestamp

This means the full picture is always available for retraining, forensics, and audit — not just a summary.

---

## Repository Structure

```
UBA_Using_VAE_Angular_SpringBoot/
│
├── uba_research_ui/                          Angular 17 frontend
│   └── src/app/
│       ├── core/services/
│       │   ├── uba-tracker.service.ts        28-feature telemetry collection
│       │   ├── auth.service.ts               Register/login — handles 202 async
│       │   ├── crypto.service.ts             AES-256-GCM + RSA-OAEP encryption
│       │   └── session-store.service.ts      Session state management
│       └── features/
│           ├── auth/
│           │   ├── register/                 Registration form + UBA integration
│           │   └── login/                    Login form + credential verification
│           └── dashboard/
│               └── dashboard.component.ts   Audit dashboard with charts + tables
│
├── uba-research-backend/                     Spring Boot 3 backend
│   └── src/main/java/com/forensic/audit/
│       ├── analysis/
│       │   └── VAEAnalysis.java              ONNX inference, three-zone decision
│       ├── commons/
│       │   └── Metadata.java                Telemetry deserialisation model
│       ├── config/
│       │   └── AsyncConfig.java             @Async thread pool for email
│       ├── controller/
│       │   ├── AuthController.java          Register (202) + Login (200/401)
│       │   ├── DashboardController.java     /api/dashboard/* endpoints
│       │   └── TestVAEController.java       Debug endpoint (dev profile only)
│       ├── email/
│       │   └── EmailService.java            Welcome + on-hold + vague rejection
│       ├── exception/
│       │   └── DuplicateEmailException.java 409 on duplicate registration
│       ├── fabric/
│       │   ├── FabricService.java           Fabric Gateway connection + commit/verify
│       │   ├── FabricHashService.java       SHA-256 of DB rows
│       │   └── TamperDetectionScheduler.java Hourly hash comparison
│       ├── filter/
│       │   └── UbaDecryptionFilter.java     AES+RSA payload decryption
│       ├── kafka/
│       │   ├── AnalysisRequest.java         State machine entity (RECEIVED→DONE)
│       │   ├── AnalysisRequestRepository.java
│       │   ├── KafkaConfig.java             Topics + MANUAL_IMMEDIATE factory
│       │   ├── VaeAnalysisMessage.java      Kafka message DTO
│       │   ├── VaeRequestProducer.java      Publish to uba-vae-requests
│       │   └── VaeRequestConsumer.java      Consume, infer, route, email, Fabric
│       ├── uba/
│       │   ├── UbaTelemetrySnapshot.java    @Embeddable — all 28 features + metadata
│       │   ├── UbaAccepted.java             Confirmed human records
│       │   ├── UbaAcceptedRepository.java
│       │   ├── UbaReview.java               Borderline — human review queue
│       │   ├── UbaReviewRepository.java
│       │   ├── UbaRejected.java             Attack attempts — forensic evidence
│       │   └── UbaRejectedRepository.java
│       └── user/
│           ├── User.java                    Registered users table
│           └── repository/UserRepository.java
│       └── resources/
│           └── models/
│               └── user_behavior_vae.onnx   Trained VAE — copy here after training
│
├── uba-research-pythonAI/                    Python ML pipeline
│   ├── data_generator.py                    Synthetic data (CMU + BALABIT + Fitts)
│   ├── vae_model.py                         VAE training + evaluation + ONNX export
│   ├── test_vae_endpoint.py                 8-profile bot detection test suite
│   └── balabit_loader.py                    BALABIT dataset preprocessor
│
├── uba-analytics-library/                    Shared Angular UBA components
│
└── [separate — ~/fabric-samples/audit-chaincode/]
    ├── audit_contract.go                    Chaincode — CommitRecord, VerifyRecord
    ├── main_server.go                       CCAAS server with TLS disabled
    ├── audit_main.go                        Entry point
    └── go.mod                              go 1.21, fabric-contract-api-go v1.2.2
```

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 17+ | Spring Boot backend |
| Maven | 3.6+ | Backend build |
| Node.js | 18+ | Angular frontend |
| Angular CLI | 17+ | Frontend tooling |
| Python | 3.10+ | VAE training pipeline |
| Docker Desktop | 20+ | Kafka + Zookeeper |
| PostgreSQL | 18 (Homebrew) | Persistent audit database |
| Go | 1.21+ | Fabric chaincode |
| Hyperledger Fabric | 2.5 | Blockchain ledger |

---

## Setup and Installation

### Step 1 — Environment variables (add to ~/.zshrc)

```bash
export DOCKER_SOCK=/Users/<your-username>/.docker/run/docker.sock
export DOCKER_API_VERSION=1.41
export PATH=$PATH:~/fabric-samples/bin
```

### Step 2 — PostgreSQL (native Homebrew — persists independently of Docker)

```bash
brew install postgresql@18
brew services start postgresql@18

psql postgres
```

```sql
CREATE USER uba_user WITH PASSWORD 'uba_password';
CREATE DATABASE uba_db OWNER uba_user;
GRANT ALL PRIVILEGES ON DATABASE uba_db TO uba_user;
\q
```

### Step 3 — Train the VAE (run once)

```bash
cd uba-research-pythonAI
pip install torch numpy pandas scikit-learn matplotlib joblib requests

# Download datasets
# CMU: https://www.cs.cmu.edu/~keystroke/ → DSL-StrongPasswordData.csv
# BALABIT: git clone https://github.com/balabit/Mouse-Dynamics-Challenge
python balabit_loader.py

python data_generator.py   # generates normal_behavior_features.npy
python vae_model.py        # trains VAE, exports ONNX, prints Java constants
```

After training:
```bash
# Copy the ONNX model
cp user_behavior_vae.onnx ../uba-research-backend/src/main/resources/models/

# Paste the printed SCALER_MEAN, SCALER_SCALE, and THRESHOLD
# into VAEAnalysis.java — these three constants are critical
```

### Step 4 — Hyperledger Fabric (run once to deploy chaincode)

```bash
# Download Fabric binaries and samples
cd ~
curl -sSL https://bit.ly/2ysbOFE | bash -s -- 2.5.0 1.5.7

# Fix Docker API version in peer compose file
sed -i '' 's/- CORE_VM_ENDPOINT=unix:\/\/\/host\/var\/run\/docker.sock/- CORE_VM_ENDPOINT=unix:\/\/\/host\/var\/run\/docker.sock\n      - DOCKER_API_VERSION=1.41/g' \
  ~/fabric-samples/test-network/compose/docker/docker-compose-test-net.yaml

# Create the chaincode
mkdir -p ~/fabric-samples/audit-chaincode
# Copy audit_contract.go, main_server.go, audit_main.go, go.mod into this directory

cd ~/fabric-samples/audit-chaincode
go mod tidy && go mod vendor

# Start the network and create the audit channel
cd ~/fabric-samples/test-network
./network.sh up createChannel -c auditchannel -s couchdb

# Create the CCAAS package
mkdir -p ~/fabric-samples/audit-chaincode/ccaas-package
cat > ~/fabric-samples/audit-chaincode/ccaas-package/metadata.json << 'EOF'
{"type":"ccaas","label":"auditcontract_1.0"}
EOF
cat > ~/fabric-samples/audit-chaincode/ccaas-package/connection.json << 'EOF'
{"address":"host.docker.internal:9999","dial_timeout":"10s","tls_required":false}
EOF
cd ~/fabric-samples/audit-chaincode/ccaas-package
tar cfz code.tar.gz connection.json
tar cfz ../auditcontract-ccaas.tar.gz metadata.json code.tar.gz

# Build the chaincode server binary
cd ~/fabric-samples/audit-chaincode
GOARCH=arm64 GOOS=darwin go build -o auditcontract-server .

# Install, approve, and commit (run all peer commands from test-network directory)
# See SETUP_GUIDE.md for the complete peer lifecycle commands
```

### Step 5 — application.properties

```properties
spring.profiles.active=dev

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/uba_db
spring.datasource.username=uba_user
spring.datasource.password=uba_password
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.group-id=uba-vae-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.properties.spring.json.trusted.packages=com.forensic.audit.*

# Gmail SMTP (generate an app password at myaccount.google.com → Security → App passwords)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=xxxx-xxxx-xxxx-xxxx
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# Fabric verification schedule (milliseconds)
fabric.verification.interval=3600000
```

---

## Running the System

Open five terminal windows:

```bash
# Terminal 1 — Kafka
docker compose -f docker-compose-kafka.yml up -d

# Terminal 2 — Fabric network (after initial setup)
cd ~/fabric-samples/test-network
./network.sh up createChannel -c auditchannel -s couchdb
# Then run peer install/approve/commit commands (see SETUP_GUIDE.md)

# Terminal 3 — Fabric chaincode server
cd ~/fabric-samples/audit-chaincode
CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999 \
CORE_CHAINCODE_ID_NAME=auditcontract_1.0:<package-id> \
./auditcontract-server

# Terminal 4 — Spring Boot
cd uba-research-backend
mvn spring-boot:run
# Watch for: [VAE] Model loaded — input_dim=28  threshold=X.XXXXXXX

# Terminal 5 — Angular
cd uba_research_ui
ng serve
# Open: http://localhost:4200
```

---

## Testing Bot Detection

The test suite runs 8 profiles against the live endpoint and verifies all decisions:

```bash
cd uba-research-pythonAI
python test_vae_endpoint.py
```

Expected output:
```
Profile              MSE        Probability  Decision    Correct
real_human           1.932      0.9351       ACCEPTED    ✓
average_typist       1.262      0.9699       ACCEPTED    ✓
slow_typist          1.901      0.9375       ACCEPTED    ✓
distracted_human     2.289      0.3897       REVIEW      ✓
dumb_bot             2.382      0.3380       REJECTED    ✓
smart_bot            2.333      0.3649       REJECTED    ✓
headless_browser     2.337      0.3627       REJECTED    ✓
human_mimicking_bot  2.382      0.3383       REJECTED    ✓
credential_stuffing  2.991      0.1070       REJECTED    ✓

TPR: 100%  TNR: 100%
```

---

## Dashboard

Navigate to `http://localhost:4200/dashboard` after logging in.

The dashboard auto-refreshes every 15 seconds and shows:

- **Five stat cards** — accepted registrations, under review, rejected attempts, dead letter failures, records committed to Fabric ledger with coverage percentage
- **Decision donut chart** — proportion of each outcome
- **Retry distribution bar chart** — how many requests required 0, 1, 2, or 3 retries before succeeding or dead lettering
- **VAE probability scatter plot** — every score plotted with the 0.65 accept and 0.40 reject threshold lines visible
- **Three data tables** — accepted (with ledger status), review (with human review label), rejected (with rejection reason and ledger status)

---

## Security Design

| Layer | Mechanism | Why |
|---|---|---|
| Transport | HTTPS | Standard — encrypts in transit |
| Payload | AES-256-GCM + RSA-OAEP | Prevents Burp Suite telemetry tampering |
| Bot detection | VAE anomaly detection | No labelled attack data needed |
| Async resilience | Kafka MANUAL_IMMEDIATE | No message loss on consumer crash |
| Retry | 3 retries + dead letter | Transient vs systemic failure distinction |
| Rejection messaging | Deliberately vague | Attacker receives no feedback to tune bot |
| Password telemetry | MASKED literal | Password never enters Kafka or DB |
| Clipboard | Blocked at DOM level | Paste cannot bypass keystroke tracking |
| DevTools | F12 / Ctrl+Shift+I blocked | Partial mitigation against casual inspection |
| Debug endpoints | @Profile("dev") only | Never exposed in production |
| Audit integrity | Hyperledger Fabric | DB tampering detected by hash mismatch |

---

## Penetration Resistance — What Happens Under Burp Suite Interception

This section documents the specific protections against HTTP interception and request tampering, which is a realistic attack vector against any web registration endpoint.

### What the intercepted request looks like

When Burp Suite captures the registration request, the attacker sees:

```json
{
  "payload": {
    "email": "target@example.com",
    "password": "Test@1234"
  },
  "metadata": {
    "encryptedData": "aGVsbG8gd29ybGQ...",
    "encryptedKey":  "c2VjcmV0a2V5...",
    "iv":            "randomIV123..."
  }
}
```

The `payload` is readable — email and password are in plaintext (protected by HTTPS at the transport layer). The `metadata` field containing the 28-feature behavioural telemetry is entirely opaque. The attacker cannot read it, cannot modify it without detection, and cannot replace it without the server's private key.

### Attack vector 1 — Modify the encrypted telemetry

The attacker changes bytes in `encryptedData` to try to substitute bot telemetry for human telemetry.

**What happens:** AES-256-GCM is authenticated encryption. The ciphertext includes a 128-bit authentication tag computed over both the ciphertext and the associated data. Any modification to `encryptedData` — even a single bit — causes authentication tag verification to fail on decryption. Spring Boot's `UbaDecryptionFilter` throws a `BadPaddingException` or `AEADBadTagException` and rejects the request before it reaches the controller. The attack fails at the decryption layer.

### Attack vector 2 — Replace the metadata with crafted bot telemetry

The attacker generates their own telemetry JSON, encrypts it with AES-256-GCM, and encrypts their AES key with the server's public key (obtainable from `/api/crypto/public-key`).

**What happens:** The decryption succeeds — this is a technically valid encrypted payload. But now the attacker must generate 28 features that simultaneously satisfy the VAE's learned distribution of normality. They need:

- Correct flight time distributions matching CMU population statistics
- Correct mouse movement with curvature index 1.05–3.0
- Correct Fitts' Law movement timing
- Correct correlations between features (a fast typist also has fast mouse movement)
- Correct session context (realistic idle ratios, page dwell, time before first input)

All 28 features must be consistent with each other and with the population distribution the VAE was trained on. This is a non-trivial technical challenge — essentially requiring the attacker to implement a realistic human behaviour simulator, which is exactly what the system is designed to detect. If the attacker can perfectly simulate all 28 features simultaneously, they are behaviourally indistinguishable from a human.

### Attack vector 3 — Replay a captured legitimate request

The attacker captures a genuine human registration (with valid encrypted telemetry) and replays it with a different email.

**What happens:** The replayed request passes VAE analysis because the telemetry is from a real human. However, two mitigations apply:

**Mitigation A — Duplicate email check:** The `VaeRequestProducer` checks `userRepository.existsByEmail()` before publishing to Kafka. If the original user's email was already registered, the replay with a new email passes this check but the telemetry's session context (IP address, sessionId) does not match the new request's metadata. The `AnalysisRequest` table logs the requestId, IP, and timestamp — replay patterns across multiple attempts are detectable by an analyst reviewing the `uba_accepted` table.

**Mitigation B — Timestamp correlation (future hardening):** The telemetry contains computed timestamps — `timeBeforeFirstInput`, `pageDwellSeconds`, `formCompletionTime`. These are computed from DOM event timestamps relative to page load. A replayed request arrives at the server at a different wall-clock time from when the telemetry was recorded. Server-side timestamp validation comparing the telemetry's `formCompletionTime` against the actual request arrival time would detect replays where the telemetry claims a 15-second form fill but the request arrived 2 hours after the session started. This is proposed as a future hardening step.

### Attack vector 4 — Direct API call bypassing Angular entirely

The attacker bypasses the browser entirely and calls `POST /api/auth/register` directly with crafted JSON.

**What happens:** Without going through the Angular `CryptoService`, the attacker cannot produce a valid `encryptedKey` + `encryptedData` + `iv` combination that decrypts correctly on the server. The server's RSA private key is never transmitted. Without it, a valid `encryptedKey` cannot be produced. The `UbaDecryptionFilter` rejects the request.

The attacker would need to reverse-engineer the client-side encryption implementation from the compiled Angular bundle and implement it externally — significant effort for a one-time registration bypass.

### Summary — defence-in-depth model

```
Layer 1 — HTTPS
  Encrypts transport. Prevents passive interception.
  Does not prevent active MITM or Burp Suite.

Layer 2 — AES-256-GCM authenticated encryption
  Prevents telemetry modification — any change detected.
  Prevents reading telemetry content.

Layer 3 — RSA-OAEP key wrapping
  Prevents crafting a valid replacement payload without the server private key.
  Public key is available but private key never leaves the server.

Layer 4 — VAE anomaly detection
  Even if a valid encrypted payload is crafted, the 28 features must
  simultaneously match the human behavioural distribution.
  Protects against a sophisticated attacker who implements AES+RSA correctly.

Layer 5 — Duplicate detection + audit logging
  Replay attacks produce detectable patterns in the uba_accepted table.
  fabricHash on Fabric means even accepted replays leave an immutable trace.

Layer 6 — Hyperledger Fabric
  Any post-acceptance DB manipulation is detected by hash mismatch.
  An attacker who somehow gets a record accepted cannot erase the evidence.
```

No single layer is sufficient. The combination means an attacker must defeat cryptographic authentication, generate realistic multi-dimensional behavioural telemetry, and avoid leaving detectable patterns — in sequence. Each layer independently raises the cost of attack.

---

## Known Limitations and Future Work

| Limitation | Impact | Proposed mitigation |
|---|---|---|
| Synthetic training data | 49% threshold discrepancy observed | Retrain on uba_accepted real user data |
| Mobile users excluded | Touch events incompatible | Separate mobile VAE with touch dynamics |
| Assistive technology users | Higher false positive risk | Wider review zone for accessible deployments |
| Browser auto-fill | keystrokeCount near-zero for legit users | Detect and handle auto-fill separately |
| CMU demographic bias | 51 subjects, likely university students | Supplement with broader datasets |
| Single-node evaluation | Kafka scaling theoretical only | JMeter load test |
| No feature ablation | Feature importance unknown | Remove each category, measure AUC drop |
| Login not VAE-protected | Stolen credentials bypass detection | Per-user Mahalanobis distance baseline |
| Feature drift over time | VAE degrades as behaviour changes | Evidently AI drift detection integration |

---

## Academic References

- Killourhy, K.S. & Maxion, R.A. (2009). Comparing anomaly-detection algorithms for keystroke dynamics. *DSN 2009*.
- Fulop, A. et al. (2016). BALABIT Mouse Dynamics Challenge dataset.
- Fitts, P.M. (1954). The information capacity of the human motor system. *Journal of Experimental Psychology, 47*(6), 381–391.
- Kingma, D.P. & Welling, M. (2013). Auto-encoding variational Bayes. *arXiv:1312.6114*.
- Nygard, M. (2018). *Release It! Design and Deploy Production-Ready Software* (2nd ed.). Pragmatic Bookshelf.

---

## Author

**Mithil Baria**
MSc Advanced Computer Science · University of Manchester · 2025–2026

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Mithil%20Baria-blue?style=flat-square&logo=linkedin)](https://linkedin.com/in/mithil-baria-887347173/)
[![Portfolio](https://img.shields.io/badge/Portfolio-mithil--portfolio-purple?style=flat-square)](https://mithil-portfolio-seven.vercel.app/)
[![GitHub](https://img.shields.io/badge/GitHub-Mithil21-black?style=flat-square&logo=github)](https://github.com/Mithil21)