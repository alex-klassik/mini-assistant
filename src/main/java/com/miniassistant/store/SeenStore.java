package com.miniassistant.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Идемпотентность обработки писем: раз обработанный id (Outlook EntryID /
 * Message-ID) больше не считается новым, даже после рестарта процесса.
 * Формат хранения - простой текстовый файл, один id на строку, дописываемый
 * (append-only). Файл и его родительская директория могут отсутствовать при
 * первом запуске - это не ошибка, а нормальное "ничего ещё не видели".
 */
public final class SeenStore {

    private final Path filePath;
    private final Set<String> seenIds;

    public SeenStore(Path filePath) {
        this.filePath = filePath;
        this.seenIds = readExistingIds(filePath);
    }

    public boolean isSeen(String id) {
        return seenIds.contains(id);
    }

    public void markSeen(String id) {
        if (!seenIds.add(id)) {
            return;
        }
        appendId(id);
    }

    private static Set<String> readExistingIds(Path filePath) {
        if (!Files.exists(filePath)) {
            return new LinkedHashSet<String>();
        }
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            Set<String> ids = new LinkedHashSet<String>();
            for (String line : lines) {
                String id = line.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read seen-store file: " + filePath, e);
        }
    }

    private void appendId(String id) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(filePath, Collections.singletonList(id), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write to seen-store file: " + filePath, e);
        }
    }
}
