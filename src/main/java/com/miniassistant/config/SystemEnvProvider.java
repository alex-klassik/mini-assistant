package com.miniassistant.config;

/** Прод-реализация {@link EnvProvider} - читает настоящее окружение процесса. */
public final class SystemEnvProvider implements EnvProvider {

    @Override
    public String getenv(String name) {
        return System.getenv(name);
    }
}
