# 003 — Persistencia asíncrona de pedidos en ClickHouse: especificación

## Estado

`completada`

## Problema y contexto

Al crear un pedido, `OrderService` lo guarda en PostgreSQL, publica un evento en
Kafka y también lo encola en un `ClickHouseWriter` local que vacía un buffer en
memoria cada cinco segundos. `KafkaConsumer` solo registra los mensajes. El
buffer puede perder pedidos si falla la escritura o se detiene la aplicación.

La iniciativa hará que Kafka sea el camino asíncrono para mantener en ClickHouse
una copia de los pedidos creados. La persistencia principal en PostgreSQL seguirá
siendo síncrona.

## Resultado esperado y criterios de éxito

- La creación persiste el pedido en PostgreSQL de forma síncrona y publica su
  evento en Kafka.
- `KafkaConsumer` convierte los eventos recibidos en inserciones de ClickHouse.
- El consumidor solo confirma un mensaje después de que ClickHouse acepte la
  inserción; los fallos permiten reintentar el mensaje.
- Las reentregas normales del mismo evento no duplican su inserción mientras el
  token permanezca en la ventana de deduplicación configurada por ClickHouse.
- Se elimina la escritura en paralelo y el buffer local de `ClickHouseWriter`.
- El recorrido y los fallos relevantes quedan cubiertos por pruebas verificables.

## Alcance y exclusiones

Incluido:

- Trasladar la escritura de ClickHouse al consumidor Kafka.
- Transportar en el evento los datos requeridos por la fila de ClickHouse
  (`id`, `customer`, `amount`, `status` y `created_at`), con valores por defecto
  para eventos históricos que omiten estado y fecha.
- Añadir un token de deduplicación estable derivado del ID del pedido a cada
  inserción de ClickHouse.
- Configurar el manejo de errores y confirmación del consumidor para que una
  escritura fallida pueda reintentarse.
- Retirar `ClickHouseWriter` y su buffer/ejecución programada cuando ya no sean
  usados.
- Añadir pruebas del procesamiento, deduplicación por reintento y flujo afectado.

Excluido:

- Patrón outbox o transacción distribuida entre PostgreSQL y Kafka.
- Garantía de entrega si PostgreSQL confirma el pedido pero la publicación del
  evento falla; este riesgo se acepta en esta iniciativa.
- Garantía de unicidad permanente en ClickHouse. El token protege reintentos
  dentro de la ventana de deduplicación, que es finita.
- Cambios a las APIs de pedidos o al modelo de negocio.

## Restricciones, dependencias y supuestos

- El despliegue local usa ClickHouse 23.8 y una tabla `MergeTree` no replicada.
- La deduplicación por token de inserción existe para la familia `MergeTree`; se
  debe habilitar explícitamente para la tabla no replicada actual mediante
  `non_replicated_deduplication_window` mayor que cero. El token y la inserción
  deben verificarse con ClickHouse 23.8 y el controlador JDBC del proyecto.
- El ID PostgreSQL es estable y único para identificar cada pedido.
- Kafka puede entregar un evento más de una vez; el consumidor debe tolerar
  reintentos.
- La publicación directa desde `OrderService` no es atómica con la transacción
  PostgreSQL. El envío se gestiona como parte separada del cambio aprobado y no
  se presenta como garantía de entrega.
- La deduplicación del motor `MergeTree` conserva una cantidad finita de bloques;
  no equivale a una restricción `UNIQUE` permanente.

## Brainstorming y alternativas

### Alternativa 1 — Token de deduplicación de inserción

Mantener `MergeTree` y enviar un token estable basado en el ID del pedido al
insertar desde `KafkaConsumer`. Los reintentos del mismo pedido dentro de la
ventana configurada se reconocen como la misma inserción. Es la alternativa
seleccionada por su bajo impacto en el esquema y porque se alinea con el flujo de
eventos.

### Alternativa 2 — `ReplacingMergeTree` por ID

Cambiar el motor y usar el ID como clave de ordenación para reconciliar filas
duplicadas durante las fusiones. La reconciliación es eventual y las consultas
pueden requerir `FINAL`; no garantiza que nunca haya varias filas físicas.

### Alternativa 3 — Consultar ClickHouse antes de insertar

El consumidor consulta por ID y omite la inserción si encuentra el pedido. La
comprobación y la inserción no son atómicas, puede fallar bajo concurrencia y
añade una lectura por evento.

### Alternativa 4 — Outbox en PostgreSQL

Guardar el pedido y el evento en una misma transacción PostgreSQL y publicar
desde una tabla outbox. Mejoraría la fiabilidad de publicación, pero amplía el
alcance con esquema, publicador y ciclo de vida de eventos; se descartó para esta
iniciativa.

## Comparación y recomendación

