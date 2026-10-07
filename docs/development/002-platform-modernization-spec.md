# 002 — Modernización de plataforma: especificación

## Estado

`aprobada`

## Problema y contexto inicial

El servicio usa Java 11, Spring Boot 2.7.12 y GraphQL Java Kickstart 11.1.0.
Spring Boot 2.7 ya no recibe mantenimiento OSS y GraphQL Java Kickstart está
archivado. La iniciativa de seguridad OAuth2/JWT depende de esta plataforma y
quedaría sujeta a una migración posterior de Spring Security si se implementara
ahora.

El objetivo es modernizar el stack de forma incremental, manteniendo las
funcionalidades de pedidos, REST, GraphQL, Kafka, PostgreSQL, ClickHouse y el
entorno Docker Compose.

## Estado de ejecución

Las fases 1 a 5 están completadas. El proyecto usa Java 21 en Docker, Spring
Boot 4.1.1, Spring Framework 7, Jackson 3 y PostgreSQL 17.11. La aplicación
conserva REST, GraphQL, persistencia, Kafka, ClickHouse y healthcheck según
pruebas Maven y verificación integrada de Docker Compose. El README contiene
un recorrido local reproducible. La iniciativa OAuth2/JWT refleja el stack
actual, pero permanece pausada hasta autorizar su reanudación. La suite Maven
local se ejecuta en Java 27 con `--release 21`.

PostgreSQL se migró desde 13.23 mediante backup y restauración lógica a un
volumen nombrado nuevo. Los 4 pedidos, Flyway y la secuencia quedaron
conservados; el volumen original se mantuvo para rollback. Tras la restauración,
PostgreSQL y la aplicación están `healthy`, `/actuator/health` responde `UP` y
Hibernate 7 ya no emite la advertencia de versión no soportada.

## Resultado esperado y criterios de éxito

- El proyecto compila y pasa sus pruebas con una versión LTS de Java aprobada.
- Spring Boot y sus dependencias gestionadas pasan a una línea soportada.
- No permanecen imports `javax.persistence`; se adopta Jakarta Persistence.
- GraphQL deja de depender de GraphQL Java Kickstart y usa una alternativa
  mantenida de Spring.
- REST, GraphQL, persistencia, publicación Kafka, escritura en ClickHouse y
  healthcheck conservan su comportamiento verificable.
- La iniciativa OAuth2/JWT puede reanudarse sobre una base compatible con
  Spring Security moderno.

## Alcance y exclusiones

Incluido:

- Selección razonada de versión LTS de Java y línea de Spring Boot.
- Migración de código, configuración, Dockerfile y pipeline necesarias para la
  plataforma elegida.
- Migración de JPA de `javax` a `jakarta`.
- Sustitución de GraphQL Java Kickstart por Spring for GraphQL.
- Migración de Spring Boot 3.5.16 a 4.1.1 y de la integración de Jackson 2 a
  Jackson 3.
- Actualización y creación de pruebas proporcionales a los límites modificados.

Excluido inicialmente:

- OAuth2/JWT, Keycloak y reglas de autorización; quedan en la iniciativa 001.
- Cambios de modelo de datos, rediseño de las APIs de pedidos o nuevas
  funcionalidades de negocio.
- Actualización de imágenes de infraestructura no requerida por compatibilidad.
- Cambios de arquitectura no motivados por la modernización.

## Restricciones, dependencias y supuestos

- La versión final debe ser compatible con las dependencias de PostgreSQL,
  Kafka, ClickHouse, Flyway, Logstash y GraphQL escogidas.
- La aplicación se distribuye como imagen Docker y se construye con Maven; ambas
  rutas deben usar el mismo JDK.
- Se preservarán los endpoints REST y el contrato funcional GraphQL salvo
  cambios explícitamente autorizados.
- La migración se hará por fases; cada fase requiere aprobación independiente.

## Brainstorming y alternativas

### Alternativa 1 — Java 17 + Spring Boot 3.x

