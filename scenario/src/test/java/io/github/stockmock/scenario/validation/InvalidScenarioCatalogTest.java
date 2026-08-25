package io.github.stockmock.scenario.validation;

import io.github.stockmock.scenario.loader.ScenarioLoader;
import io.github.stockmock.scenario.spec.ScenarioSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TODO(SCENARIO-07): 잘못된 YAML 예시가 어떤 필드를 보고하는지 고정한다.
 *
 * <p>이 파일들은 실행용이 아니라 검증 동작을 보여주는 예시다. 그래서 사용자가 실행하는
 * {@code scenarios/} 카탈로그가 아니라 test resources에 둔다. 카탈로그에 두면
 * {@code ScenarioCatalogTest}가 이들을 유효한 시나리오로 검사해 깨진다.</p>
 *
 * <p>문서의 표와 이 테스트가 어긋나면 둘 중 하나가 낡은 것이다.
 * {@code scenarios/README.md}를 함께 고친다.</p>
 */
class InvalidScenarioCatalogTest {
    private final ScenarioLoader loader = new ScenarioLoader();
    private final ScenarioValidator validator = new ScenarioValidator();

    /** 1.5는 범위 규칙과 합계 규칙에 모두 걸린다. 두 오류가 함께 보고된다. */
    @Test
    void reportsARatioAboveOne() {
        assertThat(fieldsOf("ratio-out-of-range.yml"))
                .containsExactly("execution.fills[0].ratio", "execution.fills");
    }

    @Test
    void reportsRatioAndQuantityUsedTogether() {
        assertThat(fieldsOf("ratio-and-quantity.yml"))
                .containsExactly("execution.fills[0]");
    }

    @Test
    void reportsRatiosSummingAboveOne() {
        assertThat(fieldsOf("ratio-sum-above-one.yml"))
                .containsExactly("execution.fills");
    }

    @Test
    void reportsATimeStringWithoutAUnit() {
        assertThat(fieldsOf("bad-duration.yml"))
                .containsExactly("execution.fills[0].after");
    }

    /** 검증기는 첫 오류에서 멈추지 않는다. 두 제약 오류가 모두 보고되어야 한다. */
    @Test
    void reportsEveryConstraintProblemAtOnce() {
        assertThat(fieldsOf("bad-constraints.yml"))
                .containsExactly("constraints.rate_limit.per_sec", "constraints.token_ttl");
    }

    /** 잘못된 예시라도 YAML 구조 자체는 spec과 맞아야 한다. 의미 오류만 보여주기 위함이다. */
    @Test
    void everyInvalidExampleStillLoadsAsAScenarioSpec() {
        List<String> names = List.of("ratio-out-of-range.yml", "ratio-and-quantity.yml",
                "ratio-sum-above-one.yml", "bad-duration.yml", "bad-constraints.yml");

        assertThat(names).allSatisfy(name -> {
            ScenarioSpec spec = loader.load(resource(name));
            assertThat(spec.scenario()).isNotBlank();
            assertThat(validator.validate(spec))
                    .as("%s는 검증에서 거부되어야 한다", name)
                    .isNotEmpty();
        });
    }

    private List<String> fieldsOf(String name) {
        return validator.validate(loader.load(resource(name))).stream()
                .map(ValidationIssue::field)
                .toList();
    }

    private Path resource(String name) {
        try {
            return Path.of(getClass().getResource("/invalid/" + name).toURI());
        } catch (Exception exception) {
            throw new IllegalStateException("예시 파일을 찾을 수 없습니다: " + name, exception);
        }
    }
}
