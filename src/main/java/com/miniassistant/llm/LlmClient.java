package com.miniassistant.llm;

import java.util.List;

/**
 * Контракт обращения к LLM: одна история диалога плюс список доступных
 * инструментов на входе, один ответ модели на выходе. Не описывает transport
 * (HTTP, повторы, таймауты) - это дело {@code HttpLlmClient} (M12);
 * {@code ToolLoop} (M5) работает только через этот интерфейс.
 */
public interface LlmClient {

    ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools);
}
