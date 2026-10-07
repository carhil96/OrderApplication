# 002 — Modernización de plataforma: plan de desarrollo

## Referencia

- Spec: `docs/development/002-platform-modernization-spec.md`

## Estado general

`aprobado`

La estrategia aprobada es Java 21 LTS, Spring Boot 3.5.x como puente técnico y
Spring Boot 4.1.1 como destino. Cada fase de ejecución requiere autorización
independiente.

## Fases

### Fase 1 — Matriz de compatibilidad y diseño de migración

- Estado: `completada`
- Objetivo: seleccionar el JDK, la versión de Spring Boot y las versiones
  compatibles de dependencias críticas.
- Alcance: Java, Spring Boot, GraphQL, JPA/Hibernate, Flyway, Kafka, ClickHouse
  JDBC, PostgreSQL, Docker e integración continua.
- Componentes previstos: `pom.xml`, Dockerfile, Jenkinsfile, Docker Compose y
  código/configuración afectados por las conclusiones.
- Criterios de aceptación: decisión de versiones justificada, matriz de riesgos
  y orden de migración verificable.
- Pruebas previstas: resolución de dependencias y una compilación exploratoria
  solo después de autorizar la fase de ejecución correspondiente.
- Riesgos: conflictos transitivos y dependencias sin soporte para Jakarta.
- Condición de cierre: diseño técnico y fases de ejecución aprobados.
- Resultado y evidencia: Java 21 LTS, Spring Boot 3.5.x como puente y Spring
  Boot 4.1.1 como destino. Se identificaron GraphQL Java Kickstart archivado,
  Jakarta, Jackson 3 y ClickHouse JDBC como riesgos principales.
- Decisiones autorizadas: Java 21; Boot 3.5.x como puente; Boot 4.1.1 como
  destino; Spring for GraphQL como sustituto de Kickstart.

### Fase 2 — Java 21, Spring Boot 3.5 y GraphQL mantenido

- Estado: `completada`
- Objetivo: actualizar el JDK, alcanzar un build/arranque estable en Boot 3.5.16
  y eliminar el bloqueo de GraphQL Java Kickstart para Jakarta.
- Alcance: compilador Maven, Dockerfile, pipeline, `javax` a `jakarta`,
  Spring for GraphQL y dependencias gestionadas compatibles con el puente.
- Componentes previstos: `pom.xml`, Dockerfile, Jenkinsfile, entidad `Order`,
  resolver, schema, configuración y pruebas de build/GraphQL.
- Criterios de aceptación: build y arranque con Java 21 y Spring Boot 3.5.16;
  contrato GraphQL de consultas y mutación conservado en `/graphql`.
- Pruebas previstas: compilación, pruebas existentes, prueba GraphQL, migraciones
  Flyway y healthcheck local.
- Riesgos: incompatibilidades Jakarta/Hibernate, Flyway, configuración GraphQL o
  imágenes de contenedor.
- Condición de cierre: build, pruebas GraphQL y construcción de imagen
  correctos.
- Resultado y evidencia: `mvn -B -Dmaven.repo.local=/private/tmp/orderapplication-m2 test`
  pasó con 2 pruebas GraphQL, sin fallos ni errores. Maven se ejecutó en Java 27
  del sistema y compiló con `--release 21`. `docker build` completó usando la
  imagen de compilación Temurin 21 y empaquetó la aplicación. `git diff --check`
  no detectó errores.
- Decisiones autorizadas: Fase 2 ampliada para incluir directamente la
  sustitución de Kickstart por Spring for GraphQL.
- Limitaciones: no se probaron PostgreSQL, Kafka, ClickHouse ni el healthcheck
  en ejecución; la terminal no pudo acceder al daemon para inspeccionar o
  levantar Docker Compose. Durante la prueba GraphQL, Logstash no estaba
  disponible y emitió avisos de conexión; las pruebas pasaron.

### Fase 3 — ClickHouse y observabilidad compatibles

