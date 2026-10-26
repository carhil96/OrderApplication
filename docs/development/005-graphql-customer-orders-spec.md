# 005 — Relación persistida Customer–Order para aprendizaje de GraphQL: especificación

## Estado

`Iniciativa completada y cerrada el 2026-10-26; fases 1–4 y pruebas E2E completadas`

## Problema y contexto

OrderApplication expone GraphQL mediante Spring for GraphQL. El schema incluye
consultas y mutaciones de Customer y Order, además de la relación anidada
`Customer.orders`. La Fase 3 demuestra la resolución individual de pedidos por
cliente como línea base. La Fase 4 añade DataLoader explícito para agrupar esa
carga por petición GraphQL.

Hechos del repositorio:

- `Order` persiste su relación mediante `customer_id`; su propiedad de
  compatibilidad `customer` devuelve el nombre de la relación y REST/GraphQL
  conservan `Order.customer: String`.
- GraphQL expone `customers`, `customerById`, `orders` y `orderById`, así como
  creación/renombre/borrado condicionado de Customer, creación de Order y
  transición de estado.
- Las consultas GraphQL requieren `ORDER_READ`; todas las mutaciones requieren
  `ORDER_WRITE`.
- El Gateway configura GraphQL en `/api-gateway/graphql`. El README de
  OrderApplication documenta esa ruta pública en el puerto 8080; la ruta
  directa del servicio es `/graphql` en el puerto 8082.

Hipótesis que orienta la propuesta:

- Los valores `customer` actuales se tratan como nombres de cliente para el
  ejercicio, aunque algunos parezcan etiquetas de pruebas. No son identificadores
  empresariales verificados.

## Resultado esperado y criterios de éxito

- PostgreSQL contiene clientes identificables y pedidos asociados mediante una
  relación uno-a-muchos: un cliente puede tener varios pedidos y cada pedido
  pertenece a un cliente.
- Una migración versionada conserva los pedidos y sus nombres actuales,
  creando inicialmente un cliente por cada valor de `customer` distinto de
  forma exacta.
- Un cliente GraphQL puede recorrer `Customer → orders` en una sola operación y
  seleccionar solo los campos que necesita.
- Los ejercicios muestran variables, selección de campos, aliases y fragments,
  y explican el rol de resolvers de campos anidados.
- El aprendizaje incluye una demostración del problema N+1 y su mitigación con
  un DataLoader explícito registrado mediante Spring for GraphQL.
- Las consultas GraphQL requieren `ORDER_READ`; las mutaciones de creación y
  estado de pedido y el CRUD de clientes requieren `ORDER_WRITE`.
- Se conserva el contrato actual de REST, GraphQL y el evento Kafka de pedido
  creado; se agregan eventos para Customer creado/actualizado/eliminado y Order
  creado/estado actualizado. No se publica un evento de borrado de Order.
- La consulta anidada se puede ejecutar desde Postman a través del Gateway y la
  suite relevante verifica contrato, permisos, migración y carga por lotes.

## Alcance y exclusiones

Incluido:

- Añadir la entidad y tabla `customers`, una clave foránea desde `orders` y una
  migración Flyway para respaldar y asociar los pedidos existentes.
- Representar en Java la relación `Customer` uno-a-muchos `Order`.
- Adaptar la creación de pedidos para buscar un cliente existente por nombre
  exacto y rechazar el pedido si no existe.
- Quitar el atributo `customer` de la entidad JPA `Order`; la FK a `Customer`
  será la única representación persistida de la relación. Mantener `customer`
  como texto en las respuestas REST y GraphQL existentes y en `OrderEvent`,
  obteniéndolo de `order.customer.name` mediante mapeos/DTOs.
- Añadir un tipo GraphQL `Customer`, consultas para obtener clientes, CRUD de
  clientes y el campo anidado `Customer.orders`.
- Exponer para `Order` consultas, creación y una mutación para cambiar solo el
  estado; no se borran pedidos ni se modifican sus otros atributos.
- Persistir `statusOrder`, inicializarlo al crear el pedido y actualizarlo
  cada vez que cambia el estado; exponerlo en REST, GraphQL, Kafka y ClickHouse.
- Proteger las operaciones GraphQL de lectura y de escritura con los roles
  actuales.
- Al renombrar un cliente, validar unicidad y propagar el nombre nuevo a todos
  los flujos que lo exponen o almacenan, incluyendo REST, GraphQL, Kafka y
  ClickHouse.
- Permitir borrar un cliente solo cuando no tenga pedidos asociados. Los
  pedidos son registros de seguimiento y nunca se borran.
