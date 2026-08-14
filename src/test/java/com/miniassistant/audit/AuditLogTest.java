package com.miniassistant.audit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuditLogTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void appendedEntriesFormAVerifiableChain() {
        HmacSigner signer = new HmacSigner("test-key");
        AuditLog log = new AuditLog(pathTo("audit.jsonl"), signer);

        log.append("processed msgId=1");
        log.append("tool_call=add_reminder msgId=1");
        log.append("replied msgId=1");

        assertTrue(log.verifyChain());
    }

    @Test
    public void chainSurvivesReopeningOverSameFile() {
        Path path = pathTo("audit.jsonl");
        HmacSigner signer = new HmacSigner("test-key");

        AuditLog first = new AuditLog(path, signer);
        first.append("processed msgId=1");

        // Симулируем рестарт процесса: новый AuditLog поверх того же файла
        // должен продолжить существующую цепочку, а не начать новую с нуля.
        AuditLog reopened = new AuditLog(path, signer);
        reopened.append("processed msgId=2");

        assertTrue(reopened.verifyChain());
    }

    @Test
    public void tamperedEntryIsDetectedByVerifyChain() throws IOException {
        Path path = pathTo("audit.jsonl");
        HmacSigner signer = new HmacSigner("test-key");
        AuditLog log = new AuditLog(path, signer);

        log.append("processed msgId=1");
        log.append("replied msgId=1");
        assertTrue(log.verifyChain());

        // Подмена содержимого первой записи "руками", как при взломе файла
        // журнала в обход HmacSigner - hash в этой строке остаётся старым.
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        lines.set(0, lines.get(0).replace("msgId=1", "msgId=999"));
        Files.write(path, lines, StandardCharsets.UTF_8);

        AuditLog reloaded = new AuditLog(path, signer);
        assertFalse(reloaded.verifyChain());
    }

    private Path pathTo(String relative) {
        return new File(tempFolder.getRoot(), relative).toPath();
    }
}
