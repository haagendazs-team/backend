FROM --platform=linux/amd64 eclipse-temurin:25-jre-noble
WORKDIR /app
COPY build/libs/*.jar app.jar
COPY start.sh start.sh
RUN chmod +x start.sh
ENTRYPOINT ["/bin/sh", "start.sh"]
