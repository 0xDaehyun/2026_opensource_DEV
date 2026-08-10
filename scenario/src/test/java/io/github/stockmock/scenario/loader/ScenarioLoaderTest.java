package io.github.stockmock.scenario.loader;

import io.github.stockmock.scenario.spec.ScenarioSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioLoaderTest {
    private final ScenarioLoader loader = new ScenarioLoader();

    @Test
    void loadsTheStarterScenario() {
        ScenarioSpec spec = loader.load(resource("starter-partial-fill.yml"));

        assertThat(spec.scenario()).isEqualTo("starter_partial_fill");
        assertThat(spec.account().cash()).isEqualTo(10_000_000L);
        assertThat(spec.execution().fills()).singleElement()
                .satisfies(fill -> {
                    assertThat(fill.after()).isEqualTo("1s");
                    assertThat(fill.ratio()).isEqualTo(0.3);
                });
    }

    @Test
    void rejectsUnknownFields() {
        assertThatThrownBy(() -> loader.load(resource("unknown-field.yml")))
                .isInstanceOf(ScenarioLoadException.class)
                .hasMessageContaining("시나리오를 읽을 수 없습니다");
    }

    private Path resource(String name) {
        try {
            return Path.of(getClass().getResource("/" + name).toURI());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