Java 17 es el mínimo requerido por Spring Boot 3 y reduce el tamaño inicial del
salto desde Java 11. Exige migrar a Jakarta, Spring Framework 6 y Spring
Security 6. Es una ruta conocida, pero la línea concreta de Boot 3 debe
confirmarse como soportada antes de ejecutarla.

### Alternativa 2 — Java 21 + Spring Boot actual compatible

Java 21 es LTS y ofrece una ventana de mantenimiento más amplia que Java 17.
Reduce una futura actualización de JDK, manteniendo un salto de código similar
al de Java 17. Requiere validar explícitamente cada dependencia no gestionada.

### Alternativa 3 — Java 25 + Spring Boot actual compatible

Java 25 es el LTS más reciente. Maximiza la vida útil de la plataforma, pero es
la opción con mayor distancia respecto al entorno actual y exige una matriz de
compatibilidad más rigurosa para todos los adaptadores.

### Alternativa 4 — Mantener Java 11 y Spring Boot 2.7

Minimiza cambios a corto plazo, pero conserva una base sin mantenimiento OSS y
duplica el trabajo de seguridad cuando se produzca una actualización futura.

## Comparación y recomendación

Se descarta la alternativa 4. La evaluación recomienda Java 21 LTS por
equilibrar soporte, madurez y coste de cambio. El destino es Spring Boot 4.1.1,
compatible con Java 21. La última revisión disponible de Spring Boot 3.5.x se
usará solo como puente técnico, siguiendo la ruta recomendada por Spring para
migrar a Boot 4.

La migración de GraphQL a Spring for GraphQL es necesaria: se preservarán el
schema y el endpoint `/graphql`, migrando los resolvers a controladores con
`@QueryMapping` y `@MutationMapping`. Boot 4 prioriza Jackson 3; adaptar el uso
de `ObjectMapper` y el encoder de Logstash será un riesgo explícito. No se
usará la compatibilidad temporal de Jackson 2 salvo necesidad demostrada y
autorizada.

## Decisiones autorizadas

| Fecha | Decisión | Motivo | Autorización |
| --- | --- | --- | --- |
| 2026-10-07 | Pausar la iniciativa 001 de OAuth2/JWT hasta modernizar la plataforma. | Evitar implementar seguridad sobre un stack sin mantenimiento y tener que adaptarla después. | Usuario |
| 2026-10-07 | Crear la iniciativa 002 de modernización. | Actualizar primero la base tecnológica y reducir trabajo futuro. | Usuario |
| 2026-10-07 | Seleccionar Java 21 LTS. | Equilibrio entre soporte moderno, madurez y compatibilidad. | Usuario |
| 2026-10-07 | Usar Spring Boot 3.5.x como puente y Spring Boot 4.1.1 como destino. | Ruta incremental recomendada por Spring. | Usuario |
| 2026-10-07 | Sustituir GraphQL Java Kickstart por Spring for GraphQL y adoptar Jackson 3. | Kickstart está archivado y Boot 4 prioriza Jackson 3. | Usuario |

## Resultado de la migración

- Spring Boot 4.1.1, Java 21 en la imagen y Jackson 3 quedaron aplicados.
- Los starters MVC, Kafka, Jackson y pruebas GraphQL corresponden a la línea
  Boot 4; Logstash Encoder se actualizó a 9.0.
- Las 2 pruebas GraphQL pasan. La aplicación integrada arranca con health
  `UP`; una consulta GraphQL funciona y un pedido de prueba recorrió REST,
  Kafka y ClickHouse.
- La advertencia de Hibernate 7 sobre PostgreSQL 13 quedó resuelta al migrar
  a PostgreSQL 17.11; la detección automática del dialecto también eliminó el
  aviso por configuración explícita redundante.

## Riesgos, preguntas y decisiones pendientes

- La verificación automatizada cubre el contrato GraphQL; REST, Kafka, ClickHouse
  y healthcheck se verificaron con recorrido integrado en Docker Compose. Una
  prueba automatizada persistente del recorrido completo queda como mejora.
- OAuth2/JWT y Spring Security como mecanismo de autenticación/autorización
  permanecen fuera de esta modernización y pausados en la iniciativa 001.
