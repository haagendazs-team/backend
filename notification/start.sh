#!/bin/sh
# ECS Task Metadata Endpoint V4에서 실제 컨테이너 IP 추출
if [ -n "$ECS_CONTAINER_METADATA_URI_V4" ]; then
  CONTAINER_IP=$(wget -qO- "${ECS_CONTAINER_METADATA_URI_V4}" 2>/dev/null | grep -o '"IPv4Addresses":\["[^"]*"' | grep -o '[0-9.]*$')
fi
if [ -z "$CONTAINER_IP" ]; then
  CONTAINER_IP=$(ip -4 addr show eth0 2>/dev/null | grep -oE 'inet ([0-9.]+)' | awk '{print $2}' | head -1)
fi
if [ -z "$CONTAINER_IP" ]; then
  CONTAINER_IP=$(hostname -I | tr ' ' '\n' | grep -v '^169\.' | grep -v '^127\.' | head -1)
fi
export EUREKA_INSTANCE_HOSTNAME="$CONTAINER_IP"
export EUREKA_INSTANCE_IP_ADDRESS="$CONTAINER_IP"
exec java -jar /app/app.jar
