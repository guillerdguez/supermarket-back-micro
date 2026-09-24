FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace/eureka-server

COPY eureka-server/mvnw eureka-server/pom.xml ./
COPY eureka-server/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY eureka-server/src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/eureka-server/target/*.jar app.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
