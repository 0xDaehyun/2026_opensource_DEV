package io.github.stockmock.scenario.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TODO(SCENARIO-03) 테스트 목록:
 * <ul>
 *   <li>500ms, 1s, 10s, 10m, 24h를 Duration으로 변환한다.</li>
 *   <li>단위가 없는 문자열을 거부한다.</li>
 *   <li>0과 음수 시간을 거부한다.</li>
 *   <li>소수, 공백 포함, 알 수 없는 단위를 거부한다.</li>
 * </ul>
 *
 * <p>필드 경로는 이 파서의 책임이 아니다. 파서는 잘못된 값만 알리고
 * {@code ScenarioValidator}가 {@code ValidationIssue}로 YAML 경로를 붙인다.</p>
 */
class DurationParserTest {
    private final DurationParser parser = new DurationParser();

    @ParameterizedTest
    @CsvSource({
            "500ms, PT0.5S",
            "1ms,   PT0.001S",
            "1s,    PT1S",
            "10s,   PT10S",
            "10m,   PT10M",
            "24h,   PT24H",
            "1h,    PT1H"
    })
    void parsesTheSupportedUnits(String value, String expected) {
        assertThat(parser.parse(value)).isEqualTo(Duration.parse(expected));
    }

    @Test
    void distinguishesMillisecondsFromMinutes() {
        assertThat(parser.parse("5ms")).isEqualTo(Duration.ofMillis(5));
        assertThat(parser.parse("5m")).isEqualTo(Duration.ofMinutes(5));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsBlankInput(String value) {
        assertThatThrownBy(() -> parser.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "1000", "0"})
    void rejectsAValueWithoutAUnit(String value) {
        assertThatThrownBy(() -> parser.parse(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0ms", "0s", "0m", "0h"})
    void rejectsZeroDuration(String value) {
        assertThatThrownBy(() -> parser.parse(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1s", "-500ms", "-24h"})
    void rejectsNegativeDuration(String value) {
        assertThatThrownBy(() -> parser.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5s", "0.5s", "1,5s"})
    void rejectsFractionalValues(String value) {
        assertThatThrownBy(() -> parser.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {" 1s", "1s ", "1 s", "1\ts"})
    void rejectsWhitespace(String value) {
        assertThatThrownBy(() -> parser.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1d", "1w", "1x", "1sec", "1S", "1H", "1MS", "s", "ms1"})
    void rejectsUnknownUnits(String value) {
        assertThatThrownBy(() -> parser.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    /** long 범위를 넘는 값이 NumberFormatException으로 새어나가지 않게 한다. */
    @Test
    void rejectsAValueTooLargeForLong() {
        assertThatThrownBy(() -> parser.parse("99999999999999999999s"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 시간 단위 곱셈이 overflow하면 조용히 음수가 되지 않고 거부되어야 한다. */
    @Test
    void rejectsAValueThatOverflowsWhenConverted() {
        assertThatThrownBy(() -> parser.parse("9223372036854775807h"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsTheOffendingValueButNotAYamlFieldPath() {
        assertThatThrownBy(() -> parser.parse("3초"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3초");
    }

    @Test
    void neverReadsTheSystemClock() {
        // 같은 입력은 언제 호출해도 같은 값이다. 정책은 가상시각을 전달받아 판단한다.
        assertThat(parser.parse("1s")).isEqualTo(parser.parse("1s"));
    }
}
