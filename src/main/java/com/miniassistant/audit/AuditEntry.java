package com.miniassistant.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Одна строка {@link AuditLog}: текст события и HMAC-хеш, продолжающий цепочку. */
public final class AuditEntry {

    private final String event;
    private final String hash;

    @JsonCreator
    public AuditEntry(@JsonProperty("event") String event, @JsonProperty("hash") String hash) {
        this.event = event;
        this.hash = hash;
    }

    public String getEvent() {
        return event;
    }

    public String getHash() {
        return hash;
    }
}
