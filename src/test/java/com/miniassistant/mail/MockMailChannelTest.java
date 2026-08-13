package com.miniassistant.mail;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MockMailChannelTest {

    @Test
    public void fetchUnreadReturnsPreconfiguredMessages() {
        Msg msg1 = new Msg("id-1", "alice@example.com", "Напоминание",
                "Напомни завтра в 10", Instant.parse("2026-08-13T10:00:00Z"));
        Msg msg2 = new Msg("id-2", "bob@example.com", "Список",
                "Покажи список напоминаний", Instant.parse("2026-08-13T11:00:00Z"));

        MockMailChannel channel = new MockMailChannel(msg1, msg2);

        List<Msg> unread = channel.fetchUnread();

        assertEquals(2, unread.size());
        assertSame(msg1, unread.get(0));
        assertSame(msg2, unread.get(1));
    }

    @Test
    public void replyRecordsBodyForVerification() {
        Msg msg = new Msg("id-1", "alice@example.com", "Напоминание",
                "Напомни завтра в 10", Instant.parse("2026-08-13T10:00:00Z"));
        MockMailChannel channel = new MockMailChannel(msg);

        channel.reply(msg, "Готово, напомню.");

        List<MockMailChannel.RecordedReply> replies = channel.repliesSent();
        assertEquals(1, replies.size());
        assertSame(msg, replies.get(0).original);
        assertEquals("Готово, напомню.", replies.get(0).body);
    }
}
