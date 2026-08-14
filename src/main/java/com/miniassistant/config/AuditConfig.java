package com.miniassistant.config;

/**
 * Настройки журнала аудита. Как и {@code LlmConfig.apiKeyEnv}, в YAML лежит
 * только имя переменной окружения ({@code hmacKeyEnv}), а не сам HMAC-ключ.
 */
public final class AuditConfig {

    private String hmacKeyEnv;

    public String getHmacKeyEnv() {
        return hmacKeyEnv;
    }

    public void setHmacKeyEnv(String hmacKeyEnv) {
        this.hmacKeyEnv = hmacKeyEnv;
    }

    /**
     * Значение самого HMAC-ключа - из переменной окружения, чьё имя задано в
     * {@link #getHmacKeyEnv()}.
     *
     * @throws IllegalStateException переменная не задана в окружении
     */
    public String resolveHmacKey(EnvProvider env) {
        String value = env.getenv(hmacKeyEnv);
        if (value == null) {
            throw new IllegalStateException(
                    "environment variable '" + hmacKeyEnv + "' (audit.hmacKeyEnv) is not set");
        }
        return value;
    }
}
