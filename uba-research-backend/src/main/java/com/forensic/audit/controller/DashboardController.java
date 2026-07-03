package com.forensic.audit.controller;

import com.forensic.audit.kafka.AnalysisRequest;
import com.forensic.audit.kafka.AnalysisRequestRepository;
import com.forensic.audit.uba.UbaAcceptedRepository;
import com.forensic.audit.uba.UbaRejectedRepository;
import com.forensic.audit.uba.UbaReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class DashboardController {

    private final AnalysisRequestRepository requestRepository;
    private final UbaAcceptedRepository     acceptedRepository;
    private final UbaReviewRepository       reviewRepository;
    private final UbaRejectedRepository     rejectedRepository;

    /**
     * GET /api/dashboard/stats
     * Returns summary counts for the dashboard cards and charts.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {

        long totalRequests  = requestRepository.count();
        long totalAccepted  = acceptedRepository.count();
        long totalReview    = reviewRepository.count();
        long totalRejected  = rejectedRepository.count();
        long fabricCommitted = acceptedRepository.findAll().stream()
                .filter(r -> r.getFabricHash() != null).count()
                + reviewRepository.findAll().stream()
                .filter(r -> r.getFabricHash() != null).count()
                + rejectedRepository.findAll().stream()
                .filter(r -> r.getFabricHash() != null).count();

        // Retry distribution from analysis_requests
        List<AnalysisRequest> allRequests = requestRepository.findAll();

        Map<String, Long> stateDistribution = allRequests.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getState() != null ? r.getState().toString() : "UNKNOWN",
                        Collectors.counting()
                ));

        // Retry count distribution — how many had 0, 1, 2, 3 retries
        Map<Integer, Long> retryDistribution = allRequests.stream()
                .collect(Collectors.groupingBy(
                        AnalysisRequest::getRetryCount,
                        Collectors.counting()
                ));

        // Dead letter count
        long deadLetterCount = allRequests.stream()
                .filter(r -> r.getState() != null &&
                        r.getState().toString().equals("DEAD_LETTER"))
                .count();

        // Fabric coverage percentage
        double fabricCoverage = totalRequests > 0
                ? (fabricCommitted * 100.0) / (totalAccepted + totalReview + totalRejected)
                : 0.0;

        return ResponseEntity.ok(Map.of(
                "totalRequests",      totalRequests,
                "totalAccepted",      totalAccepted,
                "totalReview",        totalReview,
                "totalRejected",      totalRejected,
                "deadLetterCount",    deadLetterCount,
                "fabricCommitted",    fabricCommitted,
                "fabricCoverage",     Math.round(fabricCoverage * 10.0) / 10.0,
                "stateDistribution",  stateDistribution,
                "retryDistribution",  retryDistribution
        ));
    }

    /**
     * GET /api/dashboard/accepted
     * Returns all accepted records with telemetry summary.
     */
    @GetMapping("/accepted")
    public ResponseEntity<?> getAccepted() {
        var records = acceptedRepository.findAll().stream().map(r -> Map.of(
                "recordId",     r.getRecordId(),
                "email",        r.getEmail(),
                "decision",     r.getDecision(),
                "createdAt",    r.getCreatedAt().toString(),
                "vaeScore",     r.getTelemetry() != null ? r.getTelemetry().getNormalProbability() : 0,
                "mseScore",     r.getTelemetry() != null ? r.getTelemetry().getReconstructionError() : 0,
                "fabricHash",   r.getFabricHash() != null ? r.getFabricHash().substring(0, 8) + "..." : "pending",
                "onLedger",     r.getFabricHash() != null
        )).toList();
        return ResponseEntity.ok(records);
    }

    /**
     * GET /api/dashboard/review
     * Returns all review records.
     */
    @GetMapping("/review")
    public ResponseEntity<?> getReview() {
        var records = reviewRepository.findAll().stream().map(r -> Map.of(
                "recordId",     r.getRecordId(),
                "email",        r.getEmail(),
                "decision",     r.getDecision(),
                "createdAt",    r.getCreatedAt().toString(),
                "vaeScore",     r.getTelemetry() != null ? r.getTelemetry().getNormalProbability() : 0,
                "mseScore",     r.getTelemetry() != null ? r.getTelemetry().getReconstructionError() : 0,
                "reviewLabel",  r.getReviewLabel() != null ? r.getReviewLabel() : "PENDING",
                "onLedger",     r.getFabricHash() != null
        )).toList();
        return ResponseEntity.ok(records);
    }

    /**
     * GET /api/dashboard/rejected
     * Returns all rejected records.
     */
    @GetMapping("/rejected")
    public ResponseEntity<?> getRejected() {
        var records = rejectedRepository.findAll().stream().map(r -> Map.of(
                "recordId",        r.getRecordId(),
                "email",           r.getEmail(),
                "decision",        r.getDecision(),
                "createdAt",       r.getCreatedAt().toString(),
                "vaeScore",        r.getTelemetry() != null ? r.getTelemetry().getNormalProbability() : 0,
                "mseScore",        r.getTelemetry() != null ? r.getTelemetry().getReconstructionError() : 0,
                "rejectionReason", r.getRejectionReason() != null ? r.getRejectionReason() : "VAE_SCORE_LOW",
                "onLedger",        r.getFabricHash() != null
        )).toList();
        return ResponseEntity.ok(records);
    }

    /**
     * GET /api/dashboard/requests
     * Returns all Kafka state machine records — shows retry history.
     */
    @GetMapping("/requests")
    public ResponseEntity<?> getRequests() {
        var records = requestRepository.findAll().stream().map(r -> Map.of(
                "requestId",   r.getRequestId(),
                "state",       r.getState() != null ? r.getState().toString() : "UNKNOWN",
                "retryCount",  r.getRetryCount(),
                "createdAt",   r.getCreatedAt() != null ? r.getCreatedAt().toString() : "",
                "message",     r.getMessage() != null ? r.getMessage() : ""
        )).toList();
        return ResponseEntity.ok(records);
    }

    /**
     * GET /api/dashboard/vae-scores
     * Returns VAE scores for scatter plot visualisation.
     */
    @GetMapping("/vae-scores")
    public ResponseEntity<?> getVaeScores() {
        var accepted = acceptedRepository.findAll().stream().map(r -> Map.of(
                "decision", "ACCEPTED",
                "vaeScore", r.getTelemetry() != null ? r.getTelemetry().getNormalProbability() : 0,
                "mseScore", r.getTelemetry() != null ? r.getTelemetry().getReconstructionError() : 0,
                "email",    r.getEmail()
        )).toList();

        var review = reviewRepository.findAll().stream().map(r -> Map.of(
                "decision", "REVIEW",
                "vaeScore", r.getTelemetry() != null ? r.getTelemetry().getNormalProbability() : 0,
                "mseScore", r.getTelemetry() != null ? r.getTelemetry().getReconstructionError() : 0,
                "email",    r.getEmail()
        )).toList();

        var rejected = rejectedRepository.findAll().stream().map(r -> Map.of(
                "decision", "REJECTED",
                "vaeScore", r.getTelemetry() != null ? r.getTelemetry().getNormalProbability() : 0,
                "mseScore", r.getTelemetry() != null ? r.getTelemetry().getReconstructionError() : 0,
                "email",    r.getEmail()
        )).toList();

        return ResponseEntity.ok(Map.of(
                "accepted", accepted,
                "review",   review,
                "rejected", rejected
        ));
    }
}