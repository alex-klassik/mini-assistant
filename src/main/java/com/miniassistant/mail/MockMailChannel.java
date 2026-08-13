package com.miniassistant.mail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Рукописный фейк {@link MailChannel} для тестов: {@link #fetchUnread()} всегда
 * отдаёт письма, переданные в конструктор, а {@link #reply(Msg, String)} не
 * отправляет ничего по-настоящему, а лишь запоминает вызов для проверки в
 * тесте через {@link #repliesSent()}.
 */
public final class MockMailChannel implements MailChannel {

    /** Один зафиксированный вызов {@link #reply(Msg, String)}. */
    public static final class RecordedReply {
        public final Msg original;
        public final String body;

        RecordedReply(Msg original, String body) {
            this.original = original;
            this.body = body;
        }
    }

    private final List<Msg> unread;
    private final List<RecordedReply> replies = new ArrayList<RecordedReply>();

    public MockMailChannel(Msg... unread) {
        this.unread = new ArrayList<Msg>(Arrays.asList(unread));
    }

    @Override
    public List<Msg> fetchUnread() {
        return Collections.unmodifiableList(unread);
    }

    @Override
    public void reply(Msg original, String body) {
        replies.add(new RecordedReply(original, body));
    }

    /** Все ответы, отправленные через {@link #reply(Msg, String)}, в порядке вызовов. */
    public List<RecordedReply> repliesSent() {
        return Collections.unmodifiableList(replies);
    }
}
