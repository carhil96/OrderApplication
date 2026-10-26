# Order Application

Microservicio de pedidos basado en Java 21 y Spring Boot 4.1.1. Expone una API
REST y GraphQL, persiste en PostgreSQL, publica eventos en Kafka y envía pedidos
a ClickHouse para analítica. Logstash, Elasticsearch y Kibana reciben y exploran
los logs.

## Requisitos

- Docker con el complemento `docker compose`.
- Maven 3.9+ y Java 21 para compilar fuera de Docker.
- Para el recorrido OAuth2: `curl`, `jq`, OpenSSL y un navegador.

## Arranque local

Desde la raíz de `OrderApplication`, inicia solo la infraestructura:

```sh
docker compose up -d
docker compose ps
```

Compose levanta PostgreSQL 17.11, Kafka, Kafbat UI, ClickHouse, ELK y Keycloak
26.7.5. Keycloak importa el realm local `orders` al primer arranque. La base de
datos de pedidos usa el volumen persistente `postgres_data`.

Después ejecuta las APIs desde IntelliJ:

1. Ejecuta OrderApplication. Su único archivo de configuración es
   `src/main/resources/application.yml`; escucha en `8082` y usa Keycloak en
   `localhost:8180` y Kafka en `localhost:29092` por defecto.
2. Ejecuta ApiGateway desde su proyecto. Escucha en `8080` y enruta REST y
   GraphQL a OrderApplication en `localhost:8082`.

Para parar los contenedores de infraestructura conservando los datos:

```sh
docker compose down
```

No uses `docker compose down -v` si quieres conservar el volumen de PostgreSQL.

## Endpoints locales

| Servicio | URL |
| --- | --- |
| REST pedidos por Gateway | `http://localhost:8080/api/orders` |
| GraphQL por Gateway | `http://localhost:8080/api-gateway/graphql` |
| OrderApplication directo (desarrollo/diagnóstico) | `http://localhost:8082` |
| Health de OrderApplication | `http://localhost:8082/actuator/health` |
| Health del Gateway | `http://localhost:8080/actuator/health` |
| Keycloak | `http://localhost:8180` |
| Realm OIDC | `http://localhost:8180/realms/orders` |
| Kafbat UI | `http://localhost:8081` |
| Kafka desde el host (IntelliJ) | `localhost:29092` |
| ClickHouse HTTP | `http://localhost:8123` |
| Elasticsearch | `http://localhost:9200` |
| Kibana | `http://localhost:5601` |

OrderApplication ofrece `GET /api/orders`, `POST /api/orders` y una API GraphQL
para Customer y Order. GraphQL permite consultar clientes y pedidos, recorrer
`Customer.orders`, crear/renombrar/borrar clientes (si no tienen pedidos), crear
pedidos y cambiar su estado de `CREATED` a `DELIVERED`. REST y GraphQL requieren
un access token JWT de Keycloak. Los clientes deben entrar por el Gateway en
`8080`; OrderApplication también valida JWT si se accede directamente a `8082`.
El health de ambos servicios permanece público; GraphiQL está deshabilitado.

### Permisos de demostración

| Usuario | Contraseña local | Permiso | Acceso esperado |
| --- | --- | --- | --- |
| `reader` | `reader-dev-only` | `ORDER_READ` | Consultas REST y GraphQL; no puede ejecutar mutaciones |
| `writer` | `writer-dev-only` | `ORDER_WRITE` | Mutaciones GraphQL y escrituras REST permitidas |

Estas identidades solo sirven para desarrollo local. El cliente público
`orders-cli` usa Authorization Code con PKCE S256; no habilita el flujo de
contraseña ni service accounts. En la autenticación, la contraseña se introduce
en la página local de Keycloak y no se envía a la API.

Antes de solicitar un token, espera a que Keycloak esté listo y comprueba el
issuer del realm:

```sh
curl -fsS http://localhost:8180/realms/orders/.well-known/openid-configuration \
  | jq '{issuer, code_challenge_methods_supported}'
```

