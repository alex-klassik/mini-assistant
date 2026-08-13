package com.miniassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Сохраняет напоминание из аргументов вызова в {@link ReminderStore}. */
public final class AddReminderTool implements Tool {

    private final ReminderStore store;
    private final ObjectMapper mapper = new ObjectMapper();

    public AddReminderTool(ReminderStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "add_reminder";
    }

    @Override
    public String description() {
        return "Сохраняет напоминание с текстом и сроком выполнения (ISO-8601).";
    }

    @Override
    public String jsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"text\":{\"type\":\"string\"},"
                + "\"dueIso\":{\"type\":\"string\"}},"
                + "\"required\":[\"text\",\"dueIso\"]}";
    }

    @Override
    public String execute(String argsJson) {
        JsonNode args = parseArgs(argsJson);
        Reminder reminder = store.add(args.get("text").asText(), args.get("dueIso").asText());
        return writeJson(reminder);
    }

    private JsonNode parseArgs(String argsJson) {
        try {
            return mapper.readTree(argsJson);
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid JSON arguments for add_reminder: " + argsJson, e);
        }
    }

    private String writeJson(Reminder reminder) {
        try {
            return mapper.writeValueAsString(reminder);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize add_reminder result", e);
        }
    }
}
