package com.miniassistant.audit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;

/**
 * Append-only журнал действий агента (JSONL): каждая запись подписана
 * HMAC-хешем, который зависит от хеша предыдущей записи. Подмена или
 * удаление любой строки рвёт цепочку и обнаруживается {@link #verifyChain()}
 * - без знания ключа {@link HmacSigner} подделать хеш нельзя. Переоткрытие
 * над тем же файлом ({@code new AuditLog(...)} после рестарта процесса)
 * продолжает существующую цепочку, а не начинает новую.
 */
public final class AuditLog {

    private static final String GENESIS_HASH = "GENESIS";

    private final Path filePath;
    private final HmacSigner signer;
    private final ObjectMapper mapper = new ObjectMapper();
    private String currentHash;

    public AuditLog(Path filePath, HmacSigner signer) {
        this.filePath = filePath;
        this.signer = signer;
        this.currentHash = readLastHash();
    }

    public synchronized void append(String event) {
        String hash = signer.sign(currentHash + event);
        writeLine(new AuditEntry(event, hash));
        currentHash = hash;
    }

    /** {@code true}, если цепочка хешей от начала файла до конца целая. */
    public synchronized boolean verifyChain() {
        String expectedPrevHash = GENESIS_HASH;
        for (String line : readAllLines()) {
            AuditEntry entry = parseLine(line);
            String expectedHash = signer.sign(expectedPrevHash + entry.getEvent());
            if (!expectedHash.equals(entry.getHash())) {
                return false;
            }
            expectedPrevHash = entry.getHash();
        }
        return true;
    }

    private String readLastHash() {
        List<String> lines = readAllLines();
        if (lines.isEmpty()) {
            return GENESIS_HASH;
        }
        return parseLine(lines.get(lines.size() - 1)).getHash();
    }

    private List<String> readAllLines() {
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }
        try {
            return Files.readAllLines(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read audit log file: " + filePath, e);
        }
    }

    private AuditEntry parseLine(String line) {
        try {
            return mapper.readValue(line, AuditEntry.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse audit log line in file: " + filePath, e);
        }
    }

    private void writeLine(AuditEntry entry) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = mapper.writeValueAsString(entry);
            Files.write(filePath, Collections.singletonList(json), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write to audit log file: " + filePath, e);
        }
    }
}
