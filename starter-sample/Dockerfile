FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY starter-sample/target/observability-starter-sample-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
