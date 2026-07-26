#!/bin/sh
# ECS Fargate 컨테이너 실제 IP를 Eureka에 등록
CONTAINER_IP=$(hostname -I | awk '{print $1}')
export EUREKA_INSTANCE_IP_ADDRESS="$CONTAINER_IP"
export EUREKA_INSTANCE_HOSTNAME="$CONTAINER_IP"
exec java -jar /app/app.jar
