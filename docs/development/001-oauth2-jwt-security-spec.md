# 001 — Seguridad OAuth2 y JWT: especificación

## Estado

`cerrada — 2026-10-08`

## Problema y contexto

El microservicio expone operaciones REST de pedidos (`/api/orders`) y GraphQL
(`POST /graphql`) sin autenticación ni autorización. Cualquier cliente puede
leer o crear pedidos. La plataforma actual es Java 21 y Spring Boot 4.1.1, con
Spring Security 7 disponible para la futura integración; el entorno local usa
Docker Compose.

Además de proteger el sistema, la iniciativa busca aprender un flujo OAuth2
realista: emisión de tokens por un proveedor de identidad y validación de JWT
por la API.

## Resultado esperado y criterios de éxito

- Los endpoints de pedidos REST y GraphQL rechazan peticiones sin un JWT válido.
- Un proveedor OAuth2/OIDC emite JWT firmados para usuarios de prueba.
- La API valida firma, emisor y caducidad usando las claves públicas del
  proveedor, sin compartir una clave simétrica.
- Los permisos del token separan lectura de creación de pedidos.
- Existen pruebas y una guía reproducible para obtener un token y probar los
  casos autorizados y no autorizados.

## Alcance y exclusiones

Incluido:

- Keycloak local como proveedor OAuth2/OIDC en Docker Compose.
- Configuración de Spring Security como OAuth2 Resource Server.
- Protección de REST y GraphQL con roles de lectura y escritura.
- Usuarios, roles y cliente de demostración para desarrollo local.
- Pruebas de seguridad y documentación de uso.

Excluido inicialmente:

- Registro público de usuarios, recuperación de contraseña y MFA.
- Gestión productiva de secretos, alta disponibilidad de Keycloak y SSO con
  otros sistemas.
- Autenticación/autorización de Kafka, PostgreSQL, ClickHouse o ELK.
- Revocación inmediata de JWT antes de su caducidad.

## Restricciones, dependencias y supuestos

- Debe ser compatible con Java 21, Spring Boot 4.1.1 y Spring Security 7.
- Docker Compose ya es el entorno local de integración; se añadirá Keycloak a
  ese entorno si se aprueba la fase correspondiente.
- Se usará el flujo OAuth2 apropiado para clientes de prueba. No se almacenarán
  contraseñas ni secretos reales en el repositorio.
- El endpoint de salud básico deberá permanecer disponible para los healthchecks
  del contenedor; el detalle de salud se revisará durante el diseño.

## Brainstorming y alternativas

### Alternativa 1 — JWT emitido por la aplicación

La aplicación incorporaría inicio de sesión, usuarios y emisión/verificación de
JWT. Tiene poco despliegue adicional, pero no aporta un servidor OAuth2 real y
acopla la gestión de identidad al servicio de pedidos.

### Alternativa 2 — Spring Authorization Server

Un componente Spring propio emitiría tokens OAuth2. Ofrece control total y un
ecosistema homogéneo, pero amplía de forma considerable el código, la operación
y los conceptos que debe resolver este proyecto.

### Alternativa 3 — Keycloak OAuth2/OIDC + API como Resource Server

Keycloak gestiona usuarios, clientes, roles y emisión de JWT; el microservicio
solo valida los access tokens y aplica autorización. Permite ejercitar OAuth2
con responsabilidades separadas y un entorno local autocontenido.

### Alternativa 4 — Proveedor SaaS (Auth0, Okta u otro)

Reduce la operación local, pero requiere una cuenta externa, puede generar
coste y hace menos reproducible el entorno de aprendizaje.

## Comparación y recomendación

Se recomienda la alternativa 3: Keycloak local como Authorization Server
OAuth2/OIDC y este microservicio como OAuth2 Resource Server. Equilibra mejor
valor formativo, separación de responsabilidades, compatibilidad con el stack
actual y reproducibilidad local. La aplicación no emitirá tokens ni gestionará
credenciales.

