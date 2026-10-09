# 001 — Seguridad OAuth2 y JWT: plan de desarrollo

## Referencia

- Spec: `docs/development/001-oauth2-jwt-security-spec.md`

## Estado general

`cerrada — 2026-10-08`

La iniciativa 002 conserva sus cambios implementados, con fases 2 a 5 en revisión
retrospectiva por falta de evidencia TDD. El diseño de esta iniciativa fue
aprobado el 2026-10-07; la Fase 2 de OAuth/JWT fue rectificada y verificada el
2026-10-08.

## Fases

### Fase 1 — Diseño de autorización y entorno de identidad

- Estado: `completada`
- Objetivo: concretar roles, flujos de token, exposición de endpoints y la
  configuración local de Keycloak.
- Alcance: diseño de la integración, versión del proveedor y criterios de
  protección para REST, GraphQL y Actuator.
- Componentes previstos: Docker Compose, configuración de Spring Security y
  artefacto de configuración de Keycloak.
- Criterios de aceptación: diseño verificable con roles, matriz de permisos y
  flujos de prueba definidos. Cumplido según el diseño autorizado registrado
  en la spec.
- Pruebas previstas: revisión de configuración y prueba manual del flujo de
  obtención de token documentado.
- Riesgos: exponer accidentalmente healthchecks o interfaces de desarrollo;
  escoger un flujo OAuth2 no adecuado para el cliente.
- Condición de cierre: diseño y plan de implementación autorizados.
- Resultado y evidencia: diseño aprobado por el usuario el 2026-10-07; decisiones
  y matriz de permisos registradas en `001-oauth2-jwt-security-spec.md`. No se
  modificó código ni se desplegó Keycloak en esta fase.
- Decisiones autorizadas: Keycloak 26.7.5 local; Authorization Code con PKCE;
  sin Direct Access Grants; Client Credentials diferido; roles ORDER_READ y
  ORDER_WRITE con matriz REST/GraphQL; health público, GraphiQL deshabilitado.

### Fase 2 — Validación de JWT y seguridad de endpoints

- Estado: `completada`
- Objetivo: configurar la API como Resource Server y exigir JWT válidos.
- Alcance: dependencias, configuración y reglas de seguridad HTTP.
- Componentes previstos: `pom.xml`, configuración Spring Security y pruebas de
  integración.
- Criterios de aceptación: una petición sin token recibe 401 y un token válido
  puede alcanzar solo los recursos autenticados en esta fase. Verificado para
  REST, GraphQL HTTP y health público.
- Pruebas previstas: integración para ausencia de token, token inválido y token
  válido.
- Riesgos: incompatibilidad entre versiones o mapeo incorrecto de claims.
- Condición de cierre: pruebas aprobadas y comportamiento verificado.
- Resultado y evidencia: auditoría del 2026-10-08 identificó ausencia de ciclo
  TDD en la ejecución original. Rectificación TDD: `JwtDecoderValidationTest`
  se escribió primero; en rojo, `rejectsJwtWithUnexpectedIssuer` falló porque
  el decoder aceptaba un emisor distinto. Se restauró
  `JwtValidators.createDefaultWithIssuer`; en verde pasan las 4 pruebas del
  decoder (firma confiable, firma no confiable, issuer y expiración), 5 pruebas
  HTTP y 2 GraphQL con `mvn -q -Dtest=JwtDecoderValidationTest,SecurityConfigurationTest,OrderResolverGraphQlTest test`.
- Decisiones autorizadas: Fase 2 autorizada por el usuario el 2026-10-08;
  autenticación HTTP sin sesión según matriz aprobada. En la rectificación se
  añadió cobertura de decoder Nimbus real con JWKS local.

### Fase 3 — Autorización por roles y configuración de Keycloak

- Estado: `completada`
- Objetivo: aplicar permisos de lectura/escritura y proporcionar identidades de
  demostración reproducibles.
- Alcance: roles, usuarios de prueba, cliente OAuth2 y políticas REST/GraphQL.
- Componentes previstos: configuración de Keycloak, Docker Compose, reglas de
  autorización y pruebas.
- Criterios de aceptación: un rol de lectura no puede crear pedidos; un rol de
  escritura puede realizar las operaciones definidas. Verificado con tokens
  reales de Keycloak en REST y GraphQL.
