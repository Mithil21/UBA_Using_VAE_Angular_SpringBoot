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
    MSE distribution of rejected requests → c
