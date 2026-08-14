package com.miniassistant.config;

/** Настройки почтового канала (Outlook-профиль, папка, частота опроса). */
public final class MailConfig {

    private int pollSeconds;
    private String profile;
    private String folder;

    public int getPollSeconds() {
        return pollSeconds;
    }

    public void setPollSeconds(int pollSeconds) {
        this.pollSeconds = pollSeconds;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }
}