### Obtener un token con Authorization Code + PKCE

Ejecuta estos comandos en una terminal Bash o Zsh. Las variables se mantienen
solo en la sesión actual:

```sh
CODE_VERIFIER=$(openssl rand -base64 48 | tr -d '=+/' | cut -c1-64)
CODE_CHALLENGE=$(printf '%s' "$CODE_VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
OAUTH_STATE=$(openssl rand -hex 16)
AUTH_URL="http://localhost:8180/realms/orders/protocol/openid-connect/auth?client_id=orders-cli&redirect_uri=http%3A%2F%2Flocalhost%2Fcallback&response_type=code&scope=openid&state=${OAUTH_STATE}&code_challenge=${CODE_CHALLENGE}&code_challenge_method=S256"
printf '%s\n' "$AUTH_URL"
```

Abre la URL impresa en el navegador e inicia sesión como `reader`. Keycloak
redirige a `http://localhost/callback`; no hay un servidor escuchando en ese
callback, así que la página puede mostrar un error de conexión. Copia el valor
del parámetro `code` de la URL del navegador y comprueba que el parámetro
`state` coincide con `$OAUTH_STATE`.

El realm local permite cinco minutos para completar el canje del código. Si el
código se rechaza o ya expiró, inicia un flujo nuevo; cada código solo se puede
canjear una vez.

Intercambia el código en la misma terminal:

```sh
AUTH_CODE='pega-aqui-el-valor-del-parametro-code'
TOKEN_RESPONSE=$(curl -fsS -X POST 'http://localhost:8180/realms/orders/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode 'client_id=orders-cli' \
  --data-urlencode 'redirect_uri=http://localhost/callback' \
  --data-urlencode "code=${AUTH_CODE}" \
  --data-urlencode "code_verifier=${CODE_VERIFIER}")
ACCESS_TOKEN=$(printf '%s' "$TOKEN_RESPONSE" | jq -r '.access_token')
unset TOKEN_RESPONSE AUTH_CODE CODE_VERIFIER CODE_CHALLENGE OAUTH_STATE AUTH_URL
```

El token queda en `$ACCESS_TOKEN` en la terminal actual. No lo publiques ni lo
incluyas en archivos versionados. Para repetir el flujo con `writer`, genera un
nuevo verifier/challenge/state y vuelve a iniciar sesión con esa identidad.
Cuando termines las comprobaciones, elimina el token de la sesión con
`unset ACCESS_TOKEN`.

### Comprobar permisos

Con el token de `reader`, la consulta de pedidos debe responder correctamente;
la creación REST debe responder `403`:

```sh
curl -i -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/api/orders
curl -i -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"customer":"reader-denied","amount":12.50}'
```

La consulta GraphQL se autoriza con `ORDER_READ`:

```sh
curl -fsS http://localhost:8080/api-gateway/graphql \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ orders { customer amount status } }"}'
```

Para consultar clientes junto con sus pedidos, usa una sola operación GraphQL.
`Customer.orders` usa un DataLoader explícito: agrupa los IDs solicitados en esa
petición y obtiene los pedidos con una consulta batch. La Fase 3 mostró la
resolución individual como línea base; puedes comparar ambas versiones en el
historial del plan de desarrollo:

```graphql
query CustomersAndOrders {
  customers {
    id
    name
    orders {
      id
      amount
      status
      statusOrder
    }
  }
}
```

También puedes buscar un Customer por ID:

```graphql
query CustomerById($id: ID!) {
  customerById(id: $id) {
    id
    name
    orders { id amount status }
  }
}
```

En Postman, configura `POST http://localhost:8080/api-gateway/graphql`, usa
Authorization tipo **Bearer Token** y pega el access token. En la pestaña
GraphQL puedes ejecutar estas operaciones directamente; para `CustomerById`,
define las variables:

```json
{ "id": "1" }
```

### Aliases y fragments

Un alias permite consultar el mismo campo con argumentos distintos en una sola
operación. Un fragment reutiliza la selección de campos en ambos resultados:

