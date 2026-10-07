# 001 — Seguridad OAuth2 y JWT: especificación

## Estado

`en revisión`

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
  de autorización siguen pendientes para la Fase 1 de esta iniciativa.
