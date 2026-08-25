package io.github.stockmock.scenario;

import io.github.stockmock.scenario.constraint.Operation;
import io.github.stockmock.scenario.execution.ScenarioFillPlanProvider;
import io.github.stockmock.scenario.fault.FaultTiming;
import io.github.stockmock.scenario.fault.ResponseDelayRule;
import io.github.stockmock.scenario.spec.ScenarioSpec;
import io.github.stockmock.scenario.time.DurationParser;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 검증을 통과한 {@link ScenarioSpec}을 각 정책이 바로 쓸 수 있는 타입으로 바꾼다.
 *
 * <p>YAML은 시간을 {@code "10s"}, 열거값을 {@code "PLACE_ORDER"} 같은 문자열로 담는다.
 * 정책들은 {@link Duration}과 {@link Operation}을 받는다. 이 변환을 조립 계층이 하면
 * {@code Operation.valueOf}와 {@code DurationParser}가 app으로 새어나가는데,
 * docs/contracts.md는 "app은 모듈을 조립하고 업무 규칙을 갖지 않는다"고 정한다.
 * 그래서 변환을 시나리오 모듈에 둔다.</p>
 *
 * <p>{@link #from(ScenarioSpec)}은 검증을 대신하지 않는다. {@code ScenarioValidator}로 먼저
 * 검사하고, 오류가 없을 때만 호출한다. 검증을 건너뛴 입력은 여기서 예외로 드러난다.</p>
 */
public final class ScenarioSettings {
    private final ScenarioFillPlanProvider fillPlanProvider;
    private final long initialCash;
    private final Duration tokenTtl;
    private final Integer ratePerSecond;
    private final ResponseDelayRule responseDelay;

    private ScenarioSettings(ScenarioFillPlanProvider fillPlanProvider, long initialCash,
                             Duration tokenTtl, Integer ratePerSecond, ResponseDelayRule responseDelay) {
        this.fillPlanProvider = fillPlanProvider;
        this.initialCash = initialCash;
        this.tokenTtl = tokenTtl;
        this.ratePerSecond = ratePerSecond;
        this.responseDelay = responseDelay;
    }

    public static ScenarioSettings from(ScenarioSpec spec) {
        return from(spec, new DurationParser());
    }

    public static ScenarioSettings from(ScenarioSpec spec, DurationParser durationParser) {
        Objects.requireNonNull(durationParser, "durationParser");
        if (spec == null) {
            throw new IllegalArgumentException("시나리오 본문이 필요합니다");
        }
        if (spec.account() == null || spec.account().cash() == null) {
            throw new IllegalArgumentException("초기 현금이 필요합니다");
        }

        return new ScenarioSettings(
                new ScenarioFillPlanProvider(spec.execution(), durationParser),
                spec.account().cash(),
                tokenTtlOf(spec, durationParser),
                ratePerSecondOf(spec),
                responseDelayOf(spec, durationParser));
    }

    /** core {@code SimulationEngine}에 주입할 체결 계획 생성기다. */
    public ScenarioFillPlanProvider fillPlanProvider() {
        return fillPlanProvider;
    }

    public long initialCash() {
        return initialCash;
    }

    /** {@code TokenExpiryPolicy}에 넘길 TTL이다. 시나리오가 지정하지 않으면 비어 있다. */
    public Optional<Duration> tokenTtl() {
        return Optional.ofNullable(tokenTtl);
    }

    /** {@code RateLimitPolicy}에 넘길 초당 허용 호출 수다. 지정하지 않으면 비어 있다. */
    public OptionalInt ratePerSecond() {
        return ratePerSecond == null ? OptionalInt.empty() : OptionalInt.of(ratePerSecond);
    }

    /** {@code ResponseDelayPolicy}에 넘길 규칙이다. faults가 없으면 비어 있다. */
    public Optional<ResponseDelayRule> responseDelay() {
        return Optional.ofNullable(responseDelay);
    }

    private static Duration tokenTtlOf(ScenarioSpec spec, DurationParser parser) {
        if (spec.constraints() == null || spec.constraints().tokenTtl() == null) {
            return null;
        }
        return parser.parse(spec.constraints().tokenTtl());
    }

    private static Integer ratePerSecondOf(ScenarioSpec spec) {
        if (spec.constraints() == null || spec.constraints().rateLimit() == null) {
            return null;
        }
        Integer perSec = spec.constraints().rateLimit().perSec();
        if (perSec != null && perSec < 1) {
            throw new IllegalArgumentException("초당 허용 호출 수는 1 이상이어야 합니다: " + perSec);
        }
        return perSec;
    }

    private static ResponseDelayRule responseDelayOf(ScenarioSpec spec, DurationParser parser) {
        if (spec.faults() == null || spec.faults().response() == null) {
            return null;
        }
        ScenarioSpec.ResponseFaultSpec response = spec.faults().response();
        return new ResponseDelayRule(
                enumOf(response.on(), Operation.class, "faults.response.on"),
                enumOf(response.timing(), FaultTiming.class, "faults.response.timing"),
                parser.parse(response.delay()));
    }

    private static <E extends Enum<E>> E enumOf(String value, Class<E> type, String path) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " 값이 필요합니다");
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(path + "의 알 수 없는 값입니다: " + value, unknown);
        }
    }
}
