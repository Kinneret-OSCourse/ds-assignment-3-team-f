# parking-server image (Assignment 2).
#
# Build context MUST be the repository root so the parking-common module is
# included in the source tree.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .

RUN chmod +x gradlew \
    && ./gradlew --no-daemon :parking-server:installDist :parking-common:jar \
    && mkdir -p /workspace/docker-dist \
    && cp -a /root/.mulligan-gradle-build/parking-system/parking-server/install/parking-server /workspace/docker-dist/parking-server

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/docker-dist/parking-server /app/
COPY --from=build /root/.mulligan-gradle-build/parking-system/parking-common/libs/parking-common-1.0-SNAPSHOT.jar /app/lib/
COPY --from=build /workspace/local-libs/amqp-client-5.21.0.jar /app/lib/
COPY --from=build /workspace/local-libs/slf4j-api-2.0.13.jar /app/lib/

RUN mkdir -p /var/log/mulligan && chmod 750 /var/log/mulligan

ENTRYPOINT ["/app/bin/parking-server"]
