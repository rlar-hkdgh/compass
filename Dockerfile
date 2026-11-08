FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY src/main/ src/main/
RUN sh ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system compass && useradd --system --gid compass compass
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar compass.jar
USER compass
CMD ["sh", "-c", "exec java -XX:MaxRAMPercentage=50.0 -jar /app/compass.jar --server.address=0.0.0.0 --server.port=\"${PORT:-8080}\""]
