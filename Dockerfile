FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY src src

RUN chmod +x gradlew \
    && ./gradlew bootJar --no-daemon \
    && JAR_FILE="$(find build/libs -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && cp "$JAR_FILE" app.jar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S owlnest \
    && adduser -S owlnest -G owlnest

COPY --from=builder --chown=owlnest:owlnest /workspace/app.jar app.jar

USER owlnest

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
