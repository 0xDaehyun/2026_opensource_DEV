package io.github.stockmock.app;

import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.engine.SimulationEngine;
import io.github.stockmock.adapter.ls.LsRequestPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.time.Instant;

@SpringBootApplication(scanBasePackages = "io.github.stockmock")
public class MockServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockServerApplication.class, args);
    }

    @Bean
    VirtualClock virtualClock(
            @Value("${mock.clock.origin:2026-01-02T00:00:00Z}") Instant origin,
            @Value("${mock.clock.mode:ATTACHED}") VirtualClock.Mode mode) {
        return new VirtualClock(mode, origin);
    }

    @Bean(destroyMethod = "close")
    SimulationEngine simulationEngine(
            VirtualClock virtualClock,
            LoadedScenario scenario) {
        return new SimulationEngine(virtualClock, scenario.settings().initialCash(),
                scenario.settings().fillPlanProvider());
    }

    @Bean
    LsRequestPolicy requestPolicy(
            LoadedScenario scenario,
            VirtualClock virtualClock,
            @Value("${mock.token.ttl:PT24H}") Duration defaultTokenTtl) {
        return new ScenarioRequestPolicy(scenario.settings(), virtualClock, defaultTokenTtl);
    }
}
