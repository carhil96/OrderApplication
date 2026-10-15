# 003 — Persistencia asíncrona de pedidos en ClickHouse: plan de desarrollo

## Referencia

- Spec: `docs/development/003-clickhouse-async-kafka-spec.md`

## Estado general

`completada`

El diseño detallado y la implementación quedaron completados y documentados el
2026-10-15. El usuario autorizó el cierre de la iniciativa.

## Fases

### Fase 1 — Diseño detallado y contrato del evento

- Estado: `completada`
- Objetivo: concretar contrato, configuración de deduplicación y comportamiento
  de confirmación/reintentos antes de editar código.
- Alcance: validar compatibilidad del token con ClickHouse 23.8 y el controlador
  JDBC; definir la ventana de deduplicación; confirmar campos del evento,
  confirmación de offsets, reintentos y tratamiento de fallos permanentes.
- Componentes previstos: `OrderEvent`, `OrderService`, `KafkaConsumer`,
  `KafkaConfig`, `application.yml`, `docker-compose.yml` y documentación.
- Criterios de aceptación: diseño que cubra escritura, fallo, reentrega y
  publicación no atómica con PostgreSQL; límites de deduplicación registrados.
- Pruebas previstas: integración de inserción repetida y token en ClickHouse
  23.8; errores de ClickHouse y manejo de offsets Kafka (pendientes de la fase 2).
- Riesgos: ventana finita; diferencias del controlador JDBC; eventos inválidos.
- Condición de cierre: diseño aprobado por el usuario.
- Resultado y evidencia: inspección de `clickhouse-init.sql`,
  `docker-compose.yml`, entidades/eventos, consumidor y configuración Kafka.
  ClickHouse 23.8 soporta tokens personalizados (disponibles desde 22.2), pero
  la tabla actual es `MergeTree` no replicada y necesita una ventana de
  deduplicación positiva. El evento carece de `status` y `created_at`; el
  consumidor actualmente absorbe excepciones y auto commit está activo. La
  documentación oficial confirma que la ventana es finita.
- Decisiones autorizadas: token estable por ID; ventana explícita para tabla no
  replicada; auto commit desactivado; errores de ClickHouse con reintento y
  espera creciente; eventos inválidos enviados a cola de errores; sin outbox.
- Pruebas ejecutadas: ninguna; fase de diseño de solo lectura.

### Fase 2 — Persistencia ClickHouse desde el consumidor

- Estado: `completada`
- Objetivo: hacer que el consumidor persista en ClickHouse y tolere reentregas.
- Alcance: serializar los campos requeridos; insertar desde `KafkaConsumer` con
  token estable; propagar fallos para permitir reintentos; retirar la escritura
  programada y el buffer de `ClickHouseWriter`.
- Componentes previstos: servicio Kafka, `OrderEvent`, configuración Kafka,
  `ClickHouseWriter` o adaptador de persistencia resultante y pruebas afectadas.
- Criterios de aceptación: un evento válido crea la fila; repetir el mismo
  evento dentro de la ventana no duplica la inserción; una falla de ClickHouse
  se propaga al error handler para reintento; el guardado síncrono en PostgreSQL
  se conserva.
- Pruebas previstas: unitarias del consumidor/escritor, integración del token
  con ClickHouse y pruebas del contrato de creación según el alcance aprobado.
- Riesgos: la ventana es finita; los reintentos ilimitados bloquean la partición
  si ClickHouse permanece indisponible; publicación directa sin outbox puede
  perder la copia asíncrona.
- Condición de cierre: pruebas aprobadas, revisión del diff y evidencia de la
  escritura idempotente.
- Resultado y evidencia: `ClickHouseWriter` usa `order-<id>` como token y
  configura explícitamente una ventana de 10.000 bloques antes de su primera
  inserción. La prueba de integración real en ClickHouse 23.8/JDBC 0.10.0
  confirmó una fila después de dos entregas idénticas. El consumidor propaga los
  fallos para reintento y enruta eventos inválidos a `.DLT`; la configuración de
  Kafka tiene auto commit desactivado. `OrderService` conserva el guardado
  síncrono en PostgreSQL y publica el evento completo sin llamar directamente a
  ClickHouse. La suite Maven pasó (22 pruebas, cero fallos); la integración
  ClickHouse pasó aparte; `git diff --check` pasó.
- Decisiones autorizadas: token estable por ID; ventana positiva de 10.000
  bloques; reintento exponencial sin límite para errores de ClickHouse; DLT para
  eventos inválidos; no usar outbox.
- Limitaciones de verificación: no se ejecutó un recorrido extremo a extremo con
  un broker Kafka real. Los tests de contexto registraron avisos de Kafka y
  Logstash no disponibles en la ejecución local, sin fallos de pruebas.

## Cierre

Iniciativa cerrada con las verificaciones y limitaciones registradas en la fase
2. La especificación y el plan quedan completados.
