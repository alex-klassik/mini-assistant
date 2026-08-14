package com.miniassistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Читает YAML-файл конфигурации в {@link AppConfig}. Секретов не резолвит - см. {@link LlmConfig#resolveApiKey}. */
public final class ConfigLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public AppConfig load(Path path) {
        try {
            return yamlMapper.readValue(path.toFile(), AppConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load config from " + path, e);
        }
    }
}
