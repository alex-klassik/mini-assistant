package com.miniassistant.logging;

/**
 * Константы ключей структурных логов ({@code event=...}), чтобы одинаковые
 * события всегда логировались под одним и тем же именем, а не разбредались
 * по коду строковыми литералами.
 */
public final class Events {

    public static final String LLM_FAILED = "llm_failed";
    public static final String MAIL_SEND_FAILED = "mail_send_failed";

    private Events() {
    }
}
