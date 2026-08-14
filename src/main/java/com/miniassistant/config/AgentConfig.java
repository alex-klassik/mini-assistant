package com.miniassistant.config;

/** Настройки {@code ToolLoop}: сколько шагов дать модели до обрыва. */
public final class AgentConfig {

    private int maxSteps;

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }
}