## Decisiones autorizadas

| Fecha | Decisión | Motivo | Autorización |
| --- | --- | --- | --- |
| 2026-10-07 | Usar Keycloak local como proveedor OAuth2/OIDC y la aplicación como OAuth2 Resource Server. | Flujo realista, separación de identidad y negocio, y aprendizaje reproducible. | Usuario |
| 2026-10-07 | Crear esta spec y el plan de la iniciativa en `docs/development/`. | Mantener trazabilidad del trabajo. | Usuario |
| 2026-10-07 | Actualizar la plataforma objetivo de la iniciativa a Java 21, Spring Boot 4.1.1 y Spring Security 7; mantener la iniciativa pausada hasta una autorización explícita para reanudarla. | Reflejar la modernización completada sin ampliar la autorización de seguridad. | Usuario, al autorizar la Fase 5 de la iniciativa 002 |

## Riesgos, preguntas y decisiones pendientes

- Definir los roles concretos y su asignación a lectura y creación.
- Decidir si GraphQL se protege íntegramente por endpoint o con autorización
  adicional por operación.
- Confirmar el flujo de obtención de token para la demostración y qué clientes
  estarán fuera del navegador.
- Elegir una versión de Keycloak compatible y mantenida antes de modificar
  Docker Compose.
- La versión de Keycloak, el flujo de obtención de token, los roles y el alcance
  de autorización fueron definidos y autorizados en la Fase 1 (2026-10-07).

## Diseño autorizado — Fase 1 (2026-10-07)

- **Proveedor y entorno:** Keycloak `26.7.5`, con versión fijada en Docker
  Compose y un realm importable para desarrollo local. El modo `start-dev` se
  limita al entorno de desarrollo.
- **Flujo de usuario:** OpenID Connect Authorization Code con PKCE. No se
  habilitará Direct Access Grants (flujo de contraseña). Client Credentials
  queda fuera del alcance inicial por no existir un consumidor máquina a
  máquina definido.
- **API:** Spring Security OAuth2 Resource Server, sin sesión, validando
  firma, emisor y caducidad del access token JWT mediante la metadata/JWKS del
  issuer de Keycloak. No se comparte una clave simétrica.
- **Autoridades:** los roles del realm `ORDER_READ` y `ORDER_WRITE` se mapearán
  a autoridades Spring equivalentes.
- **Matriz de autorización:**

  | Recurso | Permiso requerido |
  | --- | --- |
  | `GET /api/orders` | `ORDER_READ` |
  | GraphQL `orders` y `orderById` | `ORDER_READ` |
  | `POST /api/orders` | `ORDER_WRITE` |
  | GraphQL `createOrder` | `ORDER_WRITE` |
  | `/actuator/health` | Público para el healthcheck |
  | Resto de Actuator | No expuesto |
  | `/playground` (GraphiQL) | Deshabilitado en el perfil local protegido |

- **Respuestas esperadas:** sin token o con token inválido, `401`; token
  válido sin permiso suficiente, `403`.
- **Criterios para las fases de implementación:** verificar ausencia/token
  inválido, lectura autorizada, escritura autorizada, denegación por rol y
  disponibilidad pública del healthcheck; documentar el recorrido de obtención
  y uso de un token de prueba sin guardar credenciales reales.
- **Limitaciones de entorno:** usuarios y credenciales serán únicamente de
  demostración local; no se considera configuración de producción, persistencia
  de identidad ni operación de alta disponibilidad.

### Alternativas de flujo evaluadas

- Authorization Code con PKCE: recomendado para clientes interactivos; evita
  entregar la contraseña del usuario a un script cliente.
- Direct Access Grants: descartado porque requiere enviar contraseña al cliente
  y no es necesario para este recorrido.
- Client Credentials: adecuado para clientes máquina a máquina, pero se difiere
  hasta que exista ese consumidor y su matriz de permisos.

