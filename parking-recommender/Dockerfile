FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :parking-recommender:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/parking-recommender/build/install/parking-recommender/ /app/
ENTRYPOINT ["/app/bin/parking-recommender"]