- Generar eventos Kafka para crear, actualizar y eliminar clientes, y para
  crear pedidos y cambiar su estado; aplicar esos cambios en ClickHouse para
  que represente los datos vigentes de PostgreSQL.
- Añadir ejemplos y ejercicios de consultas y documentar el recorrido por
  Gateway.
- Verificar el N+1 y la solución por lotes con pruebas deterministas.

Excluido inicialmente:

- Promedios u otras operaciones analíticas; el promedio por fechas pertenece a
  la iniciativa 004 y REST.
- Gráficos, interfaz web, frontend o librerías de visualización.
- Datos personales adicionales como email, dirección o teléfono.
- Eliminar o cambiar las rutas REST actuales y rediseñar la tabla/copia
  analítica de ClickHouse, salvo los cambios de propagación necesarios para
  renombres y borrados definidos en esta iniciativa.
- Cargas masivas, federación GraphQL y subscriptions.
- Filtros complejos y paginación cursor-based; se pueden proponer como una
  iniciativa educativa posterior.

## Restricciones, dependencias y supuestos

- PostgreSQL es la persistencia autoritativa; Flyway administra cambios de
  esquema y Hibernate valida el esquema migrado.
- La publicación de cambios usará Transactional Outbox con Debezium CDC: la
  aplicación insertará el evento Outbox en la misma transacción PostgreSQL que
  la mutación; Kafka Connect/Debezium leerá el Outbox y lo publicará en Kafka.
  No se publicará directamente desde callbacks `@PostPersist`/`@PostUpdate`.
- El Compose actual tiene Kafka y PostgreSQL, pero no Kafka Connect/Debezium;
  PostgreSQL también debe habilitar logical replication para que el conector
  pueda leer el WAL.
- ClickHouse es una proyección asíncrona de PostgreSQL alimentada por Kafka;
  después de cada mutación puede existir un intervalo breve antes de que la
  proyección refleje el cambio.
- La migración parte del esquema V1 con `orders.customer VARCHAR(200) NOT NULL`.
  Añadirá `customers`, creará un cliente por cada nombre exacto distinto,
  asociará los pedidos, verificará conteos y ausencia de huérfanos, y luego
  eliminará `orders.customer` tras confirmar el backfill. Inicializará el nuevo
  `status_order` de los pedidos existentes con `created_at`. Debe ejecutarse
  primero en una copia de prueba y demostrar que preserva todos los pedidos
  antes de usarse sobre datos locales persistentes.
- La equivalencia de nombres será literal y sensible a mayúsculas/minúsculas;
  no se recortan espacios ni se normalizan acentos. El nombre único debe aceptar
  hasta 200 caracteres para representar los valores existentes sin pérdida.
- Kafka y ClickHouse esperan el nombre del cliente en texto. El cambio de modelo
  debe mapear la relación a ese texto al construir el evento y la respuesta de
  ClickHouse.
- El Gateway y OrderApplication son repositorios separados. La verificación
  HTTP usa `/api-gateway/graphql` en `localhost:8080`; la API directa conserva
  `/graphql` en `localhost:8082`.
- Se espera usar las capacidades de Spring for GraphQL ya incluidas. No se
  propone una nueva dependencia hasta que el diseño confirme que es necesaria.

## Estado de implementación

### Fase 3 completada — 2026-10-26

- `schema.graphqls` define `Customer`, `Order`, `OrderStatus`, las consultas y
  mutaciones aprobadas; se mantienen los campos de Order existentes y se añade
  `statusOrder`.
- `CustomerResolver` implementa consultas, CRUD y `Customer.orders`.
  `OrderResolver` implementa consultas, creación y actualización de estado.
  La lógica de unicidad de nombre, nombre no vacío, borrado solo sin pedidos y
  transición única `CREATED → DELIVERED` permanece en los servicios de dominio.
- En la implementación de Fase 3, `Customer.orders` invocaba el servicio por
  cada Customer. GraphQlTester comprobó esa respuesta para dos clientes; la
  prueba no contaba sentencias SQL directamente. La Fase 4 reemplazó esa ruta
  por DataLoader.
- Las pruebas de autorización cubren `ORDER_READ` para lectura de Customer y
  `ORDER_WRITE` para creación de Customer, además de las operaciones Order
  existentes.
- `DateTimeUtils` concentra las conversiones de `Instant` a ISO-8601 y el parseo
  de valores con offset o timestamp local interpretado como UTC. GraphQL y
  `KafkaConsumer` comparten la utilidad.
- Verificación: `mvn -B test` — 34 pruebas, 0 fallos, 0 errores; compilación
  adicional de la utilidad con `mvn -B -DskipTests compile`; `git diff --check`
  limpio. Logback emitió avisos de resolución DNS de `logstash` al ejecutarse
  las pruebas desde el host local, sin fallos asociados.

