# 001 — Seguridad OAuth2 y JWT: plan de desarrollo

## Referencia

- Spec: `docs/development/001-oauth2-jwt-security-spec.md`

## Estado general

`pausado`

La migración de plataforma (iniciativa 002) está completada y dejó una base
compatible con Spring Security 7: Java 21, Spring Boot 4.1.1 y PostgreSQL 17.11.
La iniciativa de seguridad sigue pausada: esta preparación no autoriza todavía
a diseñar ni implementar OAuth2/JWT, ni a desplegar Keycloak.

## Fases

### Fase 1 — Diseño de autorización y entorno de identidad

- Estado: `pendiente`
- Objetivo: concretar roles, flujos de token, exposición de endpoints y la
  configuración local de Keycloak.
- Alcance: diseño de la integración, versión del proveedor y criterios de
  protección para REST, GraphQL y Actuator.
- Componentes previstos: Docker Compose, configuración de Spring Security y
  artefacto de configuración de Keycloak.
- Criterios de aceptación: diseño verificable con roles, matriz de permisos y
  flujos de prueba definidos.
- Pruebas previstas: revisión de configuración y prueba manual del flujo de
  obtención de token documentado.
- Riesgos: exponer accidentalmente healthchecks o interfaces de desarrollo;
  escoger un flujo OAuth2 no adecuado para el cliente.
- Condición de cierre: diseño y plan de implementación autorizados.
- Resultado y evidencia: pendiente.
- Decisiones autorizadas: pendiente.

### Fase 2 — Validación de JWT y seguridad de endpoints

- Estado: `pendiente`
- Objetivo: configurar la API como Resource Server y exigir JWT válidos.
- Alcance: dependencias, configuración y reglas de seguridad HTTP.
- Componentes previstos: `pom.xml`, configuración Spring Security y pruebas de
  integración.
- Criterios de aceptación: una petición sin token recibe 401 y un token válido
  puede alcanzar solo los recursos autorizados.
- Pruebas previstas: integración para ausencia de token, token inválido y token
  válido.
- Riesgos: incompatibilidad entre versiones o mapeo incorrecto de claims.
- Condición de cierre: pruebas aprobadas y comportamiento verificado.
- Resultado y evidencia: pendiente.
- Decisiones autorizadas: pendiente.

### Fase 3 — Autorización por roles y configuración de Keycloak

- Estado: `pendiente`
- Objetivo: aplicar permisos de lectura/escritura y proporcionar identidades de
  demostración reproducibles.
- Alcance: roles, usuarios de prueba, cliente OAuth2 y políticas REST/GraphQL.
- Componentes previstos: configuración de Keycloak, Docker Compose, reglas de
  autorización y pruebas.
- Criterios de aceptación: un rol de lectura no puede crear pedidos; un rol de
  escritura puede realizar las operaciones definidas.
- Pruebas previstas: integración y guía manual con tokens reales de Keycloak.
- Riesgos: diferencias entre los claims de roles de Keycloak y las autoridades
  de Spring Security.
- Condición de cierre: matriz de permisos verificada para REST y GraphQL.
- Resultado y evidencia: pendiente.
- Decisiones autorizadas: pendiente.

### Fase 4 — Documentación operativa y cierre técnico

- Estado: `pendiente`
- Objetivo: dejar instrucciones de uso, límites y evidencia de pruebas.
- Alcance: README y documentación de la iniciativa.
- Componentes previstos: `README.md` y los documentos de esta iniciativa.
- Criterios de aceptación: una persona puede levantar el entorno, obtener un
  token y ejecutar los casos de acceso documentados.
- Pruebas previstas: recorrido manual reproducible y comprobaciones de build.
- Riesgos: documentación desalineada con la configuración final.
- Condición de cierre: documentación autorizada y evidencia registrada.
- Resultado y evidencia: pendiente.
- Decisiones autorizadas: pendiente.

## Siguiente acción que requiere autorización

Solicitar autorización explícita para reanudar la Fase 1 de esta iniciativa.
Al reanudarla, revisar primero la matriz de permisos, los flujos OAuth2/OIDC,
la exposición de GraphQL y Actuator, y una versión mantenida de Keycloak
compatible con el entorno. La spec describe el stack antiguo y deberá
actualizarse durante esa fase antes de implementar cambios.
