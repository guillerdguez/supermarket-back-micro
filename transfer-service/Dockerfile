FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY libs-supermarket-commons libs-supermarket-commons
RUN chmod +x libs-supermarket-commons/mvnw \
    && cd libs-supermarket-commons && ./mvnw install -DskipTests -B

WORKDIR /workspace/transfer-service
COPY transfer-service/mvnw transfer-service/pom.xml ./
COPY transfer-service/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY transfer-service/src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/transfer-service/target/*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
