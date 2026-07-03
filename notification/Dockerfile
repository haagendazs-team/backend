FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

