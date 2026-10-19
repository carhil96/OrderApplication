# 004 — Promedio del importe de pedidos por rango de fechas: especificación

## Estado

`implementada; pendiente de cierre de iniciativa`

## Problema y contexto

La API permite consultar pedidos, pero no ofrece el promedio de sus importes en
un periodo. Los pedidos se almacenan en PostgreSQL y tienen un campo
`createdAt` de tipo `Instant`. El resultado debe poder solicitarse enviando el
rango de fechas en el payload.

## Resultado esperado y criterios de éxito

- Un cliente puede solicitar el promedio de `amount` de pedidos creados en un
  rango temporal mediante REST.
- PostgreSQL calcula el promedio con una consulta agregada y solo devuelve el
  valor agregado a la aplicación.
- El rango es semiabierto: incluye `from` y excluye `to` (`[from, to)`).
- El contrato representa el promedio como `averageAmount`; si no hay filas,
  devuelve `null`.
- Un rango con `from` igual o posterior a `to`, o fechas ausentes/malformadas,
  se rechaza como petición inválida (`400`).
- La operación está protegida con el permiso de lectura `ORDER_READ`.

## Alcance y exclusiones

Incluido:

- Consulta agregada JPA/PostgreSQL sobre `orders.amount` y `orders.created_at`.
- Endpoint `POST /api/orders/average-amount` con payload `from` y `to` como
  timestamps ISO-8601 con zona/offset, normalizados a `Instant`.
- Respuesta JSON con `averageAmount` decimal, nullable cuando no hay pedidos.
- Validación del intervalo y autorización de lectura.
- Pruebas de la consulta/servicio, contrato REST y reglas de autorización
  relevantes.

Excluido:

- Nuevas consultas GraphQL, gráficos o visualizaciones.
- Filtros por estado, cliente u otros campos.
- Cambios al modelo de datos, migraciones o a la persistencia ClickHouse.
- Cambios a los permisos de creación de pedidos.

## Restricciones, dependencias y supuestos

- PostgreSQL es la fuente de verdad síncrona de los pedidos; ClickHouse es una
  copia asíncrona y queda fuera de esta consulta.
- `created_at` es persistido por JPA a partir de `Order.createdAt`; el
  almacenamiento y la comparación temporal se normalizan a UTC.
- El endpoint usa `POST` para transportar el intervalo en el payload aunque la
  operación sea de lectura. La seguridad debe asignarle explícitamente
  `ORDER_READ`, evitando que herede `ORDER_WRITE` por la regla general de POST.
- GraphQL puede exponer consultas que los clientes usan para solicitar datos
  destinados a gráficos, pero no incluye una librería de cálculo ni genera
  visualizaciones. El cálculo del promedio es responsabilidad de PostgreSQL.

## Brainstorming y alternativas

### Alternativa 1 — Agregación en PostgreSQL

Una consulta agregada calcula `AVG(amount)` filtrando por `created_at`. Reduce
lecturas y transferencia de filas, y mantiene el cálculo cerca de la fuente de
datos. Es la alternativa seleccionada.

### Alternativa 2 — Cargar pedidos y promediar en Java

El repositorio recupera todos los pedidos del periodo y el servicio calcula el
promedio. Es directo, pero aumenta memoria, transferencia y tiempo conforme
crece el rango.

### Alternativa 3 — Consulta GraphQL

Añadir una query como `averageOrderAmount(from, to)` permitiría a clientes
GraphQL solicitar el dato dentro de su contrato actual. El cálculo seguiría
requiriendo PostgreSQL; GraphQL no aporta por sí mismo librerías analíticas ni
visualización. Se deja fuera del alcance inicial para mantener la interfaz
aprobada como REST.

## Comparación y recomendación

Se recomienda la alternativa 1: es eficiente para una operación agregada,
coherente con PostgreSQL como fuente autoritativa y evita trasladar filas
individuales a Java. La API inicial será REST. El endpoint de lectura usará
`POST` por el payload y recibirá `ORDER_READ` de forma explícita.

## Decisiones autorizadas

| Fecha | Decisión | Motivo | Autorización |
| --- | --- | --- | --- |
| 2026-10-19 | Calcular el promedio mediante una consulta agregada en PostgreSQL. | Evitar cargar filas individuales y mantener el cálculo junto a la fuente de verdad. | Usuario |
| 2026-10-19 | Usar un intervalo `[from, to)` con timestamps ISO-8601; devolver `null` cuando no haya pedidos y `400` para rango inválido. | Evitar doble conteo en límites de periodos consecutivos y hacer explícitos los casos sin datos/entrada inválida. | Usuario |
| 2026-10-19 | Crear la especificación y el plan inicial de la iniciativa 004. | Mantener trazabilidad y revisar las fases antes de implementar. | Usuario |
| 2026-10-19 | Aprobar el diseño detallado REST y registrar la implementación y sus resultados. | Confirmar el contrato del endpoint, el acceso de lectura y la evidencia de verificación. | Usuario |

## Implementación y verificación

- `OrderRepository` calcula `AVG(amount)` en PostgreSQL con `created_at >= from`
  y `created_at < to`; el resultado se representa con `BigDecimal`.
- `OrderService` expone la operación en una transacción de solo lectura.
- `POST /api/orders/average-amount` recibe `from` y `to` como `Instant` y
  devuelve `{"averageAmount": ...}`. Si no hay pedidos, `averageAmount` es
  `null`. Un extremo ausente o un intervalo vacío/invertido devuelve `400`.
- La ruta tiene una regla específica `ORDER_READ` antes de la regla general
  para POST; la creación de pedidos continúa requiriendo `ORDER_WRITE`.
- Se añadieron pruebas de integración PostgreSQL para el promedio, los límites
  `[from, to)` y el resultado nulo sin filas; también pruebas REST para
  respuesta, rango inválido y autorización.
- Evidencia: `mvn -B test` pasó con 28 pruebas, cero fallos y cero errores.
  `git diff --check` pasó. La suite usó PostgreSQL local y Docker para la prueba
  de integración existente de ClickHouse.
- Limitaciones: Logstash no estaba disponible durante las pruebas y emitió
  avisos de conexión, sin afectar sus resultados.

## Preguntas, riesgos y decisiones pendientes

- La API Gateway externa pertenece a otro proyecto; el endpoint directo de
  OrderApplication podría necesitar una regla de enrutamiento allí para quedar
  expuesto a través de `localhost:8080`.
- Si en el futuro se necesita una interfaz GraphQL, debe decidirse como alcance
  independiente y compartir el servicio de aplicación de agregación.
- La iniciativa queda pendiente de autorización explícita de cierre.
