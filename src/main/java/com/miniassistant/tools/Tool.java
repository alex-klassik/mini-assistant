package com.miniassistant.tools;

/**
 * Контракт инструмента для LLM tool-calling: модель узнаёт о наличии
 * инструмента по {@link #name()}/{@link #description()}/{@link #jsonSchema()},
 * а вызывает его через {@link #execute(String)} с аргументами в виде JSON-строки.
 *
 * <p>Реализации не обязаны сами защищаться от плохих аргументов - если JSON
 * невалиден или внутри возникла ошибка, {@code execute} может бросить
 * исключение. Ловить его и превращать в {@code {"error": "..."}} для модели -
 * ответственность {@code ToolLoop} (M5), а не каждого инструмента по отдельности.
 */
public interface Tool {

    String name();

    String description();

    /** JSON Schema параметров вызова - то, что отдаётся модели вместе с name/description. */
    String jsonSchema();

    String execute(String argsJson);
}