Se selecciona la alternativa 1. Conserva la tabla existente y permite que el
consumidor reintente inserciones usando la identidad estable del pedido. Su
garantía tiene una ventana finita; no implementa una restricción única global.
La configuración de reintentos, confirmación Kafka y retención de tokens debe
ser coherente para que el comportamiento esperado quede cubierto.

## Decisiones autorizadas

| Fecha | Decisión | Motivo | Autorización |
| --- | --- | --- | --- |
| 2026-10-15 | Mantener PostgreSQL como persistencia síncrona y mover la escritura ClickHouse al consumidor Kafka. | ClickHouse es una copia asíncrona y el consumidor debe responsabilizarse de persistir los eventos. | Usuario |
| 2026-10-15 | Usar un token de deduplicación estable por ID del pedido en ClickHouse. | Evitar inserciones repetidas durante los reintentos Kafka sin añadir una consulta previa ni cambiar inicialmente el motor de tabla. | Usuario |
| 2026-10-15 | Excluir outbox de esta iniciativa. | El usuario descartó esa alternativa. | Usuario |
| 2026-10-15 | Habilitar la ventana finita de deduplicación en la tabla `MergeTree` no replicada. | ClickHouse no habilita por defecto la deduplicación en esta tabla; el token requiere que se conserve su registro de deduplicación. | Usuario |
| 2026-10-15 | Desactivar auto commit, propagar fallos de ClickHouse y reintentar con espera creciente; enviar eventos inválidos a una cola de errores. | Evitar confirmar eventos antes de persistirlos y aislar mensajes que no se pueden procesar. | Usuario |

## Implementación y verificación

- `OrderService` persiste el pedido en PostgreSQL y publica en Kafka el ID,
  cliente, importe, estado y fecha de creación. Ya no invoca la escritura de
  ClickHouse.
- `KafkaConsumer` valida y deserializa eventos, completa estado/fecha ausentes
  en eventos antiguos y delega la persistencia en `ClickHouseWriter`.
- `ClickHouseWriter` inserta un evento por operación con
  `insert_deduplicate = 1` y un token `order-<id>`. Antes de su primera
  inserción, aplica `ALTER TABLE ... MODIFY SETTING` con ventana de 10.000
  bloques; la creación de tablas nuevas incluye el mismo ajuste en
  `clickhouse-init.sql`.
- La configuración desactiva auto commit. Los fallos de ClickHouse se propagan
  y se reintentan con espera exponencial sin límite; eventos inválidos se
  publican en `<topic>.DLT`.
- TDD: la prueba inicial del token falló frente al comportamiento existente;
  tras la implementación, las pruebas unitarias cubren token estable, evento
  completo, persistencia sincrónica PostgreSQL, compatibilidad con eventos
  antiguos, evento inválido y propagación de errores.
- La prueba de integración usa Testcontainers para iniciar ClickHouse 23.8 y
  ahora forma parte de la suite normal; requiere Docker disponible durante la
  ejecución. Comprueba que dos inserciones del mismo evento vía ClickHouse JDBC
  0.10.0 dejan una sola fila en una tabla temporal, que el test elimina.
- Evidencia: el usuario confirmó que la prueba funciona correctamente al correr
  la suite completa. Antes de incorporar Testcontainers, la suite omitía esta
  prueba y se ejecutaba aparte mediante una propiedad del sistema.
- No se hizo una prueba integrada que publique por un broker Kafka real y
  consuma hasta ClickHouse. La configuración del listener y la propagación de
  errores se verificaron por compilación y pruebas unitarias.

## Riesgos, preguntas y decisiones pendientes

- La publicación Kafka puede fallar después de persistir el pedido en
  PostgreSQL; sin outbox, ClickHouse podría no recibir la copia.
- La deduplicación por token solo aplica mientras ClickHouse conserve el token
  en su ventana. Una reentrega posterior podría insertar otra fila.
- El valor de la ventana de deduplicación debe dimensionarse para el volumen y
  retraso máximos esperados de reintentos; al ser por cantidad de bloques, una
  ventana llena deja de reconocer tokens antiguos.
- La fecha de creación y el estado deben serializarse de forma compatible con
  ClickHouse y con eventos ya existentes en Kafka.
- La cola de errores necesita una política operativa de retención, alertado y
  reproducción; queda pendiente.
- La publicación Kafka de `OrderService` sigue siendo asíncrona y no tiene
  outbox: un fallo del broker puede dejar el pedido de PostgreSQL sin copia en
  ClickHouse. El callback registra el fallo, pero no existe una recuperación
  durable en PostgreSQL.
- Cada mensaje provoca una inserción individual en ClickHouse. Si aumenta
  sustancialmente el volumen, habrá que evaluar lotes sin perder la identidad de
  deduplicación por evento.

## Referencias técnicas

- [ClickHouse: deduplicar inserciones en reintentos](https://clickhouse.com/docs/guides/developer/deduplicating-inserts-on-retries)
- [ClickHouse 22.2: token personalizado de deduplicación](https://clickhouse.com/blog/clickhouse-22-2-released)
