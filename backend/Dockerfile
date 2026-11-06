FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn -q -DskipTests package && rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/shambanexa-backend-0.1.0.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
