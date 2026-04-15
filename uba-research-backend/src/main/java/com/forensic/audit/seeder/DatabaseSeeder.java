package com.forensic.audit.seeder;


import com.forensic.audit.user.User;
import com.forensic.audit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    private static final String ADMIN_EMAIL = "admin@test.com";
    // NOTE: Plaintext password — strictly for local testing only.
    // Replace with BCryptPasswordEncoder in production.
    private static final String ADMIN_PASSWORD = "password123";

//    public DatabaseSeeder(UserRepository userRepository) {
//        this.userRepository = userRepository;
//    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            if (!userRepository.existsByEmail(ADMIN_EMAIL)) {
                userRepository.save(new User(ADMIN_EMAIL, ADMIN_PASSWORD));
//                log.info("Admin user seeded successfully.");
            }
        } catch (DataIntegrityViolationException e) {
            // Swallow: another node/thread beat us to it — idempotent outcome
//            log.warn("Admin user already exists (concurrent insert detected), skipping.");
        }
    }
}