### Decisiones autorizadas en Fase 1

| Fecha | Decisión | Motivo | Autorización |
| --- | --- | --- | --- |
| 2026-10-07 | Reanudar la iniciativa y aprobar el diseño de la Fase 1. | Continuar tras completar la modernización de plataforma. | Usuario |
| 2026-10-07 | Fijar Keycloak `26.7.5` para desarrollo local e importar un realm reproducible. | Versión publicada y entorno de demostración reproducible. | Usuario |
| 2026-10-07 | Usar Authorization Code con PKCE; excluir Direct Access Grants y diferir Client Credentials. | Mantener un flujo interactivo apropiado y acotar la primera entrega. | Usuario |
| 2026-10-07 | Definir `ORDER_READ`/`ORDER_WRITE` y la matriz de permisos de REST y GraphQL descrita arriba. | Separar explícitamente lectura y creación. | Usuario |
| 2026-10-07 | Proteger GraphQL por operación, deshabilitar GraphiQL y dejar público solo `/actuator/health`. | Aplicar acceso mínimo y conservar el healthcheck. | Usuario |

## Resultado de implementación — Fase 2 (2026-10-08)

- La aplicación usa Spring Security OAuth2 Resource Server para Bearer JWT y
  una cadena sin sesión.
- `GET /api/orders/**` y `POST /api/orders/**` requieren autenticación; en esta
  fase todavía no se aplican autoridades por operación.
- `/graphql` requiere autenticación a nivel HTTP y las operaciones GraphQL se
  autorizan mediante `ORDER_READ`/`ORDER_WRITE` como se describe en Fase 3.
- `/actuator/health` permanece público; los demás paths no incluidos se deniegan.
- El issuer se configura mediante `OAUTH2_ISSUER_URI`; puede proporcionarse
  `OAUTH2_JWK_SET_URI` para configurar JWKS directamente. El issuer sigue siendo
  validado junto con firma y caducidad estándar.
- Verificación: la auditoría detectó que la primera versión solo probaba la
  cadena HTTP con `JwtDecoder` simulado y no seguía TDD. La rectificación añadió
  `JwtDecoderValidationTest`: se observó rojo al aceptar un issuer incorrecto,
  se restauró el validador estándar y pasaron 4 pruebas Nimbus con clave RSA y
  JWKS local. La suite dirigida completa pasó 11 pruebas (4 decoder, 5 HTTP,
  2 GraphQL); `git diff --check` pasó.
- Limitación: el servidor JWKS fue local y efímero; la autorización por roles
  quedó cubierta en la Fase 3.

## Resultado de implementación — Fase 3 (2026-10-08)

- `SecurityConfiguration` mapea las autoridades del `realm_access.roles` de
  Keycloak y exige `ORDER_READ` para GET REST y consultas GraphQL, y
  `ORDER_WRITE` para POST REST y la mutación `createOrder`.
- Los resolvers GraphQL aplican autorización por operación. GraphiQL quedó
  deshabilitado; `/actuator/health` continúa público.
- Compose incluye Keycloak `26.7.5` en modo desarrollo y el realm importable
  `config/keycloak/orders-realm.json`. Incluye roles, usuarios de demostración
  y cliente público `orders-cli` con Authorization Code + PKCE S256; Direct
  Access Grants y service accounts están deshabilitados.
- TDD: las pruebas de denegación se ejecutaron primero y fallaron en tres casos
  de permisos; después de la implementación pasaron. La suite completa
  `mvn -q test` pasó con 17 pruebas. La validación JSON del realm, `docker compose
  config --quiet` y `git diff --check` pasaron.
- Keycloak inició e importó `orders`; se comprobó discovery, issuer y JWKS con
  firma RS256. Las pruebas de autorización inyectan claims `realm_access.roles`
  en el contexto HTTP.
