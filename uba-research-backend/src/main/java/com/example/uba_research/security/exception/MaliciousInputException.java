package com.example.uba_research.security.exception;

public class MaliciousInputException extends RuntimeException {
    private final double anomalyScore;
    private final String timestamp;

    public MaliciousInputException(String message, double anomalyScore, String timestamp) {
        super(message);
        this.anomalyScore = anomalyScore;
        this.timestamp = timestamp;
    }

    public MaliciousInputException(String message) {
        super(message);
        this.anomalyScore = 0.0;
        this.timestamp = null;
    }

    public double getAnomalyScore() {
        return anomalyScore;
    }

    public String getTimestamp() {
        return timestamp;
    }
}