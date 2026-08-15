## ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache dependencies separately from source so a source-only change doesn't re-download the world.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

## ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S finbank && adduser -S finbank -G finbank
COPY --from=build /app/build/libs/*.jar app.jar
USER finbank

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
