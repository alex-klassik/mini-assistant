package com.miniassistant.config;

/** Настройки дискового хранилища ({@code SeenStore}, {@code ReminderStore} и т.п.). */
public final class StoreConfig {

    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
