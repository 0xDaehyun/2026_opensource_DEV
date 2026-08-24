package io.github.stockmock.scenario.time;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 시나리오의 짧은 시간 문자열을 {@link Duration}으로 변환한다.
 *
 * <h2>지원 단위</h2>
 * <ul>
 *   <li>{@code 500ms} → 500 milliseconds</li>
 *   <li>{@code 1s} → 1 second</li>
 *   <li>{@code 10m} → 10 minutes</li>
 *   <li>{@code 24h} → 24 hours</li>
 * </ul>
 *
 * <h2>오류</h2>
 * <ul>
 *   <li>null, 빈 문자열, 단위 없는 값은 {@link IllegalArgumentException}</li>
 *   <li>0과 음수 시간은 {@link IllegalArgumentException}</li>
 *   <li>소수, 공백 포함, 알 수 없는 단위는 {@link IllegalArgumentException}</li>
 * </ul>
 *
 * <p>오류 메시지에는 잘못된 값만 담고 YAML 필드 경로는 담지 않는다. 파서는 자기가 어느 필드에서
 * 왔는지 모르기 때문이다. 경로는 {@code ScenarioValidator}가 {@code ValidationIssue}로 붙인다.</p>
 *
 * <p>이 클래스는 현재 시각을 조회하거나 대기하지 않는다.</p>
 */
public final class DurationParser {
    /** 앞뒤 공백, 부호, 소수점을 모두 거부하기 위해 양끝을 고정한다. */
    private static final Pattern SHORT_FORM = Pattern.compile("^(\\d+)(ms|s|m|h)$");

    public Duration parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("시간 문자열이 필요합니다");
        }

        Matcher matcher = SHORT_FORM.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "시간 형식이 올바르지 않습니다: \"" + value + "\". 예: 500ms, 1s, 10m, 24h");
        }

        long amount = parseAmount(value, matcher.group(1));
        if (amount == 0) {
            throw new IllegalArgumentException("시간은 0보다 커야 합니다: \"" + value + "\"");
        }
        return toDuration(value, amount, matcher.group(2));
    }

    private long parseAmount(String value, String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException overflow) {
            throw new IllegalArgumentException("시간 값이 너무 큽니다: \"" + value + "\"", overflow);
        }
    }

    /** 단위 곱셈이 overflow하면 조용히 음수가 되므로 {@link ArithmeticException}을 거부로 바꾼다. */
    private Duration toDuration(String value, long amount, String unit) {
        try {
            return switch (unit) {
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> throw new IllegalArgumentException("알 수 없는 시간 단위입니다: \"" + value + "\"");
            };
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("시간 값이 너무 큽니다: \"" + value + "\"", overflow);
        }
    }
}