- Pruebas previstas: integración y guía manual con tokens reales de Keycloak.
- Riesgos: diferencias entre los claims de roles de Keycloak y las autoridades
  de Spring Security.
- Condición de cierre: matriz de permisos verificada para REST y GraphQL, realm
  importado y flujo Authorization Code con PKCE comprobado con cliente real.
- Resultado y evidencia: rojo TDD en `SecurityConfigurationTest`: fallaban la
  denegación de lectura sin `ORDER_READ`, la denegación de escritura a lectores
  y la denegación de mutación GraphQL a lectores. Tras implementar el mapeo
  `realm_access.roles`, reglas REST y `@PreAuthorize`, pasaron las 11 pruebas de
  seguridad, 4 pruebas JWT y 2 GraphQL; suite completa `mvn -q test` (17/17).
  `jq empty config/keycloak/orders-realm.json`, `docker compose config --quiet`
  y `git diff --check` pasaron. Keycloak 26.7.5 arrancó e importó el realm
  `orders`; discovery anunció issuer `http://localhost:8180/realms/orders`, y el
  JWKS devolvió claves de firma RS256. El 2026-10-08 el flujo PKCE manual obtuvo
  tokens reales: `reader` recibió REST GET 200 y GraphQL query con `data`; REST
  POST respondió 403 y la mutación GraphQL devolvió `FORBIDDEN`. `writer` recibió
  REST POST 200 y GraphQL mutation con `data.createOrder`. Se descartó como
  inválido un primer resultado de autorización: la API de Docker llevaba 21
  horas activa y ejecutaba una imagen anterior a los roles; tras
  `docker compose up -d --build app`, la aplicación arrancó saludable y las
  verificaciones anteriores pasaron. La prueba contra la imagen anterior creó
  el pedido local `reader-denied` (id 6), que se conserva. No se borraron datos.
- Decisiones autorizadas: Fase 3 autorizada por el usuario el 2026-10-08;
  cliente público `orders-cli`, flujo estándar habilitado, PKCE S256, Direct
  Access Grants y service accounts deshabilitados; usuarios locales `reader` y
  `writer` con roles realm `ORDER_READ` y `ORDER_WRITE`.

### Fase 4 — Documentación operativa y cierre técnico

- Estado: `completada`
- Objetivo: dejar instrucciones de uso, límites y evidencia de pruebas.
- Alcance: README y documentación de la iniciativa.
- Componentes previstos: `README.md` y los documentos de esta iniciativa.
- Criterios de aceptación: una persona puede levantar el entorno, obtener un
  token y ejecutar los casos de acceso documentados. Guía y recorrido con
  navegador verificados para ambos roles.
- Pruebas previstas: recorrido manual reproducible y comprobaciones de build.
- Riesgos: documentación desalineada con la configuración final.
- Condición de cierre: documentación autorizada y evidencia registrada.
- Resultado y evidencia: README actualizado con endpoints, roles y credenciales
  solo locales, verificación de disponibilidad OIDC, pasos PKCE para `reader` y
  `writer`, intercambio del código y ejemplos de acceso REST/GraphQL. El recorrido
  manual en navegador obtuvo tokens reales para ambos usuarios y verificó toda
  la matriz REST/GraphQL descrita en Fase 3. El 2026-10-08
  `mvn -q test` pasó con 17 pruebas; `docker compose config --quiet`, `jq empty
  config/keycloak/orders-realm.json` y `git diff --check` también pasaron. Tras
  detectar que el timeout de canje heredaba el valor de un minuto, se fijó a
  cinco minutos en el realm activo y en su archivo de importación; Admin REST
  confirmó `accessCodeLifespan: 300`. README documenta el límite y la
  necesidad de usar un código nuevo tras un rechazo. La API local se reconstruyó
  desde el código actual y pasó el healthcheck; el Dockerfile omite pruebas con
  `-DskipTests`, por lo que la evidencia automatizada sigue siendo la suite
  previa `mvn -q test` (17/17), complementada por las pruebas manuales reales.
- Decisiones autorizadas: Fase 4 autorizada por el usuario el 2026-10-08 para
  actualizar la guía operativa y verificar PKCE.

## Cierre

El usuario aprobó formalmente el cierre el 2026-10-08. Las Fases 1 a 4 están
completadas con su evidencia registrada en este plan y en la spec.
