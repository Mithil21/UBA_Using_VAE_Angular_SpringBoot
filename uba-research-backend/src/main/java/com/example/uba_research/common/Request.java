package com.example.uba_research.common;

public class Request<T> {
    private T payload;
    private Metadata metadata;

    public T getRequest() {
        return payload;
    }

    public void setRequest(T request) {
        this.payload = request;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
