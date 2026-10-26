# 005 — Relación persistida Customer–Order para aprendizaje de GraphQL: plan

## Referencia

- Spec: `docs/development/005-graphql-customer-orders-spec.md`

## Estado general

`Iniciativa completada y cerrada el 2026-10-26; fases 1–4 y pruebas E2E completadas`

La persistencia y sincronización están implementadas. La Fase 3 expone el
contrato GraphQL de Customer–Order y deja registrado el comportamiento inicial
de resolución individual. La Fase 4 agrega DataLoader explícito, carga por lotes
y ejemplos para explorar variables, aliases, fragments y relaciones.

## Fases ejecutadas y completadas

### Fase 1 — Contrato de identidad y compatibilidad de migración

- Estado: `completada; diseño aprobado por el usuario el 2026-10-20`
- Objetivo: cerrar el diseño del modelo persistido Customer–Order, la identidad
  por nombre y la compatibilidad de los contratos actuales.
- Alcance: definir el CRUD GraphQL de clientes y el ciclo limitado de pedidos
  (consulta, creación y cambio de estado, sin borrado); confirmar rechazo de pedidos con cliente
  desconocido, unicidad de nombre, esquema `customers`, clave foránea
  `orders.customer_id`, backfill exacto, eliminación de `orders.customer`,
  respuesta REST, schema GraphQL y contratos de eventos Kafka para sincronizar
  cambios hacia ClickHouse mediante Transactional Outbox y Debezium CDC.
- Componentes previstos: entidades `Customer`/`Order`, repositorios, servicio de
  pedidos, DTOs REST, `OrderEvent`, migraciones Flyway y schema/resolvers
  actuales, únicamente como áreas de diseño.
- Criterios de aceptación: plan de migración con recuperación documentada;
  nombres existentes preservados byte a byte dentro del texto; cero pedidos
  huérfanos; contratos de lectura/escritura actuales identificados; propagación
  de renombres/borrados a ClickHouse/Kafka definida; borrado Customer restringido
  a clientes sin pedidos; `statusOrder` y transición única a entregado
  definidos; contrato Outbox, Kafka Connect/Debezium, logical replication,
  reintentos, idempotencia, versionado y orden de eventos definidos.
- Pruebas previstas: plan para migración desde V1, pedidos con nombres
  repetidos, límites de longitud, nombre vacío/null y compatibilidad de REST,
  GraphQL y evento.
- Riesgos: identidad por texto libre, carreras al crear clientes y coste de
  mantener compatibilidad externa.
- Condición de cierre: diseño detallado revisado y aprobado por el usuario.
- Cierre: el usuario aprobó la Fase 1 y confirmó la transición única
  `CREATED → DELIVERED`, el campo `statusOrder`, nombres no vacíos y Customer
  borrable solo sin pedidos.
- Cierre histórico: la Fase 2 recibió autorización independiente, se implementó
  y su cierre está registrado abajo.

### Fase 2 — Persistencia Customer–Order y backfill

- Estado: `completada y verificada el 2026-10-20`
- Objetivo: persistir clientes y relacionar los pedidos existentes sin perder
  información, manteniendo los contratos externos existentes y haciendo aditivos
  los nuevos campos/eventos.
- Alcance: migración Flyway; entidad/repositorio Customer; relación JPA;
  búsqueda de Customer existente y rechazo si no existe; mapeo a nombre de
  cliente para REST, GraphQL y `OrderEvent`; eliminar el texto duplicado de
  `orders` tras verificar el backfill; añadir `status_order` (backfill
  desde `created_at`) y exponerlo
  en DTOs/eventos/proyección. Diseñar
  cómo aplicar renombres y borrados en ClickHouse, conservando el contrato del
  evento existente de creación de pedidos. Preparar eventos versionados CRUD
  para Customer y eventos de creación/cambio de estado para Order, y aplicarlos
  a ClickHouse. Añadir Kafka Connect con Debezium, configurar PostgreSQL para
  logical replication y registrar el conector Outbox Event Router.
- Componentes previstos: `db/migration/V2`, tabla Outbox, `Customer`, `Order`,
  repositorios, `OrderService`, adaptadores REST, eventos de dominio, ClickHouse
  consumer/writer, Kafka Connect/Debezium y configuración del connector.
- Criterios de aceptación: todos los pedidos existentes quedan asociados y la
  columna redundante `orders.customer` se elimina tras verificaciones;
  valores/nombres y cantidades se preservan; `statusOrder` se establece al
  crear y se actualiza al cambiar estado; crear por REST/GraphQL produce una
  relación válida o rechaza un nombre inexistente; JSON REST y evento mantienen
  el campo `customer` como texto; eventos de CRUD actualizan la proyección de
  ClickHouse de forma idempotente; los eventos existentes de creación siguen
  siendo compatibles;
  pruebas de consultas del promedio siguen funcionando.
