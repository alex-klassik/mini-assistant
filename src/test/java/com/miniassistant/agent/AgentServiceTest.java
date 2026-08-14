package com.miniassistant.agent;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniassistant.audit.AuditEntry;
import com.miniassistant.audit.AuditLog;
import com.miniassistant.audit.HmacSigner;
import com.miniassistant.llm.ChatMessage;
import com.miniassistant.llm.ChatResponse;
import com.miniassistant.llm.LlmClient;
import com.miniassistant.llm.MockLlmClient;
import com.miniassistant.llm.ToolCall;
import com.miniassistant.llm.ToolSpec;
import com.miniassistant.logging.Events;
import com.miniassistant.mail.MailChannel;
import com.miniassistant.mail.MockMailChannel;
import com.miniassistant.mail.Msg;
import com.miniassistant.store.SeenStore;
import com.miniassistant.tools.AddReminderTool;
import com.miniassistant.tools.CurrentDatetimeTool;
import com.miniassistant.tools.FindItemsTool;
import com.miniassistant.tools.ReminderStore;
import com.miniassistant.tools.Tool;
import com.miniassistant.tools.ToolRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AgentServiceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void processesFourGoldenEmailsFromTheAssignment() {
        Msg reminderMsg = new Msg("msg-reminder", "user@example.com", "Напоминание",
                "Напомни мне позвонить клиенту завтра в 15:00", Instant.parse("2026-08-14T09:00:00Z"));
        Msg listMsg = new Msg("msg-list", "user@example.com", "Список",
                "Покажи мои напоминания", Instant.parse("2026-08-14T09:05:00Z"));
        Msg dateMsg = new Msg("msg-date", "user@example.com", "Дата",
                "Какая сегодня дата?", Instant.parse("2026-08-14T09:10:00Z"));
        Msg garbageMsg = new Msg("msg-garbage", "user@example.com", "",
                "???", Instant.parse("2026-08-14T09:15:00Z"));
        MockMailChannel mailChannel = new MockMailChannel(reminderMsg, listMsg, dateMsg, garbageMsg);

        ReminderStore reminderStore = new ReminderStore(pathTo("reminders.json"));
        reminderStore.add("купить молоко", "2026-08-16T10:00:00Z");

        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-14T09:00:00Z"), ZoneOffset.UTC);
        ToolRegistry registry = new ToolRegistry(Arrays.<Tool>asList(
                new AddReminderTool(reminderStore),
                new FindItemsTool(reminderStore),
                new CurrentDatetimeTool(fixedClock)));

        MockLlmClient llm = new MockLlmClient(
                // 1. напоминание
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall(
                        "call-reminder", "add_reminder",
                        "{\"text\":\"позвонить клиенту\",\"dueIso\":\"2026-08-15T15:00:00Z\"}"))),
                ChatResponse.text("Напоминание добавлено."),
                // 2. список
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall(
                        "call-list", "find_items", "{\"query\":\"\"}"))),
                ChatResponse.text("Вот ваши напоминания: купить молоко."),
                // 3. текущая дата
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall(
                        "call-date", "current_datetime", "{}"))),
                ChatResponse.text("Сегодня 2026-08-14."),
                // 4. пустое/мусорное письмо - модель отвечает сразу, без инструментов
                ChatResponse.text("Не понял ваш запрос, уточните, пожалуйста."));

        ToolLoop toolLoop = new ToolLoop(llm, registry, 5);
        SeenStore seenStore = new SeenStore(pathTo("seen.txt"));
        AuditLog auditLog = newAuditLog();
        AgentService agentService = new AgentService(mailChannel, toolLoop, seenStore, auditLog);

        agentService.processUnread();

        List<MockMailChannel.RecordedReply> replies = mailChannel.repliesSent();
        assertEquals(4, replies.size());

        assertSame(reminderMsg, replies.get(0).original);
        assertEquals("Напоминание добавлено.", replies.get(0).body);

        assertSame(listMsg, replies.get(1).original);
        assertEquals("Вот ваши напоминания: купить молоко.", replies.get(1).body);

        assertSame(dateMsg, replies.get(2).original);
        assertEquals("Сегодня 2026-08-14.", replies.get(2).body);

        assertSame(garbageMsg, replies.get(3).original);
        assertEquals("Не понял ваш запрос, уточните, пожалуйста.", replies.get(3).body);

        // Тексты финальных ответов - просто скрипт мока. Дальше проверяем, что
        // соответствующие инструменты реально выполнились, а не были пропущены.
        assertTrue("add_reminder должен был сохранить новую запись",
                reminderStore.findByText("позвонить клиенту").size() == 1);
        assertTrue("find_items должен был найти предзаполненную запись",
                lastMessageOfCall(llm, 3).getContent().contains("купить молоко"));
        assertEquals("{\"iso\":\"2026-08-14T09:00:00Z\"}", lastMessageOfCall(llm, 5).getContent());
    }

    @Test
    public void repeatedProcessUnreadDoesNotReplyTwiceToSameMessage() {
        Msg msg = new Msg("msg-reminder", "user@example.com", "Напоминание",
                "Напомни мне позвонить клиенту завтра в 15:00", Instant.parse("2026-08-14T09:00:00Z"));
        MockMailChannel mailChannel = new MockMailChannel(msg);

        MockLlmClient llm = new MockLlmClient(
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall(
                        "call-1", "add_reminder",
                        "{\"text\":\"позвонить клиенту\",\"dueIso\":\"2026-08-15T15:00:00Z\"}"))),
                ChatResponse.text("Напоминание добавлено."));

        ReminderStore reminderStore = new ReminderStore(pathTo("reminders.json"));
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>singletonList(new AddReminderTool(reminderStore)));
        ToolLoop toolLoop = new ToolLoop(llm, registry, 5);
        SeenStore seenStore = new SeenStore(pathTo("seen.txt"));
        AgentService agentService = new AgentService(mailChannel, toolLoop, seenStore, newAuditLog());

        agentService.processUnread();
        // Повторный опрос того же MailChannel (то же "непрочитанное" письмо) не
        // должен породить второй ответ - SeenStore должен отфильтровать его. Если
        // бы AgentService переобработал письмо, MockLlmClient бросил бы
        // IllegalStateException (скрипт из двух ответов уже исчерпан).
        agentService.processUnread();

        assertEquals(1, mailChannel.repliesSent().size());
        assertEquals(1, reminderStore.findByText("позвонить клиенту").size());
    }

    @Test
    public void llmFailureSendsFallbackReplyMarksSeenAndDoesNotRetryOnNextPoll() {
        Msg msg = new Msg("msg-1", "user@example.com", "Вопрос",
                "Расскажи анекдот", Instant.parse("2026-08-14T09:00:00Z"));
        MockMailChannel mailChannel = new MockMailChannel(msg);

        CountingThrowingLlmClient llm = new CountingThrowingLlmClient();
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>emptyList());
        ToolLoop toolLoop = new ToolLoop(llm, registry, 5);
        SeenStore seenStore = new SeenStore(pathTo("seen.txt"));
        AgentService agentService = new AgentService(mailChannel, toolLoop, seenStore, newAuditLog());

        agentService.processUnread();
        // Повторный опрос не должен снова дёргать упавший LlmClient - письмо
        // уже помечено обработанным (фолбэк-ответ был успешно отправлен).
        agentService.processUnread();

        assertEquals(1, mailChannel.repliesSent().size());
        assertEquals(AgentService.LLM_FAILURE_FALLBACK, mailChannel.repliesSent().get(0).body);
        assertTrue(seenStore.isSeen("msg-1"));
        assertEquals(1, llm.callCount());
    }

    @Test
    public void mailSendFailureLogsWarnAndStillProcessesNextMessageInBatch() {
        Msg failing = new Msg("msg-fail", "user@example.com", "Первое",
                "Первый вопрос", Instant.parse("2026-08-14T09:00:00Z"));
        Msg ok = new Msg("msg-ok", "user@example.com", "Второе",
                "Второй вопрос", Instant.parse("2026-08-14T09:05:00Z"));
        FlakyMailChannel mailChannel = new FlakyMailChannel("msg-fail", failing, ok);

        MockLlmClient llm = new MockLlmClient(
                ChatResponse.text("ответ на первое"),
                ChatResponse.text("ответ на второе"));
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>emptyList());
        ToolLoop toolLoop = new ToolLoop(llm, registry, 5);
        SeenStore seenStore = new SeenStore(pathTo("seen.txt"));
        AgentService agentService = new AgentService(mailChannel, toolLoop, seenStore, newAuditLog());

        agentService.processUnread();

        assertEquals(1, mailChannel.successfulReplies().size());
        assertSame(ok, mailChannel.successfulReplies().get(0));
        assertFalse("недоставленное письмо не должно считаться обработанным",
                seenStore.isSeen("msg-fail"));
        assertTrue(seenStore.isSeen("msg-ok"));
    }

    @Test
    public void successfullyProcessedMessageAppendsAuditEntryForMailAndForEachToolCall() {
        Msg msg = new Msg("msg-1", "user@example.com", "Напоминание",
                "Напомни мне позвонить клиенту завтра в 15:00", Instant.parse("2026-08-14T09:00:00Z"));
        MockMailChannel mailChannel = new MockMailChannel(msg);

        MockLlmClient llm = new MockLlmClient(
                ChatResponse.toolCalls(Collections.singletonList(new ToolCall(
                        "call-1", "add_reminder",
                        "{\"text\":\"позвонить клиенту\",\"dueIso\":\"2026-08-15T15:00:00Z\"}"))),
                ChatResponse.text("Напоминание добавлено."));

        ReminderStore reminderStore = new ReminderStore(pathTo("reminders.json"));
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>singletonList(new AddReminderTool(reminderStore)));
        ToolLoop toolLoop = new ToolLoop(llm, registry, 5);
        SeenStore seenStore = new SeenStore(pathTo("seen.txt"));
        AuditLog auditLog = newAuditLog();
        AgentService agentService = new AgentService(mailChannel, toolLoop, seenStore, auditLog);

        agentService.processUnread();

        assertTrue("цепочка хешей аудит-журнала должна быть целой", auditLog.verifyChain());
        List<String> events = readAuditEvents();
        assertEquals(2, events.size());
        assertEquals("event=" + Events.TOOL_CALLED + " tool=add_reminder", events.get(0));
        assertEquals("event=" + Events.MAIL_PROCESSED + " msgId=msg-1", events.get(1));
    }

    @Test
    public void llmFailureLogsMaskedErrorWithoutLeakingEmailAddress() {
        Msg msg = new Msg("msg-1", "user@example.com", "Вопрос",
                "Расскажи анекдот", Instant.parse("2026-08-14T09:00:00Z"));
        MockMailChannel mailChannel = new MockMailChannel(msg);

        ToolRegistry registry = new ToolRegistry(Collections.<Tool>emptyList());
        ToolLoop toolLoop = new ToolLoop(new EmailLeakingLlmClient(), registry, 5);
        SeenStore seenStore = new SeenStore(pathTo("seen.txt"));
        AgentService agentService = new AgentService(mailChannel, toolLoop, seenStore, newAuditLog());

        Logger agentLogger = (Logger) LoggerFactory.getLogger(AgentService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        agentLogger.addAppender(appender);

        try {
            agentService.processUnread();
        } finally {
            agentLogger.detachAppender(appender);
        }

        boolean foundMaskedEvent = false;
        for (ILoggingEvent event : appender.list) {
            String formatted = event.getFormattedMessage();
            assertFalse("email адрес не должен попадать в лог в открытом виде: " + formatted,
                    formatted.contains("victim@example.com"));
            if (formatted.contains("event=" + Events.LLM_FAILED)) {
                assertTrue("замаскированный текст ошибки должен попасть в лог вместо адреса",
                        formatted.contains("[EMAIL]"));
                foundMaskedEvent = true;
            }
        }
        assertTrue("должно быть залогировано событие " + Events.LLM_FAILED, foundMaskedEvent);
    }

    private static final class EmailLeakingLlmClient implements LlmClient {
        @Override
        public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) {
            throw new RuntimeException("upstream rejected request for victim@example.com");
        }
    }

    private static final class CountingThrowingLlmClient implements LlmClient {
        private int callCount = 0;

        @Override
        public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) {
            callCount++;
            throw new RuntimeException("simulated LLM timeout");
        }

        int callCount() {
            return callCount;
        }
    }

    private static final class FlakyMailChannel implements MailChannel {
        private final List<Msg> unread;
        private final String failingMsgId;
        private final List<Msg> successfulReplies = new ArrayList<>();

        FlakyMailChannel(String failingMsgId, Msg... unread) {
            this.failingMsgId = failingMsgId;
            this.unread = Arrays.asList(unread);
        }

        @Override
        public List<Msg> fetchUnread() {
            return unread;
        }

        @Override
        public void reply(Msg original, String body) {
            if (original.getId().equals(failingMsgId)) {
                throw new RuntimeException("simulated COM error while sending reply");
            }
            successfulReplies.add(original);
        }

        List<Msg> successfulReplies() {
            return successfulReplies;
        }
    }

    private static ChatMessageContent lastMessageOfCall(MockLlmClient llm, int callIndex) {
        return new ChatMessageContent(llm.recordedMessages().get(callIndex));
    }

    /** Небольшая обёртка, чтобы не тащить import ChatMessage только ради одного метода. */
    private static final class ChatMessageContent {
        private final List<com.miniassistant.llm.ChatMessage> messages;

        ChatMessageContent(List<com.miniassistant.llm.ChatMessage> messages) {
            this.messages = messages;
        }

        String getContent() {
            return messages.get(messages.size() - 1).getContent();
        }
    }

    private Path pathTo(String relative) {
        return new File(tempFolder.getRoot(), relative).toPath();
    }

    private AuditLog newAuditLog() {
        return new AuditLog(pathTo("audit.jsonl"), new HmacSigner("test-hmac-key"));
    }

    private List<String> readAuditEvents() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<String> events = new ArrayList<>();
            for (String line : Files.readAllLines(pathTo("audit.jsonl"), StandardCharsets.UTF_8)) {
                events.add(mapper.readValue(line, AuditEntry.class).getEvent());
            }
            return events;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
