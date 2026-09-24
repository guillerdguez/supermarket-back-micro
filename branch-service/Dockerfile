FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY libs-supermarket-commons libs-supermarket-commons
RUN chmod +x libs-supermarket-commons/mvnw \
    && cd libs-supermarket-commons && ./mvnw install -DskipTests -B

WORKDIR /workspace/branch-service
COPY branch-service/mvnw branch-service/pom.xml ./
COPY branch-service/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY branch-service/src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/branch-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
