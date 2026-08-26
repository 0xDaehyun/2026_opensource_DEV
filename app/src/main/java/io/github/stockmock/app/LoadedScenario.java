package io.github.stockmock.app;

import io.github.stockmock.scenario.ScenarioSettings;
import io.github.stockmock.scenario.spec.ScenarioSpec;

/** 시작 시 검증을 통과해 실행에 사용할 수 있는 YAML 시나리오다. */
public record LoadedScenario(String source, ScenarioSpec spec, ScenarioSettings settings) {
    public String name() {
        return spec.scenario();
    }
}