### Fase 4 completada — 2026-10-26

- `CustomerResolver` registra un DataLoader llamado `ordersByCustomer` en
  `BatchLoaderRegistry`. El resolver de `Customer.orders` encola el ID de cada
  padre; el loader batch consulta todos los IDs en una operación de repositorio.
- `OrderService.findOrdersByCustomerIds` agrupa los pedidos por Customer y
  devuelve también una lista vacía para cada clave sin pedidos. El repositorio
  usa `@EntityGraph` para traer la relación Customer junto con los pedidos.
- La prueba GraphQL verifica tres clientes y una sola llamada batch para sus
  IDs, incluyendo el caso sin pedidos. La prueba del servicio valida la
  agrupación y una única llamada al repositorio. La prueba de seguridad ejecuta
  la relación anidada con `ORDER_READ`.
- El README ahora incluye ejemplos por Gateway de variables, aliases, fragments,
  consultas anidadas y mutaciones. Explica que DataLoader mantiene la respuesta
  GraphQL mientras agrupa la carga de pedidos por request.
- Verificación final: `mvn -B test` — 35 pruebas, 0 fallos y 0 errores; el
  schema GraphQL reportó cero mappings pendientes; `git diff --check` pasó.
- No se usó `@BatchMapping`: se eligió DataLoader explícito como decisión
  educativa autorizada. La prueba valida las invocaciones de servicio/repositorio
  y la respuesta, no registra métricas SQL a bajo nivel.

### Cierre y pruebas E2E — 2026-10-26

- El usuario confirmó que todas las pruebas E2E seleccionadas (5–12) pasaron y
  autorizó cerrar la iniciativa.
- Se validaron consultas con aliases y fragments; creación de Customer,
  unicidad, rechazo de nombre vacío, renombre y rechazo de nombre duplicado;
  creación de Order y transición `CREATED → DELIVERED`.
- Repetir la transición devuelve `CONFLICT`/409 con el mensaje
  `order is already DELIVERED`; el pedido mantiene `statusOrder` y no se genera
  una versión/evento adicional.
- El borrado de Customer con pedidos devuelve `CONFLICT`/409; el borrado de un
  Customer sin pedidos tiene éxito.
- ClickHouse reflejó el renombre del Customer y la entrega del pedido 46; el
  Customer eliminado no aparece en `customers_current`. `order_versions`
  contiene exactamente las versiones 0 (`CREATED`) y 1 (`DELIVERED`) del pedido.
- Los errores GraphQL de validación y conflicto informan mensaje, clasificación,
  `code` y `httpStatus`; se observaron `BAD_REQUEST`/400 para nombre vacío y
  `CONFLICT`/409 para nombre duplicado y transiciones no permitidas.
- Tras los ajustes finales, `mvn -q -DskipTests compile` terminó correctamente.
  La suite Maven registrada en Fase 4 reportó 35 pruebas, 0 fallos y 0 errores;
  el usuario confirmó que todos los tests E2E manuales pasaron.

## Estado de cierre

La iniciativa 005 queda completada. No quedan fases ni pruebas E2E pendientes.

## Brainstorming y alternativas

### Alternativa 1 — Solo ejercicios sobre el schema actual

Preparar consultas con variables, aliases, fragments, selección de campos y la
mutación existente. No cambia modelo ni aplicación. Tiene coste muy bajo y
enseña el lenguaje de consultas, pero no permite recorrer una relación de
negocio ni estudiar la resolución de campos anidados.

### Alternativa 2 — Tipo Customer GraphQL derivado de los pedidos

Construir un objeto de consulta `Customer` agrupando filas por el texto actual,
sin entidad ni tabla persistida. Permite enseñar relaciones y N+1 con impacto
bajo, pero representa la relación solo en la capa GraphQL y no modela la
identidad en el dominio.

### Alternativa 3 — Entidad Customer persistida y relacionada con Order

Crear una tabla `customers`, migrar los valores existentes, asociar cada pedido
mediante clave foránea y exponer la relación en GraphQL. Enseña tanto el modelo
de datos como el grafo GraphQL y los resolvers; también exige compatibilidad
explícita con REST, Kafka y ClickHouse.

## Comparación y recomendación

Se recomienda la alternativa 3, elegida por el usuario para revisión de la
conceptualización. Es la opción que mejor cubre el objetivo educativo práctico:
la relación existe en PostgreSQL y en el modelo, y GraphQL ofrece una vista
anidada sobre ella. El coste y riesgo son mayores porque el cambio cruza
migraciones, persistencia, serialización REST, publicación Kafka y pruebas.

