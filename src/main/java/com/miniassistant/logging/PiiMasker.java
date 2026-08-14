package com.miniassistant.logging;

import java.util.regex.Pattern;

/**
 * Маскирует персональные данные (email-адреса) в тексте перед записью в лог.
 * Нужен как защита на случай, если PII случайно попадёт в лог не напрямую
 * (тело письма и так никогда не логируется), а через текст стороннего
 * исключения - например, ошибка отправки почты может содержать адрес
 * получателя внутри своего сообщения.
 */
public final class PiiMasker {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final String EMAIL_PLACEHOLDER = "[EMAIL]";

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        return EMAIL_PATTERN.matcher(text).replaceAll(EMAIL_PLACEHOLDER);
    }
}