```graphql
fragment CustomerWithOrders on Customer {
  id
  name
  orders { id amount status }
}

query CompareCustomers($firstId: ID!, $secondId: ID!) {
  first: customerById(id: $firstId) { ...CustomerWithOrders }
  second: customerById(id: $secondId) { ...CustomerWithOrders }
}
```

Variables:

```json
{ "firstId": "1", "secondId": "2" }
```

Los dos aliases solicitan `Customer.orders`; DataLoader recolecta las claves
resueltas en esta operación y las entrega agrupadas al servicio.

### Mutaciones GraphQL

Usa un token `writer` para las mutaciones. Primero crea un Customer; el nombre
debe ser único y no puede estar vacío:

```graphql
mutation CreateCustomer($name: String!) {
  createCustomer(name: $name) { id name }
}
```

Variables:

```json
{ "name": "Ada Lovelace" }
```

Puedes renombrar un cliente si el nombre nuevo sigue siendo único, y borrar un
cliente solo si no tiene pedidos:

```graphql
mutation RenameCustomer($id: ID!, $name: String!) {
  updateCustomerName(id: $id, name: $name) { id name }
}
```

Variables de ejemplo:

```json
{ "id": "1", "name": "Ada Lovelace Byron" }
```

```graphql
mutation DeleteCustomer($id: ID!) {
  deleteCustomer(id: $id)
}
```

Variables:

```json
{ "id": "1" }
```

Para crear un pedido, el argumento `customer` debe coincidir con el nombre de
un Customer existente. Los pedidos son registros de seguimiento y no tienen
mutación de borrado:

```graphql
mutation CreateOrder($customer: String!, $amount: Float!) {
  createOrder(customer: $customer, amount: $amount) {
    id customer amount status createdAt statusOrder
  }
}
```

Variables:

```json
{ "customer": "Ada Lovelace", "amount": 24.75 }
```

El estado solo admite la transición `CREATED → DELIVERED`. La mutación asigna
la fecha del cambio a `statusOrder`:

```graphql
mutation DeliverOrder($id: ID!) {
  updateOrderStatus(id: $id, status: DELIVERED) {
    id status createdAt statusOrder
  }
}
```

Variables:

```json
{ "id": "1" }
```

Para verificar la creación permitida, repite la obtención de token como
`writer`; usa ese token en el `POST /api/orders` anterior y en esta mutación:

```sh
curl -fsS http://localhost:8080/api-gateway/graphql \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { createCustomer(name: \"writer-smoke\") { id name } }"}'
curl -fsS http://localhost:8080/api-gateway/graphql \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { createOrder(customer: \"writer-smoke\", amount: 12.50) { customer amount status } }"}'
```

Sin token, REST y `/api-gateway/graphql` responden `401`. Un token válido sin el permiso
requerido recibe `403` en REST; GraphQL devuelve un error de autorización en la
respuesta. `/actuator/health` responde sin token.

## Compilación y pruebas

```sh
mvn -B test
```

La compilación requiere Java 21 y Maven 3.9+. Las APIs se ejecutan desde
IntelliJ; Docker Compose solo levanta la infraestructura local.

## Recorrido de pedidos y servicios auxiliares

### Sincronización Outbox, Debezium y ClickHouse

PostgreSQL es la fuente autoritativa. OrderApplication escribe cada cambio de
Customer u Order y su evento en `outbox` dentro de la misma transacción. Debezium
lee esa tabla desde el WAL de PostgreSQL mediante logical replication y Kafka
Connect publica los eventos en `outbox.event.customer` y `outbox.event.order`.
OrderApplication consume esos topics y escribe versiones en ClickHouse;
`customers_current` y `orders_current` exponen la versión vigente. La proyección
es asíncrona, así que puede tardar unos segundos en reflejar un cambio.

En un entorno local, prepara la infraestructura y la base de datos antes de
registrar el conector:

1. Levanta PostgreSQL, Kafka, Kafka Connect y ClickHouse:

   ```sh
   docker compose up -d postgres zookeeper kafka kafka-connect clickhouse
   ```

