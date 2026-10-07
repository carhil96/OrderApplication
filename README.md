# Order Application

Microservicio de pedidos basado en Java 21 y Spring Boot 4.1.1. Expone una API
REST y GraphQL, persiste en PostgreSQL, publica eventos en Kafka y envía pedidos
a ClickHouse para analítica. Logstash, Elasticsearch y Kibana reciben y exploran
los logs.

## Requisitos

- Docker con el complemento `docker compose`.
- Maven 3.9+ y Java 21 para compilar fuera de Docker.

## Arranque local

Desde la raíz del repositorio:

```sh
docker compose up -d --build
docker compose ps
```

Compose levanta PostgreSQL 17.11, Kafka, ClickHouse y ELK junto con la API. La
base de datos usa el volumen persistente `postgres_data`. Para parar los
contenedores conservando los datos:

```sh
docker compose down
```

No uses `docker compose down -v` si quieres conservar ese volumen.

## Endpoints locales

| Servicio | URL |
| --- | --- |
| REST pedidos | `http://localhost:8080/api/orders` |
| GraphQL | `http://localhost:8080/graphql` |
| GraphiQL | `http://localhost:8080/playground` |
| Salud | `http://localhost:8080/actuator/health` |
| ClickHouse HTTP | `http://localhost:8123` |
| Elasticsearch | `http://localhost:9200` |
| Kibana | `http://localhost:5601` |

La API ofrece `GET /api/orders`, `POST /api/orders`, las consultas GraphQL
`orders` y `orderById`, y la mutación `createOrder`. La seguridad OAuth2/JWT
está documentada en la iniciativa 001, pero sigue pausada y todavía no protege
estos endpoints.

## Compilación y pruebas

```sh
mvn -B test
```

La compilación requiere Java 21. Docker construye la imagen con Maven y
Temurin 21.

## Recorrido integrado

Con Compose levantado, crea un pedido:

```sh
curl -fsS -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customer":"smoke-local","amount":12.50}'
```

Comprueba la salud con `curl -fsS http://localhost:8080/actuator/health`. El
evento aparece en los logs de la aplicación (`docker compose logs app`) y el
pedido se copia a ClickHouse en un máximo aproximado de cinco segundos:

```sh
curl -fsS --data-binary "SELECT id, customer, amount, status FROM orders WHERE customer = 'smoke-local' FORMAT JSONEachRow" http://localhost:8123/
```

Las imágenes y credenciales de Compose son solo para desarrollo local; no
representan una configuración de producción.
