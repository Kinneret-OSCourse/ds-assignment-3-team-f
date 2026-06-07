# MO UI image (Assignment 2).
# Build context = repository root.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :parking-system-MOUI:installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/parking-system-MOUI/build/install/parking-system-MOUI /app/
RUN mkdir -p /var/log/mulligan && chmod 750 /var/log/mulligan
ENTRYPOINT ["java", "-cp", "/app/lib/*", "com.mulligan.mo.cli.MOCLI"]
