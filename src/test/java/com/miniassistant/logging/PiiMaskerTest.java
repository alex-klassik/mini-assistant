package com.miniassistant.logging;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PiiMaskerTest {

    @Test
    public void maskReplacesEmailAddressWithPlaceholder() {
        String masked = PiiMasker.mask("contact me at john.doe@example.com please");

        assertFalse(masked.contains("john.doe@example.com"));
        assertTrue(masked.contains("[EMAIL]"));
    }

    @Test
    public void maskReplacesEachEmailAddressWhenTextContainsSeveral() {
        String masked = PiiMasker.mask("cc: a@example.com and b@example.org");

        assertFalse(masked.contains("a@example.com"));
        assertFalse(masked.contains("b@example.org"));
        assertEquals(2, countOccurrences(masked, "[EMAIL]"));
    }

    @Test
    public void maskLeavesTextWithoutEmailAddressesUnchanged() {
        String text = "upstream rejected request: timeout after 30s";

        assertEquals(text, PiiMasker.mask(text));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
