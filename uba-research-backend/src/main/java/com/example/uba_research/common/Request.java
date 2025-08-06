package com.example.uba_research.common;

import jakarta.validation.constraints.NotNull;

public class Request<T> {
    @NotNull(message = "Payload cannot be null")
    private T payload;
    private Metadata metadata;

    public T getPayload() {
        return payload;
    }

    public void setRequest(T payload) {
        this.payload = payload;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
