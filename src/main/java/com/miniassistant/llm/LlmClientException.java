package com.miniassistant.llm;

/**
 * Ошибка при обращении к LLM по HTTP - сетевой сбой (включая таймаут),
 * не-2xx HTTP-статус, либо не удалось разобрать JSON запроса/ответа.
 * Наследует {@link RuntimeException}, как и весь контракт {@link LlmClient}
 * (см. его Javadoc) - вызывающая сторона ({@code ToolLoop}/{@code AgentService},
 * M5/M8) уже умеет гасить {@code RuntimeException} от LLM и подставлять
 * фолбэк-ответ.
 */
public class LlmClientException extends RuntimeException {

    public LlmClientException(String message) {
        super(message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
