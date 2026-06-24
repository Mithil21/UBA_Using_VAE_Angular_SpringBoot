package com.forensic.audit.kafka;

import com.forensic.audit.commons.Metadata;
import com.forensic.audit.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The message placed on the Kafka topic.
 * Contains everything the consumer needs to run VAE analysis
 * and persist the result — no further DB lookups required.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VAEAnalysisMessage {

    private String     requestId;
    private String     requestType;   // "REGISTER" or "LOGIN"
    private User       user;
    private Metadata<User> metadata;
}