package com.miniassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FindItemsToolTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void executeFindsPreviouslyAddedReminderBySubstring() throws Exception {
        ReminderStore store = new ReminderStore(new File(tempFolder.getRoot(), "reminders.json").toPath());
        store.add("Купить билеты", "2026-08-20T10:00:00Z");
        store.add("Позвонить клиенту", "2026-08-14T09:00:00Z");
        FindItemsTool tool = new FindItemsTool(store);

        String resultJson = tool.execute("{\"query\":\"билеты\"}");

        JsonNode result = new ObjectMapper().readTree(resultJson);
        assertTrue(result.isArray());
        assertEquals(1, result.size());
        assertEquals("Купить билеты", result.get(0).get("text").asText());
    }

    @Test
    public void executeReturnsEmptyArrayWhenNothingMatches() throws Exception {
        ReminderStore store = new ReminderStore(new File(tempFolder.getRoot(), "reminders.json").toPath());
        store.add("Купить билеты", "2026-08-20T10:00:00Z");
        FindItemsTool tool = new FindItemsTool(store);

        String resultJson = tool.execute("{\"query\":\"нет такого\"}");

        JsonNode result = new ObjectMapper().readTree(resultJson);
        assertTrue(result.isArray());
        assertEquals(0, result.size());
    }

    @Test
    public void nameMatchesToolContractFromPlan() {
        ReminderStore store = new ReminderStore(new File(tempFolder.getRoot(), "reminders.json").toPath());
        FindItemsTool tool = new FindItemsTool(store);

        assertEquals("find_items", tool.name());
    }
}
