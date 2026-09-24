FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY libs-supermarket-commons libs-supermarket-commons
RUN chmod +x libs-supermarket-commons/mvnw \
    && cd libs-supermarket-commons && ./mvnw install -DskipTests -B

WORKDIR /workspace/auth-service
COPY auth-service/mvnw auth-service/pom.xml ./
COPY auth-service/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY auth-service/src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/auth-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