Para limitar el riesgo, el diseño propone mantener nombres y contratos
externos; introducir la tabla mediante una migración hacia adelante; y probar
los resolvers por fases. La consulta GraphQL inicial y su resolución pueden
mostrar el N+1 en una demostración acotada. La iniciativa no se consideraría
lista para cierre hasta que la ruta final use carga por lotes y se hayan
verificado las consultas.

La documentación distingue la lección conceptual del código final: GraphQL
describe los tipos y permite a cada cliente seleccionar campos y recorrer
relaciones; PostgreSQL persiste los datos y Spring for GraphQL resuelve los
campos. GraphQL no calcula métricas por sí mismo ni produce gráficos.

## Decisiones autorizadas

| Fecha | Decisión | Motivo | Autorización |
| --- | --- | --- | --- |
| 2026-10-19 | Preferir una relación Customer–Order persistida frente a una proyección solo GraphQL o ejercicios únicamente sintácticos. | El usuario considera más atractiva la alternativa persistida para el aprendizaje práctico. | Usuario |
| 2026-10-19 | Crear un cliente inicial por cada nombre `customer` distinto, con comparación exacta. | Conservar el valor actual sin inventar reglas de normalización y poder asociar los pedidos existentes. | Usuario |
| 2026-10-19 | Preparar la conceptualización en los documentos 005 de spec y plan. | El usuario quiere revisar y modificar la propuesta antes de aprobar fases o código. | Usuario |
| 2026-10-20 | `Customer.name` debe ser único; crear pedidos requiere un cliente ya existente y rechaza nombres desconocidos. | Evitar identidades duplicadas y mantener explícita la creación de clientes. | Usuario |
| 2026-10-20 | Eliminar `Order.customer` de la entidad y usar la relación/FK como única representación persistida; conservar `customer` textual en contratos externos actuales. | Evitar almacenar el nombre duplicado en `orders` sin romper REST, GraphQL o Kafka. | Usuario |
| 2026-10-20 | Exponer `customers` y `customerById`; conservar las filas actuales incluyendo nombres de prueba; medir N+1 frente a carga por lotes. | Respuestas a las preguntas de diseño. | Usuario |
| 2026-10-20 | Incluir CRUD GraphQL de Customer. | Debe poder crearse un cliente antes de asociarlo a pedidos. | Usuario |
| 2026-10-20 | Un pedido no se elimina; se conserva como registro de seguimiento. Customer se puede borrar solo si no tiene pedidos. | Preservar el historial de seguimiento y evitar borrar pedidos asociados. | Usuario |
| 2026-10-20 | Al renombrar Customer, validar que el nombre nuevo no esté usado y actualizar todos los flujos que manejan el nombre. | Mantener consistente el nombre en las representaciones derivadas. | Usuario |
| 2026-10-20 | Usar DataLoader explícito para la carga por lotes de `Customer.orders`. | El objetivo educativo incluye aprender el mecanismo DataLoader directamente. | Usuario |
| 2026-10-20 | Order expone consultas, creación y actualización únicamente del estado; no se ofrece CRUD completo ni borrado. Los estados son `CREATED` y `DELIVERED`; se agrega `statusOrder`. | Mantener el pedido como registro de seguimiento y consultar cuándo cambió su estado. | Usuario |
| 2026-10-20 | Producir eventos Kafka para cambios de Customer y creación/cambio de estado de Order, y reflejarlos en ClickHouse. | Mantener ClickHouse como proyección de los datos vigentes de PostgreSQL. | Usuario |
| 2026-10-20 | Leer el Transactional Outbox mediante Debezium CDC y publicar sus eventos en Kafka. | El usuario prefiere Debezium para capturar y publicar los cambios confirmados. | Usuario |
| 2026-10-20 | La transición de Order es únicamente `CREATED → DELIVERED`; el campo temporal se llama `statusOrder`. | Registrar las decisiones de cierre de Fase 1. | Usuario |
| 2026-10-20 | Rechazar nombres de Customer vacíos o compuestos solo por espacios. | Evitar identidades sin nombre. | Usuario |
| 2026-10-20 | Borrar físicamente Customer solo cuando no tenga pedidos asociados. | Preservar los pedidos como historial no eliminable. | Usuario |
| 2026-10-20 | Aprobar el diseño de Fase 1. | Autorizar cierre de diseño y revisión para solicitar Fase 2 por separado. | Usuario |
| 2026-10-20 | Completar la Fase 2 de persistencia y sincronización. | Flyway V2 aplicada; Debezium `RUNNING`; conteos PostgreSQL/ClickHouse coincidentes (13 clientes, 15 pedidos); corte a `orders_current` con tabla física preservada como `orders_legacy`; suite Maven con 28 tests, 0 fallos y 0 errores; README operativo actualizado. | Usuario |
| 2026-10-26 | Implementar la Fase 3 del contrato GraphQL y dejar visible el patrón inicial de resolución N+1. | El usuario autorizó ejecutar la fase; se completaron resolvers, SDL, pruebas de relación y seguridad, con 34 tests pasando. | Usuario |
| 2026-10-26 | Centralizar conversiones de fecha en `DateTimeUtils`. | Evitar duplicar lógica de formato/parsing entre GraphQL y Kafka; conservar ISO-8601 y la interpretación UTC de timestamps sin zona. | Usuario |
| 2026-10-26 | Implementar la Fase 4 con DataLoader explícito para agrupar `Customer.orders`, junto con pruebas y ejemplos GraphQL. | El usuario autorizó la Fase 4; la suite terminó con 35 pruebas, 0 fallos y 0 errores. | Usuario |
| 2026-10-26 | Traducir errores de negocio a errores GraphQL comprensibles y rechazar con conflicto la repetición de la transición a `DELIVERED`. | Se verificaron manualmente mensajes legibles y el rechazo sin modificación ni evento duplicado. | Usuario |
| 2026-10-26 | Cerrar la iniciativa tras completar las pruebas E2E 5–12. | El usuario confirmó que todos los tests pasaron y autorizó completar las fases y pruebas. | Usuario |