- Estado: `completada`
- Objetivo: sustituir los adaptadores antiguos restantes antes del salto final.
- Alcance: ClickHouse JDBC moderno, soporte PostgreSQL para Flyway y encoder
  Logstash compatible con Java 21.
- Componentes previstos: `pom.xml`, Dockerfile, `docker-compose.yml`,
  `logback-spring.xml`, `ClickHouseWriter` y `ClickHouse` URL de conexión.
- Criterios de aceptación: escritura ClickHouse y logging estructurado conservan
  su comportamiento.
- Pruebas previstas: integración PostgreSQL, ClickHouse y logs.
- Riesgos: comportamiento JDBC v2 y compatibilidad del encoder.
- Condición de cierre: adaptadores verificados con evidencia.
- Resultado y evidencia: `mvn -B -Dmaven.repo.local=/private/tmp/orderapplication-m2 test`
  pasó con 2 pruebas, sin fallos ni errores. `docker compose up -d --build app`
  construyó la imagen y la aplicación arrancó con Java 21; Flyway validó la
  migración contra PostgreSQL 13.23, el healthcheck respondió `UP`, Kafka
  recibió el evento y Logstash recibió los logs. Se creó el pedido de
  verificación `fase3-clickhouse-ok` y se confirmó en ClickHouse mediante una
  consulta a la tabla `orders`. `git diff --check` no detectó errores.
- Decisiones autorizadas: usar ClickHouse JDBC 0.10.0 con clasificador `all`,
  Logstash Encoder 8.1 con la línea Jackson 2 de Boot 3.5, el módulo Flyway
  `flyway-database-postgresql`, y la conexión ClickHouse HTTP por el puerto 8123.
- Hallazgos y limitaciones: Boot 3.5 requiere el módulo Flyway separado para
  PostgreSQL. JDBC 0.10 usa HTTP; la URL previa al puerto nativo 9000 falló. El
  primer pedido de prueba (`fase3-verification`, id 2) se persistió en
  PostgreSQL y publicó en Kafka, pero el batch se perdió en ClickHouse por esa
  URL. Tras corregirla, el segundo pedido (`fase3-clickhouse-ok`, id 3) llegó
  correctamente a ClickHouse. Las pruebas Maven locales corrieron con Java 27
  y `--release 21`; el runtime de Docker usó Java 21.0.12. Durante los tests
  locales Logstash no estaba disponible, aunque la prueba integrada con Compose
  verificó la recepción de logs.

### Fase 4 — Salto final a Spring Boot 4.1.1 y Jackson 3

- Estado: `completada`
- Objetivo: completar la migración a la plataforma destino.
- Alcance: Boot 4.1.1, Spring Framework 7, Spring Security 7, Jackson 3 y
  ajustes de configuración/dependencias resultantes.
- Componentes previstos: `pom.xml`, código de serialización, configuración,
  Dockerfile, pipeline y pruebas afectadas.
- Criterios de aceptación: build, arranque y contrato de pedidos funcionales en
  Boot 4.1.1 sin compatibilidad temporal de Jackson 2 salvo excepción aprobada.
- Pruebas previstas: compilación, integración REST/GraphQL, persistencia,
  Kafka, ClickHouse y healthcheck.
- Riesgos: paquetes Jackson, dependencias transitivas y propiedades eliminadas.
- Condición de cierre: plataforma destino verificada con evidencia.
- Resultado y evidencia: `mvn -q -Dmaven.repo.local=/private/tmp/orderapplication-m2 test`
  pasó con 2 pruebas GraphQL, sin fallos ni errores. Maven se ejecutó en Java
  27 del sistema y compiló con `--release 21`. `docker compose up -d --build app`
  construyó y arrancó la aplicación con Java 21.0.12 y Spring Boot 4.1.1;
  `/actuator/health` respondió `UP`, GraphQL atendió una consulta y el esquema
  quedó sin mapeos pendientes. El pedido `fase4-boot4-smoke` (id 4) se creó por
  REST, fue recibido por Kafka y se confirmó en ClickHouse. `git diff --check`
  no detectó errores.
