# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so they land in their own layer and survive source-only rebuilds.
COPY pom.xml checkstyle.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests (Testcontainers) need a Docker daemon, which is unavailable inside the build; the suite runs in CI/dev.
RUN mvn -B package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

# Owned by root, readable by the runtime user: the application cannot overwrite its own binary.
COPY --from=build /build/target/foreign-exchange-*.jar app.jar

USER spring:spring
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
