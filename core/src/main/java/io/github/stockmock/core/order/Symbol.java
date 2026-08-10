package io.github.stockmock.core.order;

public record Symbol(String value) {
    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("종목 코드는 비어 있을 수 없습니다");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