- Decisiones autorizadas: adoptar `spring-boot-starter-webmvc`,
  `spring-boot-starter-kafka`, `spring-boot-starter-jackson` y
  `spring-boot-starter-graphql-test`; usar `JsonMapper` de Jackson 3 inyectado
  desde Spring; actualizar Logstash Encoder a 9.0; y conservar Java 21 como
  runtime de la imagen.
- Hallazgos y limitaciones: Hibernate 7.4.5.Final avisa que PostgreSQL 13.23
  está por debajo de la versión mínima soportada (14.0); se resolvió en el
  seguimiento autorizado descrito abajo. La suite Maven local usa Java 27 con
  `--release 21`; Docker valida el runtime real Java 21.

### Seguimiento — Actualización de PostgreSQL

- Estado: `completado`
- Decisión: actualizar PostgreSQL 13.23 a PostgreSQL 17.11, una versión mayor
  soportada por Hibernate 7 y mantenida por el proyecto PostgreSQL.
- Migración y reversión: se generó y validó un backup lógico custom en
  `/private/tmp/orderapplication-appdb-before-pg17.dump`; PostgreSQL 17 usa el
  volumen nombrado `orderapplication_postgres_data`. El volumen anónimo de
  PostgreSQL 13 se conservó para rollback y no se eliminó.
- Evidencia: restauración de `appdb` conservó los 4 pedidos, historial Flyway
  con versión 1 exitosa y secuencia `orders_id_seq` en 4. PostgreSQL 17.11
  quedó `healthy`; la aplicación Boot 4.1.1 también quedó `healthy` y
  `/actuator/health` respondió `UP` con DB `UP`. Hibernate identifica la versión
  17.11 y ya no emite la advertencia de versión no soportada para PostgreSQL 13.
- Configuración: Compose usa `postgres:17.11` con volumen persistente explícito;
  se quitó el dialecto PostgreSQL redundante para que Hibernate lo detecte.

### Fase 5 — Integración, documentación y preparación de seguridad

- Estado: `completada`
- Objetivo: validar adaptadores restantes y preparar la reanudación de OAuth2/JWT.
- Alcance: Kafka, ClickHouse, healthchecks, documentación y evidencia final.
- Componentes previstos: configuración, Docker Compose, README y documentos de
  ambas iniciativas.
- Criterios de aceptación: recorrido de pedidos y servicios auxiliares
  verificado; iniciativa 001 preparada para solicitar su reanudación.
- Pruebas previstas: build, integración y recorrido manual reproducible.
- Riesgos: diferencias de ejecución entre local, Docker y CI.
- Condición de cierre: evidencia registrada y documentación autorizada.
- Resultado y evidencia: Compose mantuvo saludables app, PostgreSQL 17.11,
  Kafka, ClickHouse, Logstash, Elasticsearch y Zookeeper. `/actuator/health`
  respondió `UP` con DB `UP`. El recorrido manual creó
  `fase5-integration-smoke` (id 5) mediante REST; Kafka registró el evento y
  ClickHouse devolvió el mismo pedido. El README quedó actualizado con
  plataforma, endpoints, arranque, parada segura y recorrido reproducible. Se
  actualizó la iniciativa 001 con el stack moderno y su estado `pausado`, sin
  añadir Keycloak ni activar reglas de seguridad. `docker-compose.yml` ya no
  contiene la propiedad `version` obsoleta. `mvn -B
  -Dmaven.repo.local=/private/tmp/orderapplication-m2 test` pasó (2 pruebas,
  cero fallos/errores). Maven emitió avisos 401 al consultar metadatos de dos
  feeds opcionales, pero resolvió las dependencias necesarias; los tests locales
  también avisaron que el host `logstash` solo está disponible dentro de Compose.
- Decisiones autorizadas: cerrar la integración/documentación de plataforma y
  preparar la iniciativa 001 conservándola pausada. La reanudación de OAuth2/JWT
  requiere autorización explícita independiente.

## Siguiente acción que requiere autorización

Solicitar autorización explícita para reanudar la iniciativa 001 y ejecutar su
Fase 1 de diseño OAuth2/JWT.