2. Inicia OrderApplication desde IntelliJ. Flyway aplica V2 al arrancar; esta
   migración crea Customers y Outbox, relaciona los pedidos existentes, conserva
   `statusOrder` y genera eventos iniciales para poblar ClickHouse.

3. Si usas un PostgreSQL existente, asegúrate de que el rol `debezium` tenga
   `LOGIN REPLICATION`, contraseña `debezium-local-only` y permisos de lectura
   sobre `outbox`. Después de que V2 haya corrido, puedes aplicar los permisos
   con:

   ```sh
   docker compose exec -T postgres psql -U app -d appdb < config/postgres/grant-debezium-existing-volume.sql
   ```

   En una base nueva, el script `config/postgres/01-debezium-user.sh` crea el
   rol durante la inicialización del volumen PostgreSQL.

4. Si ClickHouse ya tiene una tabla física `orders`, prepara las tablas
   versionadas y las vistas antes de registrar el conector:

   ```sh
   docker compose exec -T clickhouse clickhouse-client --multiquery < src/main/resources/clickhouse/migrations/V2__prepare_versioned_projection.sql
   ```

5. Registra el conector una vez y comprueba que esté `RUNNING`:

   ```sh
   sh scripts/register-outbox-connector.sh
   curl -fsS http://localhost:8083/connectors/orders-outbox/status | jq
   ```

   La configuración está en `config/debezium/orders-outbox-connector.json`.
   El conector captura únicamente `public.outbox`; no lee directamente las
   tablas de negocio. Compose habilita logical replication en PostgreSQL; Flyway
   crea la publication `orders_outbox_publication` y Debezium crea el replication
   slot `orders_outbox_slot` al iniciar el conector.

6. Espera a que OrderApplication consuma el snapshot inicial y compara los
   conteos. No hagas el corte de la tabla física hasta que coincidan:

   ```sh
   docker compose exec -T postgres psql -U app -d appdb -c \
     "SELECT (SELECT count(*) FROM customers) AS customers, (SELECT count(*) FROM orders) AS orders;"
   docker compose exec -T clickhouse clickhouse-client --query \
     "SELECT (SELECT count() FROM default.customers_current) AS customers, (SELECT count() FROM default.orders_current) AS orders"
   ```

   Una diferencia suele indicar que el conector, el consumidor o la proyección
   todavía está procesando eventos. Revisa el estado del conector y los logs de
   OrderApplication antes de continuar.

7. Solo para una instalación existente donde `default.orders` aún sea una tabla
   física, cambia esa tabla por la vista compatible después de verificar los
   conteos. La tabla anterior se conserva como `orders_legacy`:

   ```sh
   docker compose exec clickhouse clickhouse-client --query \
     "RENAME TABLE default.orders TO default.orders_legacy"
   docker compose exec clickhouse clickhouse-client --query \
     "CREATE VIEW default.orders AS SELECT id, customer, amount, status, created_at FROM default.orders_current"
   ```

   Comprueba que la vista responde y devuelve los pedidos esperados:

   ```sh
   docker compose exec clickhouse clickhouse-client --query \
     "SELECT count() FROM default.orders"
   ```

Con la infraestructura y ambas APIs levantadas, y un token de `writer` en
`$ACCESS_TOKEN`, crea un pedido a través del Gateway:

```sh
curl -fsS -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"customer":"smoke-local","amount":12.50}'
```

Comprueba la salud con `curl -fsS http://localhost:8080/actuator/health`. El
cambio se persiste primero en PostgreSQL y luego se propaga a ClickHouse por
Outbox, Debezium y Kafka. Para consultar la proyección compatible:

```sh
curl -fsS --data-binary "SELECT id, customer, amount, status FROM orders WHERE customer = 'smoke-local' FORMAT JSONEachRow" http://localhost:8123/
```

Las imágenes y credenciales de Compose son solo para desarrollo local; no
representan una configuración de producción.
