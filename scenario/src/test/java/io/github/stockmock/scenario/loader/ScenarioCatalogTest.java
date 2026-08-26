package io.github.stockmock.scenario.loader;

import io.github.stockmock.scenario.spec.ScenarioSpec;
import io.github.stockmock.scenario.validation.ScenarioValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TODO(SCENARIO-01): 사용자가 실행하는 `scenarios/` 카탈로그가 {@link ScenarioSpec}과 어긋나지
 * 않는지 확인한다.
 *
 * <p>기존 {@code ScenarioLoaderTest}는 test resources의 fixture만 읽어서, 카탈로그 YAML이
 * 몰래 어긋나도 아무 테스트가 깨지지 않았다. strict 로더가 unknown field를 거부하므로
 * 카탈로그를 전부 읽어보는 것만으로 필드 일치가 검증된다.</p>
 */
class ScenarioCatalogTest {
    /** Gradle 테스트 작업 디렉터리는 모듈 루트인 {@code scenario/}다. */
    private static final Path CATALOG = Path.of("..", "scenarios");

    private final ScenarioLoader loader = new ScenarioLoader();
    private final ScenarioValidator validator = new ScenarioValidator();

    static Stream<Path> catalogFiles() throws IOException {
        try (var paths = Files.walk(CATALOG)) {
            return paths.filter(path -> path.toString().endsWith(".yml")).toList().stream();
        }
    }

    @Test
    void theCatalogIsNotEmpty() throws IOException {
        assertThat(catalogFiles()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("catalogFiles")
    void everyCatalogScenarioMatchesTheSpec(Path path) {
        ScenarioSpec spec = loader.load(path);

        assertThat(validator.validate(spec))
                .as("%s 시나리오 의미 검증", path)
                .isEmpty();
        assertThat(spec.scenario()).as("scenario 이름").isNotBlank();
        assertThat(spec.account()).as("account").isNotNull();
        assertThat(spec.account().cash()).as("account.cash").isNotNull().isPositive();
        assertThat(spec.execution()).as("execution").isNotNull();
        assertThat(spec.execution().fills()).as("execution.fills").isNotEmpty();
        assertThat(spec.seed()).as("seed — 결정론 재현에 필요하다").isNotNull();
    }

    /**
     * 카탈로그는 순수 데이터여야 한다. 실행 훅, 클래스 이름, URL이 들어오면 시나리오 파일이
     * 곧 임의 코드 실행 통로가 된다. strict 로더가 unknown field를 거부하므로 구조적으로
     * 막히지만, 회귀를 막기 위해 원문에서도 확인한다.
     */
    @ParameterizedTest
    @MethodSource("catalogFiles")
    void noCatalogScenarioCarriesExecutionCapability(Path path) throws IOException {
        String text = Files.readString(path).toLowerCase();

        // "exec"가 아니라 "exec:"로 검사한다. 정당한 execution: 키와 겹치기 때문이다.
        assertThat(text)
                .doesNotContain("class:")
                .doesNotContain("script:")
                .doesNotContain("exec:")
                .doesNotContain("command:")
                .doesNotContain("run:")
                .doesNotContain("url:")
                .doesNotContain("http://")
                .doesNotContain("https://");
    }

    /** 카탈로그 파일 하나에 실제로 execution 설정이 있는지 표본으로 확인한다. */
    @Test
    void readsThePartialFillCatalogScenario() {
        ScenarioSpec spec = loader.load(CATALOG.resolve("basic").resolve("partial-fill.yml"));

        assertThat(spec.scenario()).isEqualTo("partial_fill");
        assertThat(spec.execution().fills()).singleElement().satisfies(fill -> {
            assertThat(fill.after()).isEqualTo("1s");
            assertThat(fill.ratio()).isEqualTo(0.3);
            assertThat(fill.quantity()).isNull();
        });
    }

    /** faults 블록이 있는 카탈로그도 spec과 맞는지 확인한다. */
    @Test
    void readsTheResponseDelayCatalogScenario() {
        ScenarioSpec spec = loader.load(CATALOG.resolve("hazards").resolve("response-delay-after-commit.yml"));

        assertThat(spec.faults()).isNotNull();
        assertThat(spec.faults().response()).satisfies(response -> {
            assertThat(response.on()).isEqualTo("PLACE_ORDER");
            assertThat(response.timing()).isEqualTo("AFTER_COMMIT");
            assertThat(response.delay()).isEqualTo("3s");
        });
    }

    /** APP-01에서 실제 요청에 연결한 rate limit과 token TTL 예제를 카탈로그에 유지한다. */
    @Test
    void catalogsRateLimitAndTokenExpiryConstraints() throws IOException {
        List<Path> withConstraints = catalogFiles()
                .filter(path -> {
                    try {
                        return Files.readString(path).contains("constraints:");
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();

        assertThat(withConstraints)
                .extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrder("rate-limit.yml", "token-expiry.yml");
    }
}
