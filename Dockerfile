# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY core/build.gradle.kts ./core/build.gradle.kts
COPY adapter-ls/build.gradle.kts ./adapter-ls/build.gradle.kts
COPY scenario/build.gradle.kts ./scenario/build.gradle.kts
COPY app/build.gradle.kts ./app/build.gradle.kts

RUN chmod +x gradlew \
    && ./gradlew :app:dependencies --no-daemon

COPY core/src ./core/src
COPY adapter-ls/src ./adapter-ls/src
COPY scenario/src ./scenario/src
COPY app/src ./app/src
COPY scenarios ./scenarios

RUN ./gradlew :app:bootJar --no-daemon \
    && cp app/build/libs/app-*.jar /workspace/stock-mock-server.jar

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system stockmock \
    && useradd --system --gid stockmock --home-dir /opt/stock-mock-server stockmock

WORKDIR /opt/stock-mock-server

COPY --from=builder --chown=stockmock:stockmock \
    /workspace/stock-mock-server.jar ./stock-mock-server.jar

USER stockmock

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "stock-mock-server.jar"]
