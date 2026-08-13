FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

ENV GRADLE_USER_HOME=/workspace/.gradle

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew \
    && ./gradlew clean bootJar --no-daemon \
    && rm -rf "$GRADLE_USER_HOME/caches" "$GRADLE_USER_HOME/daemon"

FROM eclipse-temurin:21-jre-alpine
LABEL authors="juhyuk"

WORKDIR /app

RUN addgroup -S -g 10001 movi \
    && adduser -S -D -H -u 10001 -G movi movi

COPY --from=build --chown=movi:movi /workspace/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q -O - "http://localhost:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

USER movi

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
