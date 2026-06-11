package com.forensic.audit.commons;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityContext {
    private long timestamp;
    private String nonce;
}
