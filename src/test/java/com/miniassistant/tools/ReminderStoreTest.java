package com.miniassistant.tools;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ReminderStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void addPersistsReminderWithGeneratedId() {
        ReminderStore store = new ReminderStore(pathTo("reminders.json"));

        Reminder reminder = store.add("Позвонить клиенту", "2026-08-14T09:00:00Z");

        assertNotNull(reminder.getId());
        assertEquals("Позвонить клиенту", reminder.getText());
        assertEquals("2026-08-14T09:00:00Z", reminder.getDueIso());
    }

    @Test
    public void newInstanceOverSameFileSeesPreviouslyAddedRemindersAfterRestart() {
        Path path = pathTo("reminders.json");
        ReminderStore beforeRestart = new ReminderStore(path);
        beforeRestart.add("Позвонить клиенту", "2026-08-14T09:00:00Z");

        ReminderStore afterRestart = new ReminderStore(path);

        List<Reminder> found = afterRestart.findByText("клиенту");
        assertEquals(1, found.size());
        assertEquals("Позвонить клиенту", found.get(0).getText());
    }

    @Test
    public void findByTextMatchesCaseInsensitiveSubstring() {
        ReminderStore store = new ReminderStore(pathTo("reminders.json"));
        store.add("Купить билеты", "2026-08-20T10:00:00Z");
        store.add("Позвонить клиенту", "2026-08-14T09:00:00Z");

        List<Reminder> found = store.findByText("БИЛЕТЫ");

        assertEquals(1, found.size());
        assertEquals("Купить билеты", found.get(0).getText());
    }

    @Test
    public void findByTextReturnsEmptyListWhenNothingMatches() {
        ReminderStore store = new ReminderStore(pathTo("reminders.json"));
        store.add("Купить билеты", "2026-08-20T10:00:00Z");

        List<Reminder> found = store.findByText("нет такого");

        assertTrue(found.isEmpty());
    }

    private Path pathTo(String relative) {
        return new File(tempFolder.getRoot(), relative).toPath();
    }
}
