package com.forensic.audit.uba;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embeddable block storing all 28 computed VAE features
 * plus raw counts from the telemetry payload.
 * Embedded into UbaAccepted, UbaReview, and UbaRejected
 * so every table has the full picture for retraining.
 */
@Embeddable
@Data
@NoArgsConstructor
public class UbaTelemetrySnapshot {

    // ── VAE result ────────────────────────────────────────────────────────
    private float reconstructionError;
    private float normalProbability;

    // ── Keystroke dynamics (features 0-4, 19-22) ─────────────────────────
    private double avgFlightTime;
    private double stdFlightTime;
    private double backspaceRatio;
    private int    keystrokeCount;
    private double medianFlightTime;
    private double avgKeyHoldTime;
    private double typingSpeed;
    private int    backspaceCount;
    private int    specialKeyCount;

    // ── Click features (features 5-6, 26) ────────────────────────────────
    private double meanClickInterval;
    private int    clickCount;
    private double clickFrequency;

    // ── Mouse dynamics (features 7-10, 23-26) ────────────────────────────
    private double meanMouseDistance;
    private double stdMouseDistance;
    private int    mouseEventCount;
    private double meanMouseInterval;
    private double mouseDistance;
    private double avgMouseSpeed;
    private double maxMouseSpeed;

    // ── Session context (features 11-17, 27) ─────────────────────────────
    private double pageDwellSeconds;
    private int    tabSwitchCount;
    private int    windowBlurCount;
    private int    navigationCount;
    private long   timeBeforeFirstInput;
    private long   formCompletionTime;
    private int    fieldSwitchCount;
    private double idleTimeRatio;

    // ── Raw event counts (for retraining reference) ───────────────────────
    private int    rawKeystrokeEventCount;
    private int    rawMouseEventCount;
    private int    rawClickCount;
    private int    rawNavigationCount;
    private int    clipboardAttemptCount;

    // ── Session metadata ──────────────────────────────────────────────────
    private String sessionId;
    private String ipAddress;
    private String location;
}