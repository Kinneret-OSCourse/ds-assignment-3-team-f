FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :recommender-server:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/recommender-server/build/install/recommender-server/ ./
EXPOSE 8080
ENTRYPOINT ["/app/bin/recommender-server"]
