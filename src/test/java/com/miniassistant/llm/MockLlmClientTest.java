package com.miniassistant.llm;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MockLlmClientTest {

    @Test
    public void chatReturnsScriptedResponsesInCallOrder() {
        ChatResponse first = ChatResponse.text("Привет!");
        ChatResponse second = ChatResponse.text("Пока!");
        MockLlmClient client = new MockLlmClient(first, second);

        ChatResponse actualFirst = client.chat(
                Collections.singletonList(ChatMessage.user("Привет")),
                Collections.<ToolSpec>emptyList());
        ChatResponse actualSecond = client.chat(
                Collections.singletonList(ChatMessage.user("Пока")),
                Collections.<ToolSpec>emptyList());

        assertSame(first, actualFirst);
        assertSame(second, actualSecond);
    }

    @Test(expected = IllegalStateException.class)
    public void chatThrowsWhenScriptIsExhausted() {
        MockLlmClient client = new MockLlmClient(ChatResponse.text("единственный ответ"));

        client.chat(Collections.singletonList(ChatMessage.user("первый")), Collections.<ToolSpec>emptyList());
        client.chat(Collections.singletonList(ChatMessage.user("второй")), Collections.<ToolSpec>emptyList());
    }

    @Test
    public void chatRecordsMessagesPassedOnEachCall() {
        MockLlmClient client = new MockLlmClient(ChatResponse.text("ok"));
        List<ChatMessage> messages = Collections.singletonList(ChatMessage.user("hello"));

        client.chat(messages, Collections.<ToolSpec>emptyList());

        assertEquals(1, client.recordedMessages().size());
        assertSame(messages, client.recordedMessages().get(0));
    }
}
