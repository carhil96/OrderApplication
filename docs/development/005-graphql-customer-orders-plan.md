# 005 — Relación persistida Customer–Order para aprendizaje de GraphQL: plan

## Referencia

- Spec: `docs/development/005-graphql-customer-orders-spec.md`

## Estado general

`propuesta; pendiente de revisión y aprobación del usuario`

Este plan contiene fases propuestas. No hay aprobación para ejecutar código,
migraciones, pruebas o cambios de configuración.

## Fases propuestas

### Fase 1 — Contrato de identidad y compatibilidad de migración

- Estado: `pendiente`
- Objetivo: cerrar el diseño del modelo persistido Customer–Order, la identidad
  por nombre y la compatibilidad de los contratos actuales.
- Alcance: confirmar búsqueda/creación de clientes para pedidos nuevos, esquema
  de `customers`, clave foránea `orders.customer_id`, backfill exacto, política
  de nulabilidad, respuesta REST, schema GraphQL actual y evento Kafka.
- Componentes previstos: entidades `Customer`/`Order`, repositorios, servicio de
  pedidos, DTOs REST, `OrderEvent`, migraciones Flyway y schema/resolvers
  actuales, únicamente como áreas de diseño.
- Criterios de aceptación: plan de migración reversible o con recuperación
  documentada; nombres existentes preservados byte a byte dentro del texto;
  cero pedidos huérfanos; contratos de lectura/escritura actuales identificados.
- Pruebas previstas: plan para migración desde V1, pedidos con nombres
  repetidos, límites de longitud, nombre vacío/null y compatibilidad de REST,
  GraphQL y evento.
- Riesgos: identidad por texto libre, carreras al crear clientes y coste de
  mantener compatibilidad externa.
- Condición de cierre: diseño detallado revisado y aprobado por el usuario.
- Siguiente acción que requiere autorización: aprobar el diseño de migración y
  comenzar esta fase.

### Fase 2 — Persistencia Customer–Order y backfill

- Estado: `pendiente`
- Objetivo: persistir clientes y relacionar los pedidos existentes sin perder
  información ni cambiar contratos externos.
- Alcance: migración Flyway; entidad/repositorio Customer; relación JPA; carga o
  creación transaccional de Customer para pedidos nuevos; mapeo a nombre de
  cliente para REST y `OrderEvent`.
- Componentes previstos: `db/migration/V2`, `Customer`, `Order`, repositorios,
  `OrderService`, adaptadores REST, `OrderEvent`, `ClickHouseWriter` solo si su
  contrato observable lo requiere y pruebas afectadas.
- Criterios de aceptación: todos los pedidos existentes quedan asociados;
  valores/nombres y cantidades se preservan; crear por REST/GraphQL produce una
  relación válida; JSON REST y evento mantienen el campo `customer` como texto;
  pruebas de consultas del promedio siguen funcionando.
- Pruebas previstas: integración con PostgreSQL de migración y backfill,
  unicidad/lookup de nombre, creación transaccional, regresión de REST y prueba
  de mapeo del evento Kafka.
- Riesgos: migración de una base persistente, cambios de serialización y
  concurrencia al buscar o crear por nombre.
- Condición de cierre: migración y regresiones verificadas sobre una base de
  prueba; no ejecutar sobre datos persistentes sin autorización explícita.
- Siguiente acción que requiere autorización: iniciar implementación después
  del cierre aprobado de la Fase 1.

### Fase 3 — Relación GraphQL y estudio de N+1

- Estado: `pendiente`
- Objetivo: permitir recorrer desde clientes a sus pedidos y entender cómo se
  resuelven campos anidados.
- Alcance: definir `Customer` en SDL; añadir consultas de clientes y resolver
  `Customer.orders`; conservar las operaciones existentes; aplicar `ORDER_READ`.
  Medir en una prueba controlada el patrón de consultas al cargar varios
  clientes y pedidos.
- Componentes previstos: `schema.graphqls`, resolver/controlador GraphQL,
  servicio y repositorio; `OrderResolverGraphQlTest` y pruebas de seguridad.
- Criterios de aceptación: el cliente solicita `customers { name orders { id
  amount } }` en una sola operación; el resultado refleja la relación real;
  las operaciones actuales mantienen su contrato; un token sin `ORDER_READ` no
  accede a la consulta.
- Pruebas previstas: GraphQlTester con múltiples clientes y pedidos,
  autorización y evidencia de la cantidad de consultas que muestra N+1 antes
  de optimizar.
- Riesgos: resolver accidentalmente por nombre sin usar la relación persistida;
  consultas perezosas fuera de la transacción y acceso no autorizado a campos.
- Condición de cierre: contrato anidado y comportamiento inicial demostrados.
- Siguiente acción que requiere autorización: ejecutar esta fase tras autorizar
  de forma independiente la Fase 2.

### Fase 4 — Carga por lotes y laboratorio educativo

- Estado: `pendiente`
- Objetivo: mitigar N+1 y dejar ejercicios reproducibles para aprender GraphQL.
- Alcance: resolver `Customer.orders` mediante DataLoader con
  `@BatchMapping` si la versión instalada lo soporta adecuadamente; agrupar por
  IDs de Customer y cargar pedidos con una consulta acotada; añadir ejemplos de
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
- Riesgos: contar consultas SQL de forma estable; diferencia entre
  `@BatchMapping` y el DataLoader explícito; repositorios separados para
  OrderApplication y ApiGateway.
- Condición de cierre: mejora de lote demostrada, ejemplos validados y
  compatibilidad verificada.
- Siguiente acción que requiere autorización: iniciar esta fase tras aprobar
  independientemente la Fase 3.

## Siguiente acción que requiere autorización

Revisar esta conceptualización, modificar los criterios o riesgos que se
consideren necesarios y aprobar explícitamente el diseño antes de iniciar la
Fase 1. Cada fase posterior requerirá autorización independiente.
