package io.github.stockmock.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioConfigurationTest {
    @Test
    void loadsAValidatedCatalogScenario() {
        LoadedScenario scenario = ScenarioConfiguration.load(
                Path.of("../scenarios/basic/partial-fill.yml"));

        assertThat(scenario.name()).isEqualTo("partial_fill");
        assertThat(scenario.settings().initialCash()).isEqualTo(10_000_000);
        assertThat(scenario.settings().fillPlanProvider().create(100).steps())
                .hasSize(1)
                .first().satisfies(step -> assertThat(step.quantity()).isEqualTo(30));
    }

    @Test
    void rejectsInvalidYamlWithEveryFieldPathInTheStartupMessage() {
        assertThatThrownBy(() -> ScenarioConfiguration.load(
                Path.of("../scenario/src/test/resources/invalid/bad-constraints.yml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("시나리오 검증 실패")
                .hasMessageContaining("constraints.rate_limit.per_sec")
                .hasMessageContaining("constraints.token_ttl");
    }
}
