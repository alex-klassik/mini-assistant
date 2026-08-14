package com.miniassistant.llm;

/**
 * Один вызов инструмента, запрошенный моделью в ответе: имя инструмента и
 * аргументы в виде JSON-строки (как их прислала модель, без парсинга здесь -
 * разбор и валидация аргументов - забота {@code ToolLoop}, M5).
 */
public final class ToolCall {

    private final String id;
    private final String name;
    private final String argumentsJson;

    public ToolCall(String id, String name, String argumentsJson) {
        this.id = id;
        this.name = name;
        this.argumentsJson = argumentsJson;
    }

    /** Идентификатор вызова из ответа модели - на него ссылается ответное {@code role=tool} сообщение. */
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }
}
