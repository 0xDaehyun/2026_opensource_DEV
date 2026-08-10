package io.github.stockmock.scenario.validation;

import io.github.stockmock.scenario.spec.ScenarioSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioValidatorTest {
    private final ScenarioValidator validator = new ScenarioValidator();

    @Test
    void reportsMissingNameAndNegativeCash() {
        ScenarioSpec spec = new ScenarioSpec(" ", new ScenarioSpec.AccountSpec(-1L),
                null, null, null, 42L);

        assertThat(validator.validate(spec))
                .extracting(ValidationIssue::field)
                .containsExactly("scenario", "account.cash");
    }

    // TODO(SCENARIO-02): invalid ratio, simultaneous ratio/quantity, negative delay tests를 하나씩 추가한다.
}
