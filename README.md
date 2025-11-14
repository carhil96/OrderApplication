# Java Microservice Example (Full stack - Docker)

### Qué incluye
- Java 11 + Spring Boot (REST + GraphQL)
- PostgreSQL (Flyway migrations)
- Kafka (producer + consumer)
- ClickHouse (analítica)
- ELK stack (Logstash -> Elasticsearch -> Kibana)
- Git/Maven/Jenkins example pipeline
- Docker Compose para levantar todo

### Ejecutar
1. `mvn -B -DskipTests package`
2. `docker-compose up --build`

Servicios: app: http://localhost:8080, Kibana: http://localhost:5601, Elasticsearch: http://localhost:9200

GraphQL endpoint: http://localhost:8080/graphql

