# Decisiones de la migración

Documento de referencia para no reinventar convenciones a mitad de camino. Cada decisión indica el motivo y, cuando aplica, la alternativa descartada.

## Punto de partida

- `supermarket-back-micro` empezó siendo una copia exacta del monolito `supermarket-back`. Con esa copia, `./mvnw test` ejecutaba 306 tests sin fallos (los dos únicos errores eran de Testcontainers al no poder descargar la imagen de Redis en el entorno, no del código).
- El monolito de partida queda identificado por el commit `5b1091d` de este repositorio. Para tener el punto de retorno como etiqueta basta con `git tag monolito-base 5b1091d && git push origin monolito-base`.
- `supermarket-back` sigue siendo el proyecto de referencia de arquitectura en capas y no se toca ni se fusiona con este repositorio.

## Convenciones fijadas

| Tema | Decisión |
| --- | --- |
| Paquetes Java | `com.supermarket.<nombreservicio>` (`com.supermarket.salesservice`, ...). Nunca `com.supermarket.supermarket`. |
| Layout | Monorepo con una carpeta por servicio, cada una con su `pom.xml` y su `mvnw`. Sin módulo padre Maven. |
| Versiones | Java 17, Spring Boot 3.4.1, Spring Cloud 2024.0.0. Se mantiene Java 17 aunque el material de la Fase 09 use JDK 21: todo lo aprendido se transfiere igual. |
| Base de datos | Una única instancia de MySQL con un schema por servicio: `authdb`, `catalogdb`, `branchdb`, `salesdb`, `transferdb`, `notificationdb`, `auditdb`. Ningún servicio consulta tablas de otro. |
| Puertos | gateway 8080, auth 8081, catalog 8082, branch 8083, sales 8084, transfer 8085, notification 8086, audit 8087, report 8088, eureka 8761, config 8888, zipkin 9411. |
| Variables de entorno | Se heredan las del monolito (`MYSQL_*`, `REDIS_*`, `JWT_SECRET`, `JWT_EXPIRATION`, `CORS_ALLOWED_ORIGINS`) y se añaden `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `CONFIG_SERVER_URL` y `ZIPKIN_URL`. |
| Topics Kafka | `<dominio>.<evento>` en minúsculas con puntos. Las DLT usan el sufijo `.DLT`. |
| Endpoints internos | Todo lo que solo consumen otros servicios vive bajo `/internal/**`. El gateway nunca enruta esas rutas. |

## DemoResetService

Se elige la opción simple: **no se migra**. El job que trunca y resiembra todas las tablas cada 4 horas tenía sentido con una única base de datos; con siete schemas y datos que viajan por eventos, reimplementarlo exigiría un endpoint de reset por servicio y un orquestador externo. La demo pública sigue viviendo en `supermarket-back`. Cada servicio sigue sembrando sus datos de ejemplo con su propio `data.sql` (idempotente con `INSERT IGNORE`) en el primer arranque.

## Seguridad: validación centralizada, identidad por cabeceras

Siguiendo el patrón del proyecto de pacientes (`JwtValidationGatewayFilterFactory`):

1. El cliente solo habla con `api-gateway`.
2. En rutas protegidas el gateway llama a `auth-service` `GET /auth/validate` con la cabecera `Authorization`.
3. `auth-service` responde 200 sin cuerpo y con la identidad en cabeceras (`X-User-Id`, `X-User-Email`, `X-User-Name`, `X-User-Role`, `X-User-Branch-Id`) o 401 sin cuerpo con el motivo en `X-Auth-Error`.
4. El gateway elimina cualquier `X-User-*` que venga del cliente (filtro global con máxima prioridad) y reenvía solo las que devolvió `auth-service`.
5. Cada servicio reconstruye el usuario con `HeaderAuthenticationFilter` (librería común), así que `@PreAuthorize("hasRole('ADMIN')")` sigue funcionando exactamente igual que en el monolito.

Los servicios internos no publican puertos en Docker Compose: el gateway es la única puerta pública real. Es la misma decisión que analiza la Fase 09 (hallazgo 4): la seguridad se concentra en el borde.

Alternativa descartada: OAuth2 Authorization Server con JWT firmados con RSA, como en `msvc-oauth`. Cambiaría el contrato de login que ya consume el frontend (`POST /api/auth/login` con email y contraseña) y no aporta nada a los objetivos de la migración.

## Librería compartida `libs-supermarket-commons`

Solo contiene infraestructura transversal, nunca entidades de dominio:

- Excepciones de negocio comunes y `CommonExceptionHandler` (mismo formato de error que el monolito).
- Seguridad por cabeceras (`HeaderAuthenticationFilter`, `CurrentUserProvider`, `AuthenticatedUser`).
- Interceptor Feign que propaga las cabeceras de identidad y `RemoteErrorDecoder`, que traduce 404/400/409/403/5xx de otro servicio a las excepciones equivalentes.
- `DomainEventPublisher` (publicación tras commit) y el manejador de errores de Kafka con DLT.
- OpenAPI común con servidor `/api` para que "Try it out" funcione desde el Swagger del gateway.

Se registra por autoconfiguración de Spring Boot, así que ningún servicio necesita escanear su paquete.

El matiz de la Fase 09 se respeta: compartir una entidad JPA acopla modelos de datos. Por eso cada servicio define sus propios DTO de cliente (`ProductSummary`, `BranchSummary`...) y sus propios DTO de evento, aunque se parezcan entre sí.

## Relaciones que cruzaban límites de servicio

Todas las `@ManyToOne` con `@JoinColumn` hacia otro contexto pasan a un `Long xxxId` sin FK real:

| Entidad | Antes | Después |
| --- | --- | --- |
| `User` | `branch` | `branchId` |
| `Notification` | `user` | `userId` + `username` |
| `BranchInventory` | `product` | `productId` (la relación con `branch` sigue siendo JPA, mismo servicio) |
| `CashRegister` | `branch`, `openedBy`, `closedBy` | ids + `branchName`, `openedByUsername`, `closedByUsername` |
| `Sale` | `branch`, `createdBy`, `cancelledBy` | ids + `branchName`, `createdByUsername`, `createdByEmail`, `cancelledByUsername` |
| `SaleDetail` | `product` | `productId` + `productName`, `productCategory` |
| `StockTransfer` | `sourceBranch`, `targetBranch`, `product`, `requestedBy`, `approvedBy` | ids + nombres |
| `Product` | `@OneToMany branchInventories` | eliminada |

## Datos duplicados a propósito

- **Hechos históricos**: venta, detalle de venta, caja y transferencia congelan los nombres en el momento de la operación, igual que `SaleDetail` ya congelaba el precio. Así leer una venta no depende de que catalog, branch o auth estén disponibles.
- **Vistas vivas**: el inventario de una sucursal no guarda copia del producto. branch-service pide los datos a catalog-service por lotes y, si catalog no responde, devuelve las filas sin nombre en lugar de fallar.
- **Informes**: report-service usa nombres actuales pedidos a sus dueños y cae a los congelados si alguno está caído.

## Comunicación síncrona

- OpenFeign con Apache HttpClient 5 y nombres lógicos de Eureka (`@FeignClient(name = "catalog-service")`).
- Resilience4j (`@CircuitBreaker` + `@Retry`) en adaptadores `*RemoteService`/`*LookupService`, con timeouts de Feign (2 s conexión, 3 s lectura en `config-repo`).
- Los errores de negocio (404, 400, 409) no se reintentan ni abren el circuito; solo cuentan los 5xx, timeouts y errores de red.
- Se desactiva el reintento propio de Spring Cloud LoadBalancer (`spring.cloud.loadbalancer.retry.enabled=false`). Con `spring-retry` en el classpath (lo trae spring-kafka) se apilaba con Resilience4j y cada intento generaba dos peticiones; lo destapó el test de resiliencia de branch-service.
- Lecturas que solo decoran una respuesta degradan (nombres a `null` o congelados). Escrituras y validaciones que deciden si algo es posible fallan cerrado con 503.
- Con el circuito abierto no se reintenta. El fallback de `@CircuitBreaker` convertía el `CallNotPermittedException` en `RemoteServiceException` y el `@Retry` exterior lo volvía a intentar dos veces más, así que el "fallo rápido" tardaba 0,7 s y contaba tres rechazos por petición. Ahora `RemoteFailures` lo traduce a `CircuitOpenException` (subclase de `RemoteServiceException`, mismo 503 y mismo mensaje) y cada `retry` la declara en `ignore-exceptions`. Lo destapó la prueba de `docker stop catalog-service` con el sistema levantado.
- Descubrimiento más ágil: `registry-fetch-interval-seconds: 5` y caché del LoadBalancer de 5 s en `config-repo/application.yml`, y caché de respuestas de Eureka de 5 s. Con los valores por defecto (30 s + 35 s) un servicio recién reiniciado podía estar en UP pero invisible para los demás durante más de un minuto; las llamadas de ese intervalo fallaban y llegaban a abrir el circuito.

## Idempotencia de stock

branch-service expone `POST /internal/inventory/branches/{id}/decrease|increase` con cabecera obligatoria `Idempotency-Key`. La clave se inserta en `processed_stock_operations` en la misma transacción que el cambio de stock y antes de aplicarlo. Una repetición con la misma clave devuelve `applied: false` sin tocar el stock. Si la operación falla (por ejemplo, stock insuficiente), la transacción se revierte, la clave no queda guardada y un reintento posterior puede aplicarse.

`ProcessedStockOperation` implementa `Persistable` para forzar INSERT. Con un id asignado, `save()` hace `merge`, y dos peticiones concurrentes con la misma clave terminaban aplicando el descuento dos veces (el test de concurrencia lo reprodujo antes de corregirlo).

## Ventas sin transacción distribuida

El flujo del monolito (caja, stock, precios y descuento en una sola transacción ACID) ya no es posible. El nuevo orden en `SaleServiceImpl.create`:

1. Sucursal existente y activa (branch-service) y caja abierta (tabla local).
2. Precios desde catalog-service; nunca se confía en el precio del cliente.
3. El importe debe coincidir con el total **antes** de tocar stock (en el monolito se comprobaba después, dentro de la transacción).
4. Descuento de stock en branch-service con la clave `sale-<uuid>-decrease`.
5. Venta, detalles y pago en una transacción local.
6. Si el paso 5 falla, compensación con `sale-<uuid>-compensation`.

La cancelación usa la clave determinista `sale-<id>-cancel`, así que reintentar una cancelación nunca devuelve el stock dos veces.

## Transferencias como saga explícita

El ciclo `PENDING → APPROVED → COMPLETED` se completa ahora con estado persistido en la propia transferencia (`completion_state` y `completion_attempt`):

- `NONE` → se descuenta el origen con la clave `transfer-<id>-attempt-<n>-source` → `SOURCE_DEBITED`.
- `SOURCE_DEBITED` → se suma al destino con `...-target` → `COMPLETED`.
- Si falla el destino → `COMPENSATING` → se devuelve el stock al origen con `...-compensation` → `NONE` y `attempt = n + 1`.
- Una compensación interrumpida se reanuda antes del siguiente intento.

Como cada paso se persiste después de ejecutarse y las claves no cambian dentro de un intento, una caída entre la llamada remota y el guardado local se resuelve repitiendo el paso: branch-service devuelve la operación ya aplicada sin repetirla.

## Kafka

- Productores con `JsonSerializer` y `spring.json.type.mapping` (tipo lógico, no clase Java). Consumidores con `ErrorHandlingDeserializer` + `JsonDeserializer`, `trusted.packages` y su propio mapeo de tipos.
- Publicación tras el commit de la transacción local (`DomainEventPublisher.publishAfterCommit`). El patrón Outbox queda como mejora futura: hoy, si el broker cae justo después del commit, el evento se pierde y se registra en el log.
- Consumidores con `enable-auto-commit: false`, `ack-mode: record`, `DefaultErrorHandler` con backoff exponencial (1 s, 2 s, 4 s) y `DeadLetterPublishingRecoverer` hacia `<topic>.DLT`.
- Consumidores idempotentes: `processed_events` guarda el `eventId` en la misma transacción que el efecto.
- `occurredAt` y el resto de fechas viajan en ISO-8601. El `JsonSerializer` de Spring Kafka usa por defecto su propio `ObjectMapper`, que escribe `LocalDateTime` como array (`[2026,9,24,17,2,15]`); `CommonsKafkaAutoConfiguration` le pasa el `ObjectMapper` de Spring Boot. Los consumidores aceptan ambos formatos, así que los mensajes antiguos siguen siendo legibles.
- Topics creados con beans `NewTopic` (3 particiones, factor 1). Adecuado en desarrollo; en producción se crearían por infraestructura como código.

## notification-service: cómo sabe quiénes son ADMIN/MANAGER

Opción A: llamada síncrona a auth-service (`GET /internal/users/by-role?roles=ADMIN,MANAGER`) al procesar cada evento. Si auth-service no responde, el listener lanza la excepción, Kafka reintenta y, agotados los reintentos, el evento va a la DLT. Se descarta la opción B (réplica de usuarios alimentada por `user.updated`) porque añade un evento y una tabla sincronizada sin necesidad a esta escala.

## report-service

Opción (a), composición en caliente: sin base de datos propia. sales-service calcula los agregados sobre sus propias tablas en endpoints `/internal/reports/**` y report-service los combina con stock (branch), precios (catalog) y nombres (branch, catalog, auth). La opción (b), proyección propia alimentada por eventos (CQRS ligero), queda como mejora de diferenciación.

## Defecto del monolito encontrado durante la migración

La consulta de rendimiento de productos (`ProductRepository.findProductSalesTotals`) usaba `LEFT JOIN sd.sale s ON s.status = 'REGISTERED' AND ...fechas...`, pero sumaba `sd.quantity` sin comprobar que `s` existiera. En la práctica contaba todas las líneas de venta, incluidas las canceladas y las de fuera del rango de fechas o de otra sucursal. En microservicios `totalSold` solo cuenta ventas registradas dentro de los filtros, que es lo que el informe pretendía mostrar, así que ese informe no devuelve los mismos números que el monolito cuando hay ventas canceladas o filtros activos.

## Errores de ruta

Una ruta inexistente o un método no soportado devolvían 500 con "An unexpected error occurred" (el monolito hacía lo mismo, porque su manejador genérico capturaba `NoResourceFoundException`). `CommonExceptionHandler` responde ahora 404 y 405.

## Config Server

Backend `native` leyendo la carpeta `config-repo/` del propio monorepo (montada en el contenedor). Cambiar a Git es solo `CONFIG_BACKEND=git` y `CONFIG_GIT_URI`. Se adopta servicio a servicio, como en la Fase 09: cada servicio conserva en su `application.yml` lo imprescindible para arrancar solo (datasource, Kafka, Eureka, Resilience4j por defecto) y el Config Server aporta lo ajustable por entorno (exposición de Actuator, trazas, timeouts de Feign, umbrales de circuito de sales-service, TTL de cachés).
