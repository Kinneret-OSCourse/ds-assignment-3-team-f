FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle -Duser.home=/workspace :parking-recommender:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/.mulligan-gradle-build/parking-system/parking-recommender/install/parking-recommender/ /app/
ENTRYPOINT ["java", "-cp", "/app/lib/*", "com.mulligan.recommender.cli.RecommenderCLI"]
