package com.forensic.audit.user.repository;

import com.forensic.audit.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    // Add to UserRepository.java
    boolean existsByEmail(String email);

}