## Riesgos residuales y consideraciones operativas

- Los valores actuales son cadenas libres y parecen incluir etiquetas de prueba;
  agrupar coincidencias exactas puede no corresponder a identidades reales.
- Los pedidos con cliente desconocido se rechazarán; el CRUD GraphQL permitirá
  crear primero ese cliente. Las mutaciones de cliente usarán `ORDER_WRITE`.
- El nombre se representa en REST, GraphQL, eventos Kafka y ClickHouse. Los
  eventos de actualización deben mantener esas representaciones alineadas al
  renombrar un cliente o cambiar un pedido de cliente.
- Como los pedidos no se borran, un cliente con cualquier pedido asociado no se
  puede borrar físicamente, incluso si sus pedidos están entregados. La
  mutación `deleteCustomer` solo se permite si no existen pedidos asociados.
- Orders se consultan y crean; el único update permitido cambia `CREATED` a
  `DELIVERED`. No se borra ni se actualiza amount/customer.
- `status_order` se establece al crear el pedido y se actualiza en la
  transición de estado. Se expone como `statusOrder` en GraphQL
  y usar el mismo instante inicial que `createdAt`.
  Se recomienda permitir solo la transición `CREATED → DELIVERED`, sin revertir
  el estado.
- La aplicación insertará el evento de dominio en el outbox dentro de la
  transacción que modifica Customer u Order; Debezium capturará esa fila desde
  el WAL y la publicará en Kafka. El consumidor debe ser idempotente y también
  se deben definir orden/versionado de eventos y recuperación/reconciliación.
- Kafka Connect/Debezium quedó configurado en Compose con logical replication,
  publication, replication slot y Outbox Event Router. El conector lee la tabla
  Outbox y publica los eventos de Customer y Order en topics separados.
- ClickHouse ahora mantiene tablas versionadas para pedidos y clientes, además
  de las vistas `orders_current` y `customers_current`. La vista compatible
  `orders` se cambió a la proyección actual y la tabla física anterior se
  conservó como `orders_legacy`. La propagación es asíncrona.
- La comparación exacta y unicidad del nombre se implementaron con collation
  `C`, restricción de base de datos y validación de nombre no vacío. La
  migración aborta si encuentra valores legacy incompatibles.
- Al quitar `orders.customer`, REST, el `Order.customer` existente y los
  eventos Outbox conservan el nombre mediante mapeo desde la relación
  `Customer`; los tests de regresión pasaron.
- La Fase 3 registró la carga individual por Customer como línea base; en la
  Fase 4 se sustituyó por DataLoader. Las pruebas muestran una invocación batch
  para varias claves y cobertura de listas vacías. No se agregó instrumentación
  de SQL, por lo que el conteo directo de sentencias queda como posible ejercicio
  futuro si se necesita una medición física adicional.
- La ruta pública configurada es `/api-gateway/graphql`. El README de
  OrderApplication ya se actualizó para reflejarla; ApiGateway sigue siendo un
  repositorio separado y cualquier README o prueba allí requiere un cambio en
  ese repositorio.
