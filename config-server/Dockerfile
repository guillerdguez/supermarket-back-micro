FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace/config-server

COPY config-server/mvnw config-server/pom.xml ./
COPY config-server/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY config-server/src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/config-server/target/*.jar app.jar

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
