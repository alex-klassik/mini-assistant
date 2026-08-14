package com.miniassistant.llm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Тестовая реализация {@link LlmClient} со скриптованными ответами: каждый
 * вызов {@link #chat} отдаёт следующий {@link ChatResponse} из списка,
 * заданного в конструкторе, в порядке вызовов. Если ответы кончились - бросает
 * {@link IllegalStateException} вместо того чтобы молча вернуть {@code null}:
 * тест, который вызвал {@code chat} больше раз, чем ожидал, должен упасть
 * явно и сразу, а не потом на NPE где-то глубже.
 */
public class MockLlmClient implements LlmClient {

    private final List<ChatResponse> scriptedResponses;
    private final List<List<ChatMessage>> recordedMessages = new ArrayList<>();
    private int callCount = 0;

    public MockLlmClient(ChatResponse... scriptedResponses) {
        this.scriptedResponses = new ArrayList<>(Arrays.asList(scriptedResponses));
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) {
        if (callCount >= scriptedResponses.size()) {
            throw new IllegalStateException(
                    "MockLlmClient: no scripted response left for call #" + (callCount + 1));
        }
        recordedMessages.add(messages);
        return scriptedResponses.get(callCount++);
    }

    /** История сообщений, переданных в каждый вызов {@link #chat}, по порядку - для проверок в тестах. */
    public List<List<ChatMessage>> recordedMessages() {
        return Collections.unmodifiableList(recordedMessages);
    }
}
