package com.miniassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CurrentDatetimeToolTest {

    @Test
    public void executeReturnsIsoTimeFromInjectedClock() throws Exception {
        Instant fixedInstant = Instant.parse("2026-08-13T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        CurrentDatetimeTool tool = new CurrentDatetimeTool(fixedClock);

        String resultJson = tool.execute("{}");

        JsonNode result = new ObjectMapper().readTree(resultJson);
        assertEquals("2026-08-13T12:00:00Z", result.get("iso").asText());
    }

    @Test
    public void nameMatchesToolContractFromPlan() {
        CurrentDatetimeTool tool = new CurrentDatetimeTool(Clock.systemUTC());

        assertEquals("current_datetime", tool.name());
        assertFalse(tool.description().isEmpty());
    }
}
