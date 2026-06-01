# Customer UI image (Assignment 2).
#
# Build context = repository root. CLI mode is the default entrypoint because
# the JavaFX GUI cannot be displayed inside a headless Docker container; the
# GUI is launched directly on the host via `./gradlew :parking-system-CustomerUI:run`.
#
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew \
    && ./gradlew --no-daemon :parking-system-CustomerUI:installDist \
    && mkdir -p /workspace/docker-dist \
    && cp -a /root/.mulligan-gradle-build/parking-system/parking-system-CustomerUI/install/parking-system-CustomerUI /workspace/docker-dist/parking-system-CustomerUI

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/docker-dist/parking-system-CustomerUI /app/
RUN mkdir -p /var/log/mulligan && chmod 750 /var/log/mulligan
ENTRYPOINT ["java", "-cp", "/app/lib/*", "com.mulligan.customer.cli.CustomerCLI"]
