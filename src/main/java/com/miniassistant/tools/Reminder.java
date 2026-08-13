package com.miniassistant.tools;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Одна запись в {@link ReminderStore}: неизменяемый снимок данных напоминания. */
public final class Reminder {

    private final String id;
    private final String text;
    private final String dueIso;

    @JsonCreator
    public Reminder(@JsonProperty("id") String id,
                     @JsonProperty("text") String text,
                     @JsonProperty("dueIso") String dueIso) {
        this.id = id;
        this.text = text;
        this.dueIso = dueIso;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getDueIso() {
        return dueIso;
    }
}
