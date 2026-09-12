# syntax=docker/dockerfile:1

# --- Build stage -------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Maven Wrapper is not vendored in this repo, so install Maven directly.
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY src ./src

RUN mvn --batch-mode -DskipTests package

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S gatekeeper && adduser -S gatekeeper -G gatekeeper

WORKDIR /app
COPY --from=builder /build/target/gatekeeper-*.jar /app/gatekeeper.jar

USER gatekeeper

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/gatekeeper.jar"]
