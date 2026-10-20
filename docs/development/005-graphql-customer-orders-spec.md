# 005 — Relación persistida Customer–Order para aprendizaje de GraphQL: especificación

## Estado

`propuesta; pendiente de revisión y aprobación del usuario`

## Problema y contexto

OrderApplication ya expone GraphQL mediante Spring for GraphQL. El schema
actual ofrece `orders`, `orderById` y `createOrder`, pero `Order` es un tipo
plano: `customer` es texto y no existe un tipo o entidad `Customer`. El esquema
permite practicar selecciones de campos, pero todavía no muestra cómo un
cliente recorre relaciones ni cómo el servidor resuelve campos anidados.

Hechos del repositorio:

- `Order.customer` es actualmente un `String`; la columna `orders.customer` es
  obligatoria y tiene longitud máxima de 200 caracteres.
- REST devuelve la entidad `Order`; GraphQL expone `Order.customer: String`.
- `OrderService` publica por Kafka un evento con `customer` como texto; el
  consumidor conserva ese valor al escribir la copia de pedidos en ClickHouse.
- Las consultas GraphQL aplican `ORDER_READ` y la mutación `createOrder` aplica
  `ORDER_WRITE`.
- El Gateway configura GraphQL en `/api-gateway/graphql`. El README de
  OrderApplication aún muestra ejemplos con `/graphql` en el puerto 8080; la
  ruta directa del servicio es `/graphql` en el puerto 8082.

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
- El aprendizaje incluye una demostración del problema N+1 y de su mitigación
  mediante carga por lotes con DataLoader de Spring for GraphQL, preferiblemente
  con `@BatchMapping` si la compatibilidad de la versión lo confirma.
- Las consultas nuevas requieren `ORDER_READ`; las mutaciones de pedidos
  existentes conservan `ORDER_WRITE`.
- Se conserva el contrato observable actual de REST, GraphQL y el evento Kafka
  durante la migración, salvo cambios aprobados expresamente.
- La consulta anidada se puede ejecutar desde Postman a través del Gateway y la
  suite relevante verifica contrato, permisos, migración y carga por lotes.

## Alcance y exclusiones

Incluido:

- Añadir la entidad y tabla `customers`, una clave foránea desde `orders` y una
  migración Flyway para respaldar y asociar los pedidos existentes.
- Representar en Java la relación `Customer` uno-a-muchos `Order`.
- Adaptar la creación de pedidos para asociarlos con un cliente identificado
  por nombre exacto, según la decisión de identidad registrada abajo.
- Mantener el valor `customer` como texto en las respuestas REST y GraphQL
  existentes y en `OrderEvent`, mediante adaptadores/DTOs cuando haga falta.
- Añadir un tipo GraphQL `Customer`, consultas para obtener clientes y el campo
  anidado `Customer.orders`.
- Proteger las operaciones GraphQL de lectura y de escritura con los roles
  actuales.
- Añadir ejemplos y ejercicios de consultas y documentar el recorrido por
  Gateway.
- Verificar el N+1 y la solución por lotes con pruebas deterministas.

Excluido inicialmente:

- Promedios u otras operaciones analíticas; el promedio por fechas pertenece a
  la iniciativa 004 y REST.
- Gráficos, interfaz web, frontend o librerías de visualización.
- Datos personales adicionales como email, dirección o teléfono.
- Eliminar o cambiar las rutas REST actuales, cambiar el formato del evento
  Kafka o rediseñar la tabla/copia analítica de ClickHouse.
- Borrado de clientes, cargas masivas, federación GraphQL y subscriptions.
- Filtros complejos y paginación cursor-based; se pueden proponer como una
  iniciativa educativa posterior.

## Restricciones, dependencias y supuestos

- PostgreSQL es la persistencia autoritativa; Flyway administra cambios de
  esquema y Hibernate valida el esquema migrado.
- La migración parte del esquema V1 con `orders.customer VARCHAR(200) NOT NULL`.
  Debe ejecutarse primero en una copia de prueba y demostrar que preserva todos
  los pedidos antes de usarse sobre datos locales persistentes.
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

## Riesgos, preguntas y decisiones pendientes

- Los valores actuales son cadenas libres y parecen incluir etiquetas de prueba;
  agrupar coincidencias exactas puede no corresponder a identidades reales.
- Para pedidos nuevos, falta confirmar si el servicio debe buscar o crear un
  cliente automáticamente cuando recibe un nombre que aún no existe, o si se
  debe introducir un identificador de cliente y cambiar los contratos de
  escritura.
- Una restricción única por nombre exacto puede producir conflictos de
  concurrencia al crear simultáneamente el primer pedido de un cliente.
- Cambiar la entidad JPA `Order` puede alterar por accidente JSON REST y el
  schema GraphQL existentes. Se requieren pruebas de compatibilidad y DTOs o
  mapeos explícitos.
- La relación podría disparar consultas N+1. `@BatchMapping` simplifica el
  camino común de carga por lotes; el diseño debe verificar cómo agrupar los
  pedidos por claves de cliente en una consulta.
- Falta elegir el nombre y forma exacta de las operaciones GraphQL, por ejemplo
  `customers` y `customerById` o `customerByName`, así como la nulabilidad del
  schema.
- El README de OrderApplication y una prueba de ApiGateway usan `/graphql` como
  path público, pero la configuración de Gateway declara
  `/api-gateway/graphql`. La iniciativa propone alinear las instrucciones y la
  prueba con la ruta pública vigente, o documentar explícitamente ambas rutas.
- La iniciativa no está aprobada para ejecución. El usuario revisará esta
  propuesta y podrá modificarla antes de aprobar el diseño y cada fase.