- Validación manual de aceptación (2026-10-08), con el contenedor actualizado:
  `reader` obtuvo token por Authorization Code + PKCE, pudo consultar REST
  (`200`) y GraphQL (`data`), y recibió `403` en REST POST y `FORBIDDEN` en la
  mutación GraphQL (`createOrder: null`). `writer` obtuvo token por el mismo
  flujo, creó por REST (`200`, pedido id 7) y por GraphQL (`data.createOrder`,
  sin errores).
- Desviación detectada durante la validación: la primera prueba de `reader` se
  ejecutó contra un contenedor de API creado 21 horas antes, con una imagen
  anterior que aún no incorporaba la autorización por roles. Esa imagen aceptó
  el POST y creó el pedido local id 6 `reader-denied`. Se reconstruyó y recreó
  solo la API con `docker compose up -d --build app`; la aplicación quedó
  saludable y las pruebas manuales posteriores confirmaron la política
  autorizada. El pedido de prueba permanece en la base local.
- La construcción Docker ejecuta `mvn clean package -DskipTests -U`; por ello
  no se atribuye una nueva ejecución de la suite a ese build. La última suite
  Maven registrada pasó con 17/17 pruebas; además, las verificaciones de roles
  se hicieron contra Keycloak y la API con tokens reales.

### Decisiones autorizadas en Fase 3

| Fecha | Decisión | Motivo | Autorización |
| --- | --- | --- | --- |
| 2026-10-08 | Configurar Keycloak 26.7.5 en Compose con realm importable `orders`. | Entorno local reproducible para identidad y roles. | Usuario |
| 2026-10-08 | Configurar cliente público `orders-cli` con Authorization Code + PKCE S256, sin Direct Access Grants ni service accounts. | Ajustarse al diseño OIDC aprobado y no habilitar flujos excluidos. | Usuario |
| 2026-10-08 | Asignar `ORDER_READ` a `reader` y `ORDER_WRITE` a `writer`, y exigir esos roles según la matriz aprobada. | Separar lectura de creación en REST y GraphQL. | Usuario |

## Resultado documental — Fase 4 (2026-10-08)

- README describe el arranque de API y Keycloak, endpoints, identidades de
  demostración, comprobación del issuer y obtención de access tokens con
  Authorization Code + PKCE S256. Incluye ejemplos para permisos REST y
  GraphQL y el healthcheck público.
- La guía deja claro que `reader-dev-only` y `writer-dev-only` son credenciales
  locales; los tokens se mantienen en variables temporales y no se versionan.
- La prueba automatizada inicial de PKCE no pudo completar el callback del
  navegador: Keycloak devolvió `cookie_not_found` al cliente HTTP de prueba. La
  guía sí se completó después con un navegador real para `reader` y `writer`, y
  se verificaron las respuestas REST y GraphQL de la matriz de autorización.
- Diagnóstico del canje manual (2026-10-08): el realm no fijaba
  `accessCodeLifespan`, cuyo valor predeterminado de Keycloak es un minuto. La
  salida `Code already used` también puede representar un código que ya no está
  disponible por caducidad. Por autorización del usuario, el timeout local se
  fijó en 300 segundos tanto en el realm activo como en
  `config/keycloak/orders-realm.json`. El ajuste activo se verificó por Admin
  REST (`accessCodeLifespan: 300`). Con el timeout ampliado se obtuvieron tokens
  reales para ambos usuarios y se completó la matriz de permisos manual.
- Verificación de la fase documental: `mvn -q test` pasó (17 pruebas),
  `docker compose config --quiet`, `jq empty config/keycloak/orders-realm.json`
  y `git diff --check` terminaron correctamente.

## Cierre de iniciativa — 2026-10-08

El usuario aprobó formalmente el cierre. Las cuatro fases quedaron completadas;
la verificación manual confirmó Authorization Code + PKCE y la matriz de
permisos para `reader` y `writer`. La suite Maven registrada pasó 17/17 pruebas.
