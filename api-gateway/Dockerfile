FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace/api-gateway

COPY api-gateway/mvnw api-gateway/pom.xml ./
COPY api-gateway/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY api-gateway/src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/api-gateway/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
