package com.forensic.audit.uba;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UbaReviewRepository extends JpaRepository<UbaReview, String> {

    // Human review queue — all unlabelled borderline cases
    List<UbaReview> findByReviewLabelIsNull();

    // Labelled records ready for retraining
    List<UbaReview> findByReviewLabelIsNotNull();

    // Confirmed legitimate users from review — can be promoted to retraining
    List<UbaReview> findByReviewLabel(String reviewLabel);

    // Fabric verification
    List<UbaReview> findByFabricHashIsNull();
}