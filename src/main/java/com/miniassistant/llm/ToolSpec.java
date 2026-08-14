package com.miniassistant.llm;

/**
 * Описание одного инструмента для модели: то, что уходит в поле {@code tools}
 * запроса Chat Completions - имя, описание и JSON Schema параметров как
 * есть (в виде строки), без промежуточного разбора.
 */
public final class ToolSpec {

    private final String name;
    private final String description;
    private final String parametersJsonSchema;

    public ToolSpec(String name, String description, String parametersJsonSchema) {
        this.name = name;
        this.description = description;
        this.parametersJsonSchema = parametersJsonSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getParametersJsonSchema() {
        return parametersJsonSchema;
    }
}
