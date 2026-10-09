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

Desde la raíz del repositorio:

```sh
docker compose up -d --build
docker compose ps
```

Compose levanta PostgreSQL 17.11, Kafka, Kafbat UI, ClickHouse, ELK, Keycloak
26.7.5 y la API. Keycloak importa el realm local `orders` al primer arranque.
Kafbat UI se conecta al listener interno de Kafka y permite explorar el cluster
local. La base de datos de pedidos usa el volumen persistente `postgres_data`.
Para parar los contenedores conservando los datos:

```sh
docker compose down
```

No uses `docker compose down -v` si quieres conservar ese volumen.

## Endpoints locales

| Servicio | URL |
| --- | --- |
| REST pedidos | `http://localhost:8080/api/orders` |
| GraphQL | `http://localhost:8080/graphql` |
| Salud | `http://localhost:8080/actuator/health` |
| Keycloak | `http://localhost:8180` |
| Realm OIDC | `http://localhost:8180/realms/orders` |
| Kafbat UI | `http://localhost:8081` |
| Kafka desde el host (IntelliJ) | `localhost:29092` |
| ClickHouse HTTP | `http://localhost:8123` |
| Elasticsearch | `http://localhost:9200` |
| Kibana | `http://localhost:5601` |

La API ofrece `GET /api/orders`, `POST /api/orders`, las consultas GraphQL
`orders` y `orderById`, y la mutación `createOrder`. REST y GraphQL requieren un
access token JWT de Keycloak. `/actuator/health` permanece público para el
healthcheck; GraphiQL está deshabilitado.

### Permisos de demostración

| Usuario | Contraseña local | Permiso | Acceso esperado |
| --- | --- | --- | --- |
| `reader` | `reader-dev-only` | `ORDER_READ` | Consultas REST y GraphQL; no puede crear pedidos |
| `writer` | `writer-dev-only` | `ORDER_WRITE` | Crear pedidos por REST y GraphQL |

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
curl -fsS http://localhost:8080/graphql \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ orders { customer amount status } }"}'
```

Para verificar la creación permitida, repite la obtención de token como
`writer`; usa ese token en el `POST /api/orders` anterior y en esta mutación:

```sh
curl -fsS http://localhost:8080/graphql \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { createOrder(customer: \"writer-smoke\", amount: 12.50) { customer amount status } }"}'
```

Sin token, REST y `/graphql` responden `401`. Un token válido sin el permiso
requerido recibe `403` en REST; GraphQL devuelve un error de autorización en la
respuesta. `/actuator/health` responde sin token.

## Compilación y pruebas

```sh
mvn -B test
```

La compilación requiere Java 21. Docker construye la imagen con Maven y
Temurin 21.

## Recorrido de pedidos y servicios auxiliares

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
