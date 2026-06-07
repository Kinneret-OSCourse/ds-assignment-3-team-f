# parking-server image (Assignment 2).
#
# Build context MUST be the repository root so the parking-common module is
# included in the source tree.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .

RUN chmod +x gradlew && ./gradlew --no-daemon :parking-server:installDist :parking-common:jar

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/parking-server/build/install/parking-server /app/
COPY --from=build /workspace/parking-common/build/libs/parking-common-1.0-SNAPSHOT.jar /app/lib/

RUN mkdir -p /var/log/mulligan && chmod 750 /var/log/mulligan

ENTRYPOINT ["/app/bin/parking-server"]