- Pruebas previstas: integración con PostgreSQL de migración y backfill,
  unicidad/lookup de nombre, creación transaccional, regresión de REST y prueba
  de mapeo del evento Kafka.
- Riesgos: migración de una base persistente, cambios de serialización y
  necesidad de crear previamente cada cliente nuevo.
- Cierre: Flyway V2 se aplicó en la base persistente; Debezium y su tarea
  quedaron `RUNNING`; PostgreSQL y ClickHouse coincidieron en 13 Customers y
  15 Orders; `orders` se cambió a una vista sobre `orders_current`, conservando
  la tabla física como `orders_legacy`. La suite `mvn -B test` terminó con 28
  tests, 0 fallos y 0 errores. El README documenta el flujo y la operación.
- La verificación de ClickHouse fue manual sobre el entorno local; las pruebas
  automáticas de ClickHouse y el promedio también pasaron.
- Cierre histórico: la Fase 3 recibió autorización independiente el
  2026-10-26 y sus resultados están registrados abajo.

### Fase 3 — CRUD de Customer, ciclo de Order, relación GraphQL y estudio de N+1

- Estado: `completada y verificada el 2026-10-26`
- Objetivo: exponer operaciones GraphQL para administrar clientes, recorrer
  desde clientes a sus pedidos y entender cómo se resuelven campos anidados.
- Alcance: definir `Customer` y `Order` en SDL; añadir CRUD GraphQL de Customer
  y para Order solo consultas, creación y cambio de estado (`CREATED` a
  `DELIVERED`), resolver `Customer.orders`, validar nombre único, propagar
  renombres y permitir borrar Customer solo sin pedidos; conservar las
  operaciones existentes; aplicar
  `ORDER_READ` a consultas y `ORDER_WRITE` a mutaciones. Medir en una prueba
  controlada el patrón de consultas al cargar varios clientes y pedidos.
- Componentes previstos: `schema.graphqls`, resolver/controlador GraphQL,
  servicios y repositorios; `OrderResolverGraphQlTest` y pruebas de seguridad.
- Criterios de aceptación: el cliente puede ejecutar CRUD de Customer y
  consultar/crear Order y cambiar su estado; ningún pedido se borra; el borrado
  de Customer solo funciona si está vacío; el renombrado valida unicidad y
  actualiza todos los flujos acordados; `statusOrder` se inicializa al
  crear y cambia con el estado;
  puede solicitar
  `customers { name orders { id amount } }` en una sola operación; el resultado
  refleja la relación real; las operaciones actuales mantienen su contrato; se
  aplican los roles de lectura y escritura vigentes.
- Pruebas previstas: GraphQlTester con múltiples clientes y pedidos,
  autorización y evidencia de la cantidad de consultas que muestra N+1 antes
  de optimizar.
- Riesgos: resolver accidentalmente por nombre sin usar la relación persistida;
  consultas perezosas fuera de la transacción; acceso no autorizado a campos;
  consistencia entre mutación GraphQL, PostgreSQL, Kafka y ClickHouse.
- Condición de cierre: contrato anidado y comportamiento inicial demostrados.
- Implementación: SDL para `Customer`, `Order` y `OrderStatus`; consultas
  `customers`, `customerById`, `orders`, `orderById`; mutaciones CRUD de
  Customer, creación de Order y transición de estado. Se conservó el contrato
  textual de `Order.customer` y se añadió `statusOrder`.
- La relación `Customer.orders` usa la FK persistida y carga los pedidos por
  Customer. La prueba GraphQL consulta dos clientes con sus pedidos y verifica
  una invocación del servicio de pedidos por cada cliente. Esto demuestra el
  patrón inicial de N+1 en la ruta de resolución; no es un conteo de sentencias
  SQL mediante instrumentación.
- Seguridad: `ORDER_READ` en consultas y resolución de campos anidados;
  `ORDER_WRITE` en todas las mutaciones. Se añadieron pruebas de permisos para
  consultas y creación de Customers.
- Fechas: `DateTimeUtils` centraliza el formato ISO-8601 de `Instant` y el parseo
  de fechas con offset o sin zona (estas últimas se interpretan como UTC). Se
  reutiliza en GraphQL y en el consumidor de eventos Outbox.
- Verificación: `mvn -B test` completó 34 pruebas, 0 fallos y 0 errores; la
  inspección del SDL reportó cero campos, registros o argumentos sin resolver;
  `git diff --check` pasó. También se compiló el cambio de `DateTimeUtils` con
  `mvn -B -DskipTests compile`. Durante las pruebas Logback intentó resolver
  `logstash` desde el proceso local y emitió avisos DNS; no afectaron los
  resultados.
