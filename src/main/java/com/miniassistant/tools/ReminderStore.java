package com.miniassistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Хранилище напоминаний в виде JSON-файла (массив {@link Reminder}). В отличие
 * от {@code SeenStore} (append-only построчный файл), здесь при каждом
 * изменении весь файл перезаписывается целиком - формат структурный (JSON),
 * а не построчный, и записей ожидается немного.
 */
public final class ReminderStore {

    private final Path filePath;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Reminder> reminders;

    public ReminderStore(Path filePath) {
        this.filePath = filePath;
        this.reminders = readExisting(filePath, mapper);
    }

    public synchronized Reminder add(String text, String dueIso) {
        Reminder reminder = new Reminder(UUID.randomUUID().toString(), text, dueIso);
        reminders.add(reminder);
        persist();
        return reminder;
    }

    /** Подстроковый (регистронезависимый) поиск по тексту напоминания. */
    public synchronized List<Reminder> findByText(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<Reminder> matches = new ArrayList<Reminder>();
        for (Reminder reminder : reminders) {
            if (reminder.getText().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(reminder);
            }
        }
        return matches;
    }

    private static List<Reminder> readExisting(Path filePath, ObjectMapper mapper) {
        if (!Files.exists(filePath)) {
            return new ArrayList<Reminder>();
        }
        try {
            Reminder[] stored = mapper.readValue(filePath.toFile(), Reminder[].class);
            return new ArrayList<Reminder>(Arrays.asList(stored));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read reminder store file: " + filePath, e);
        }
    }

    private void persist() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(filePath.toFile(), reminders);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write reminder store file: " + filePath, e);
        }
    }
}
