package com.miniassistant.mail;

import java.time.Instant;

/**
 * Одно письмо: неизменяемый снимок данных, которые нужны агенту для обработки
 * (без вложений и MIME-деталей - этого не требует задание).
 */
public final class Msg {

    private final String id;
    private final String from;
    private final String subject;
    private final String body;
    private final Instant receivedAt;

    public Msg(String id, String from, String subject, String body, Instant receivedAt) {
        this.id = id;
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.receivedAt = receivedAt;
    }

    public String getId() {
        return id;
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
