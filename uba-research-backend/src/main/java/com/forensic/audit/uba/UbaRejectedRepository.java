package com.forensic.audit.uba;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UbaRejectedRepository extends JpaRepository<UbaRejected, String> {

    // Attack pattern analysis — how many rejections per IP
    @Query("SELECT u.telemetry.ipAddress, COUNT(u) FROM UbaRejected u " +
            "GROUP BY u.telemetry.ipAddress ORDER BY COUNT(u) DESC")
    List<Object[]> findRejectionCountByIp();

    // Emails being targeted — credential stuffing analysis
    @Query("SELECT u.email, COUNT(u) FROM UbaRejected u " +
            "GROUP BY u.email ORDER BY COUNT(u) DESC")
    List<Object[]> findRejectionCountByEmail();

    // Threshold calibration — MSE distribution of rejected requests
    @Query("SELECT u.telemetry.reconstructionError FROM UbaRejected u " +
            "ORDER BY u.telemetry.reconstructionError")
    List<Float> findAllReconstructionErrors();

    // Fabric verification
    List<UbaRejected> findByFabricHashIsNull();
}