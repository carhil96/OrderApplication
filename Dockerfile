# Usa Maven + JDK para build
FROM maven:3.9.4-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Imagen final con JDK
FROM eclipse-temurin:11-jre
WORKDIR /app

# Copia jar construido
COPY --from=build /app/target/java-microservice-example-0.1.0.jar app.jar

# Copia wait-for-it script
COPY wait-for-it.sh /app/wait-for-it.sh
RUN chmod +x /app/wait-for-it.sh

# Comando de arranque usando wait-for-it
CMD ["/app/wait-for-it.sh", "postgres:5432", "--timeout=60", "--", \
     "/app/wait-for-it.sh", "kafka:9092", "--timeout=60", "--", \
     "/app/wait-for-it.sh", "clickhouse:9000", "--timeout=60", "--", \
     "/app/wait-for-it.sh", "logstash:5002", "--timeout=60", "--", \
     "java", "-jar", "/app/app.jar"]
