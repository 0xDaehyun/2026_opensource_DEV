package io.github.stockmock.app;

import io.github.stockmock.scenario.ScenarioSettings;
import io.github.stockmock.scenario.loader.ScenarioLoader;
import io.github.stockmock.scenario.spec.ScenarioSpec;
import io.github.stockmock.scenario.validation.ScenarioValidator;
import io.github.stockmock.scenario.validation.ValidationIssue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/** YAML을 한 번만 읽고 검증한 뒤 나머지 Bean이 같은 실행 설정을 공유하게 한다. */
@Configuration
public class ScenarioConfiguration {
    @Bean
    LoadedScenario loadedScenario(
            @Value("${mock.scenario:classpath:scenarios/basic/partial-fill.yml}") String location,
            ResourceLoader resourceLoader) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("mock.scenario에 YAML 파일 경로가 필요합니다");
        }
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            return load(resourceLoader.getResource(location));
        }
        return load(Path.of(location));
    }

    static LoadedScenario load(Path path) {
        ScenarioSpec spec = new ScenarioLoader().load(path);
        return validate(path.toString(), spec);
    }

    static LoadedScenario load(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            ScenarioSpec spec = new ScenarioLoader().load(input, resource.getDescription());
            return validate(resource.getDescription(), spec);
        } catch (IOException exception) {
            throw new IllegalStateException("시나리오를 열 수 없습니다: " + resource.getDescription(), exception);
        }
    }

    private static LoadedScenario validate(String source, ScenarioSpec spec) {
        List<ValidationIssue> issues = new ScenarioValidator().validate(spec);
        if (!issues.isEmpty()) {
            StringBuilder message = new StringBuilder("시나리오 검증 실패: ").append(source);
            for (ValidationIssue issue : issues) {
                message.append(System.lineSeparator())
                        .append(" - ").append(issue.field()).append(": ").append(issue.message());
            }
            throw new IllegalStateException(message.toString());
        }
        return new LoadedScenario(source, spec, ScenarioSettings.from(spec));
    }
}
