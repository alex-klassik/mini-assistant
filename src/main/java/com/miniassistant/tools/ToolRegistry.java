package com.miniassistant.tools;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Доступные агенту инструменты, проиндексированные по имени. Не знает ничего
 * про формат запроса к LLM (тот живёт в {@code llm}) - это чисто справочник
 * {@link Tool}'ов для {@code ToolLoop} (M5), который сам решает, как
 * представить их модели.
 */
public final class ToolRegistry {

    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
    }

    /** {@code null}, если инструмента с таким именем нет - например, модель его придумала. */
    public Tool find(String name) {
        return toolsByName.get(name);
    }

    public Collection<Tool> all() {
        return toolsByName.values();
    }
}
