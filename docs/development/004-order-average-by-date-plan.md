# 004 — Promedio del importe de pedidos por rango de fechas: plan de desarrollo

## Referencia

- Spec: `docs/development/004-order-average-by-date-spec.md`

## Estado general

`implementación completada; pendiente de cierre de iniciativa`

El usuario aprobó el diseño detallado y autorizó registrar los resultados el
2026-10-19.

## Fases propuestas

### Fase 1 — Diseño del contrato y persistencia

- Estado: `completada`
- Objetivo: concretar el contrato REST, la semántica de promedio y validación,
  la consulta JPA y la autorización.
- Alcance: fijar DTO de entrada/salida, formato de fechas, precisión decimal,
  respuesta sin resultados, errores `400`, permisos y posible exposición por
  Gateway.
- Componentes previstos: `RestOrderController`, `OrderService`,
  `OrderRepository`, DTOs, `SecurityConfiguration` y Gateway si se confirma que
  forma parte de este workspace.
- Criterios de aceptación: contrato documentado con ejemplos; semántica
  temporal y precisión acordadas; plan de pruebas delimitado.
- Pruebas previstas: diseño de casos para límites inclusivos/exclusivos,
  intervalo vacío, validación y permisos.
- Riesgos: conversión de zona horaria, precisión de `AVG(NUMERIC)` y alcance
  limitado al proyecto de OrderApplication.
- Condición de cierre: diseño aprobado por el usuario.
- Resultado: contrato REST definido con payload `from`/`to` como timestamps
  ISO-8601, intervalo `[from, to)`, respuesta nullable, `400` para rango
  inválido y permiso `ORDER_READ`. La agregación queda en PostgreSQL.
- Decisiones autorizadas: PostgreSQL como motor de agregación; REST como
  interfaz inicial; GraphQL y ClickHouse excluidos de esta iniciativa.
- Condición de cierre cumplida: el usuario aprobó el diseño el 2026-10-19.

### Fase 2 — Implementar y verificar el promedio REST

- Estado: `completada`
- Objetivo: entregar la consulta de promedio siguiendo el diseño aprobado.
- Alcance: añadir la agregación en repositorio, servicio, DTOs, endpoint,
  validación y autorización explícita `ORDER_READ`.
- Componentes previstos: `OrderRepository`, `OrderService`,
  `RestOrderController`, DTOs y `SecurityConfiguration`.
- Criterios de aceptación: promedio correcto y decimal; inicio incluido/final
  excluido; `null` sin resultados; `400` en rango inválido; lectura autorizada
  por `ORDER_READ`; creación de pedidos conserva `ORDER_WRITE`.
- Pruebas ejecutadas: integración PostgreSQL del promedio y límites temporales,
  promedio `null` sin filas, respuesta REST, rango inválido y autorización.
- Riesgos: diferencias del tipo devuelto por PostgreSQL y cobertura de
  validación/error handling.
- Resultado y evidencia: `mvn -B test` pasó con 28 pruebas, cero fallos y cero
  errores; `git diff --check` pasó. La suite usó PostgreSQL local y Docker para
  la prueba de ClickHouse. Logstash no estaba disponible y emitió avisos sin
  afectar los resultados.
- Desviación: la implementación se realizó antes de la aprobación formal del
  diseño detallado. El usuario aprobó posteriormente el diseño y autorizó
  registrar los resultados el 2026-10-19.
- Condición de cierre cumplida: pruebas relevantes pasan y el diff se revisó.
- Siguiente acción que requiere autorización: cierre formal de la iniciativa.

## Cierre

La implementación está completa. La iniciativa no se declara cerrada hasta que
el usuario autorice el cierre.
