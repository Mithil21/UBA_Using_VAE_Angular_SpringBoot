package com.forensic.audit.uba;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UbaAcceptedRepository extends JpaRepository<UbaAccepted, String> {

    boolean existsByEmail(String email);

    // Used for retraining — fetch all confirmed normal records
    // SELECT * FROM uba_accepted WHERE fabric_hash IS NOT NULL
    // (only use fabric-verified records for retraining)
    @Query("SELECT u FROM UbaAccepted u WHERE u.fabricHash IS NOT NULL")
    List<UbaAccepted> findVerifiedForRetraining();

    // Fabric verification job — find records not yet committed to ledger
    List<UbaAccepted> findByFabricHashIsNull();
}