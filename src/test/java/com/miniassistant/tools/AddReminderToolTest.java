package com.miniassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AddReminderToolTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void executePersistsReminderAndReturnsItAsJson() throws Exception {
        ReminderStore store = new ReminderStore(new File(tempFolder.getRoot(), "reminders.json").toPath());
        AddReminderTool tool = new AddReminderTool(store);

        String resultJson = tool.execute("{\"text\":\"Позвонить клиенту\",\"dueIso\":\"2026-08-14T09:00:00Z\"}");

        JsonNode result = new ObjectMapper().readTree(resultJson);
        assertFalse(result.get("id").asText().isEmpty());
        assertEquals("Позвонить клиенту", result.get("text").asText());
        assertEquals("2026-08-14T09:00:00Z", result.get("dueIso").asText());
        assertEquals(1, store.findByText("клиенту").size());
    }

    @Test
    public void nameMatchesToolContractFromPlan() {
        ReminderStore store = new ReminderStore(new File(tempFolder.getRoot(), "reminders.json").toPath());
        AddReminderTool tool = new AddReminderTool(store);

        assertEquals("add_reminder", tool.name());
    }
}
