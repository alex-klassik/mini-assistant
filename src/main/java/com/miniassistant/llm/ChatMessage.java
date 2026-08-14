package com.miniassistant.llm;

import java.util.Collections;
import java.util.List;

/**
 * Одно сообщение в истории диалога с моделью (формат Chat Completions).
 * Роль определяет, какие поля заполнены:
 * <ul>
 *   <li>{@code SYSTEM}/{@code USER} - только {@link #getContent()};</li>
 *   <li>{@code ASSISTANT} - либо {@link #getContent()} (финальный ответ), либо
 *       {@link #getToolCalls()} (модель попросила вызвать инструменты),
 *       {@code content} тогда {@code null};</li>
 *   <li>{@code TOOL} - {@link #getToolCallId()} (на какой вызов отвечаем) и
 *       {@link #getContent()} (результат выполнения инструмента).</li>
 * </ul>
 */
public final class ChatMessage {

    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    private final Role role;
    private final String content;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;

    private ChatMessage(Role role, String content, List<ToolCall> toolCalls, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, Collections.<ToolCall>emptyList(), null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, Collections.<ToolCall>emptyList(), null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, Collections.<ToolCall>emptyList(), null);
    }

    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, null, toolCalls, null);
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage(Role.TOOL, content, Collections.<ToolCall>emptyList(), toolCallId);
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }
}
