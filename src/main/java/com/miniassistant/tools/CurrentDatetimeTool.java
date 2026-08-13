package com.miniassistant.tools;

import java.time.Clock;
import java.time.Instant;

/**
 * Возвращает текущее время через инжектируемый {@link Clock} - это то, что
 * делает {@link #execute(String)} детерминированным в тестах ({@link Clock#fixed})
 * и живым при работе (см. wiring в {@code Main}, {@link Clock#systemUTC()}).
 */
public final class CurrentDatetimeTool implements Tool {

    private final Clock clock;

    public CurrentDatetimeTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return "current_datetime";
    }

    @Override
    public String description() {
        return "Возвращает текущую дату и время в формате ISO-8601 (UTC).";
    }

    @Override
    public String jsonSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String execute(String argsJson) {
        Instant now = Instant.now(clock);
        return "{\"iso\":\"" + now + "\"}";
    }
}
