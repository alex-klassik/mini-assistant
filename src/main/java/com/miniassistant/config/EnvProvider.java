package com.miniassistant.config;

/**
 * Абстракция над чтением переменных окружения. Нужна, чтобы тесты могли
 * подставить фейковые значения вместо {@link System#getenv(String)} и не
 * трогать реальное окружение процесса.
 */
public interface EnvProvider {

    /** {@code null}, если переменная с таким именем не задана. */
    String getenv(String name);
}
