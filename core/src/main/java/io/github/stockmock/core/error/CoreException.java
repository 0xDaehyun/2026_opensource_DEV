package io.github.stockmock.core.error;

import java.util.Objects;

/** Adapter가 메시지 문자열 대신 오류 코드로 변환할 수 있게 하는 core 예외다. */
public class CoreException extends RuntimeException {
    private final CoreErrorCode code;

    public CoreException(CoreErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CoreErrorCode code() {
        return code;
    }
}
