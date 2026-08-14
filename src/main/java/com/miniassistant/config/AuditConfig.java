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
}
