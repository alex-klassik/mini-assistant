package com.miniassistant.config;

/**
 * Настройки LLM-клиента. В YAML секретов нет - только {@code apiKeyEnv},
 * имя переменной окружения, из которой в рантайме резолвится сам ключ
 * (см. {@link #resolveApiKey(EnvProvider)}).
 */
public final class LlmConfig {

    private String endpoint;
    private String model;
    private String apiKeyEnv;
    private int timeoutMs;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public void setApiKeyEnv(String apiKeyEnv) {
        this.apiKeyEnv = apiKeyEnv;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Значение самого ключа - из переменной окружения, чьё имя задано в
     * {@link #getApiKeyEnv()}.
     *
     * @throws IllegalStateException переменная не задана в окружении
     */
    public String resolveApiKey(EnvProvider env) {
        String value = env.getenv(apiKeyEnv);
        if (value == null) {
            throw new IllegalStateException(
                    "environment variable '" + apiKeyEnv + "' (llm.apiKeyEnv) is not set");
        }
        return value;
    }
}
