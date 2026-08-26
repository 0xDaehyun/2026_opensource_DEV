package io.github.stockmock.scenario.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.stockmock.scenario.spec.ScenarioSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/** YAML 파일을 strict 모드로 읽는 시작 구현이다. */
public final class ScenarioLoader {
    private final ObjectMapper objectMapper;

    public ScenarioLoader() {
        this(new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    ScenarioLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ScenarioSpec load(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            return objectMapper.readValue(path.toFile(), ScenarioSpec.class);
        } catch (IOException exception) {
            throw new ScenarioLoadException("시나리오를 읽을 수 없습니다: " + path, exception);
        }
    }

    public ScenarioSpec load(InputStream input, String source) {
        Objects.requireNonNull(input, "input");
        try {
            return objectMapper.readValue(input, ScenarioSpec.class);
        } catch (IOException exception) {
            throw new ScenarioLoadException("시나리오를 읽을 수 없습니다: " + source, exception);
        }
    }
}
