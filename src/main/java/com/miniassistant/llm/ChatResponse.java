package com.miniassistant.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Результат одного вызова {@link LlmClient#chat}: либо финальный текстовый
 * ответ ({@link #getContent()}, {@link #getToolCalls()} пуст), либо запрос на
 * вызов инструментов ({@link #getToolCalls()} непуст, {@link #getContent()}
 * {@code null}) - см. {@link #hasToolCalls()}.
 */
public final class ChatResponse {

    private final String content;
    private final List<ToolCall> toolCalls;

    private ChatResponse(String content, List<ToolCall> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls;
    }

    public static ChatResponse text(String content) {
        return new ChatResponse(content, Collections.<ToolCall>emptyList());
    }

    public static ChatResponse toolCalls(List<ToolCall> toolCalls) {
        return new ChatResponse(null, new ArrayList<ToolCall>(toolCalls));
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
