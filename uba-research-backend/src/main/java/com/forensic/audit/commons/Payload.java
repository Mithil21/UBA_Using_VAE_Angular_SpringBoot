package com.forensic.audit.commons;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NonNull;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Payload <T>{
    @NonNull
    private T payload;
    private SecurityContext securityContext;
    private Metadata<T> metadata;
}
