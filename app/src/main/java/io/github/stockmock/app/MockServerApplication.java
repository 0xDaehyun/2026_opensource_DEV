package io.github.stockmock.app;

import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.engine.SimulationEngine;
import io.github.stockmock.core.fill.FillPlan;
import io.github.stockmock.core.fill.FillPlanProvider;
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
            @Value("${mock.clock.origin:2026-01-02T00:00:00Z}") Instant origin) {
        return VirtualClock.attached(origin);
    }

    @Bean(destroyMethod = "close")
    SimulationEngine simulationEngine(
            VirtualClock virtualClock,
            @Value("${mock.account.initial-cash:10000000}") long initialCash,
            FillPlanProvider fillPlanProvider) {
        return new SimulationEngine(virtualClock, initialCash, fillPlanProvider);
    }

    @Bean
    FillPlanProvider fillPlanProvider(
            @Value("${mock.fill.ratio:0.3}") double fillRatio,
            @Value("${mock.fill.delay:PT5S}") Duration fillDelay) {
        return orderQuantity -> FillPlan.partial(orderQuantity, fillRatio, fillDelay);
    }
}
