FROM --platform=linux/amd64 eclipse-temurin:25-jre-noble
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
