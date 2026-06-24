package com.forensic.audit.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, String> {
}
