# Usa Maven + JDK para build
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar archivos del proyecto
COPY pom.xml .
COPY src ./src

# Compilar con limpieza completa y forzar actualización de dependencias
RUN mvn clean package -DskipTests -U

# Imagen final con JDK
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copia jar construido
COPY --from=build /app/target/java-microservice-example-0.1.0.jar app.jar

# Inicia la aplicación; JAVA_OPTS es opcional y se expande mediante el shell.
CMD ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