- Desviación controlada: los ejemplos completos de variables, aliases y
  fragments, junto con DataLoader y las pruebas por el Gateway, se completaron
  en la Fase 4.

### Fase 4 — Carga por lotes y laboratorio educativo

- Estado: `completada y verificada el 2026-10-26`
- Objetivo: mitigar N+1 y dejar ejercicios reproducibles para aprender GraphQL.
- Alcance: resolver `Customer.orders` con DataLoader explícito registrado en
  `BatchLoaderRegistry`; agrupar por IDs de Customer y cargar pedidos con una
  consulta acotada; añadir ejemplos de
  variables, aliases, fragments y operaciones anidadas; alinear prueba/README
  para usar el Gateway.
- Componentes previstos: resolver GraphQL, repositorio/servicio de carga por
  lotes, `OrderResolverGraphQlTest`, `SecurityConfigurationTest`, README de
  OrderApplication y prueba de ruta/documentación de ApiGateway si se mantiene
  dentro del alcance aprobado.
- Criterios de aceptación: la respuesta anidada es igual antes y después de
  optimizar; la carga no ejecuta una consulta de pedidos por cliente; las
  pruebas muestran el comportamiento agrupado; ejemplos Postman funcionan por
  `http://localhost:8080/api-gateway/graphql`; los permisos permanecen
  correctos.
- Pruebas previstas: integración PostgreSQL o verificación determinista del
  método batch; GraphQlTester para respuesta/errores; autorización; prueba del
  Gateway con backend simulado; suite Maven de ambos repositorios según alcance.
- Riesgos: contar consultas SQL de forma estable; enseñar que `@BatchMapping`
  es una abstracción de conveniencia sobre DataLoader sin usarla en la
  implementación; repositorios separados para OrderApplication y ApiGateway.
- Condición de cierre: mejora de lote demostrada, eventos y proyección
  ClickHouse verificados, ejemplos validados y compatibilidad comprobada.
- Implementación: `CustomerResolver` registra `ordersByCustomer` mediante
  `BatchLoaderRegistry`; `Customer.orders` encola cada ID y devuelve su future.
  Spring GraphQL mantiene el registro del DataLoader por petición GraphQL. El
  servicio consulta todos los IDs con `findAllByCustomerRecordIdIn`, agrupa los
  resultados por `customerId` y devuelve listas vacías para clientes sin
  pedidos. `@EntityGraph` carga también Customer en la misma consulta de Order.
- Pruebas: GraphQlTester pide tres clientes anidados, confirma una llamada al
  servicio batch con los tres IDs y comprueba el resultado de un cliente sin
  pedidos. `OrderServiceTest` verifica el agrupamiento y una sola invocación del
  repositorio. Seguridad prueba la consulta anidada con `ORDER_READ`.
- README: se documentan las operaciones por el Gateway, variables, aliases,
  fragments, CRUD de Customer, creación y entrega de Order, restricciones y la
  comparación educativa entre la línea base individual y el loader batch.
- Verificación: `mvn -B test` completó 35 pruebas, 0 fallos y 0 errores; el
  SDL no reportó campos ni argumentos sin resolver y `git diff --check` pasó.
- Resultado: la fase deja de ejecutar una consulta de pedidos por Customer;
  el lote se resuelve en una llamada de servicio/repositorio para las claves de
  la petición. No se añadió `@BatchMapping`; se implementó DataLoader explícito
  como pide el objetivo educativo.
- Pruebas E2E manuales 5–12: completadas el 2026-10-26 y confirmadas por el
  usuario. Se validaron aliases/fragments; creación, validación, renombre y
  borrado de Customer; creación y transición de Order; errores de negocio;
  sincronización de las vistas actuales de ClickHouse y versiones del pedido.
- En la prueba del pedido 46, ClickHouse reflejó el nombre de Customer
  actualizado y el estado `DELIVERED`; `order_versions` contiene solo la versión
  0 (`CREATED`) y la versión 1 (`DELIVERED`), incluso tras repetir la mutación.
- Se verificó que un Customer con pedidos no se borra y que un Customer sin
  pedidos sí se puede borrar y desaparece de `customers_current`.
- Los errores GraphQL de validación y conflicto exponen mensajes legibles y
  extensiones `code`/`httpStatus` (`BAD_REQUEST`/400 y `CONFLICT`/409).
- Verificación tras los últimos ajustes: compilación `mvn -q -DskipTests compile`
  correcta; el usuario confirmó que todas las pruebas E2E pasaron.

## Cierre de la iniciativa

El usuario confirmó el 2026-10-26 que todas las pruebas pasaron y autorizó dar
por terminada la iniciativa. Las fases 1–4 y sus criterios de aceptación quedan
cerrados. No hay trabajo pendiente dentro de esta iniciativa.
