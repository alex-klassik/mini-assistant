package com.miniassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Ищет ранее сохранённые напоминания по подстроке в {@link ReminderStore}. */
public final class FindItemsTool implements Tool {

    private final ReminderStore store;
    private final ObjectMapper mapper = new ObjectMapper();

    public FindItemsTool(ReminderStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "find_items";
    }

    @Override
    public String description() {
        return "Ищет ранее сохранённые напоминания по подстроке в тексте.";
    }

    @Override
    public String jsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\"}},"
                + "\"required\":[\"query\"]}";
    }

    @Override
    public String execute(String argsJson) {
        JsonNode args = parseArgs(argsJson);
        List<Reminder> matches = store.findByText(args.get("query").asText());
        return writeJson(matches);
    }

    private JsonNode parseArgs(String argsJson) {
        try {
            return mapper.readTree(argsJson);
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid JSON arguments for find_items: " + argsJson, e);
        }
    }

    private String writeJson(List<Reminder> matches) {
        try {
            return mapper.writeValueAsString(matches);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize find_items result", e);
        }
    }
}
