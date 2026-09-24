# Estado de la checklist de migración

Correspondencia entre cada punto de la checklist "De monolito a microservicios" y lo que hay en el repositorio. "Hecho" significa implementado y cubierto por tests automáticos. "Verificado en vivo" significa comprobado además con los 9 servicios, Eureka, Config Server, MySQL, Redis, Kafka y Zipkin levantados con `docker-compose.yml`; el detalle está en la sección final.

## Fase 00 · Preparación y reglas base

| Punto | Estado | Dónde |
| --- | --- | --- |
| Baseline `./mvnw test` del monolito | Hecho | 306 tests, 0 fallos (2 errores de descarga de imagen Docker del entorno) |
| Punto de retorno `monolito-base` | Hecho | Commit `5b1091d`; crear la etiqueta con `git tag monolito-base 5b1091d` |
| Convención de paquetes | Hecho | `com.supermarket.<servicio>` |
| Monorepo, un `pom.xml` y un `mvnw` por servicio, sin padre | Hecho | Carpetas raíz |
| Un schema por servicio en una única MySQL | Hecho | `docker/mysql/01-schemas.sql` |
| Decisión sobre `DemoResetService` | Hecho | No se migra; ver `docs/decisiones-migracion.md` |
| Decisiones por escrito | Hecho | `docs/decisiones-migracion.md` |

## Fase 01 · Mapa de bounded contexts

| Punto | Estado | Dónde |
| --- | --- | --- |
| 13 dominios → 8 servicios + gateway | Hecho | `docs/bounded-contexts.md` |
| Cada `@ManyToOne` entre contextos sustituido por `Long xxxId` | Hecho | Tabla en `docs/decisiones-migracion.md` |
| Datos duplicados a propósito documentados | Hecho | Sección "Datos duplicados a propósito" |
| Documento del mapa | Hecho | `docs/bounded-contexts.md` |

## Fase 02 · Infraestructura común

| Punto | Estado | Dónde |
| --- | --- | --- |
| `docker-compose.yml` raíz | Hecho | Incluye ya los 9 servicios, Kafka, Zipkin, Eureka y Config Server |
| Script de init de los 7 schemas | Hecho | `docker/mysql/01-schemas.sql` |
| Tabla de puertos fija | Hecho | README y `docs/decisiones-migracion.md` |
| Variables de entorno heredadas | Hecho | `application.yml` de cada servicio |
| Esqueleto base Java 17 + Boot 3.4 | Hecho | Todos los `pom.xml` |

## Ampliación del bloque 09 (Eureka, Config Server, Zipkin, librería compartida)

| Punto | Estado | Dónde |
| --- | --- | --- |
| Service discovery con Eureka | Hecho | `eureka-server/`, rutas `lb://` en el gateway, Feign por nombre lógico |
| Configuración centralizada con Config Server | Hecho | `config-server/` + `config-repo/`; test que sirve la configuración |
| Librería compartida y sus límites | Hecho | `libs-supermarket-commons/`, sin entidades de dominio |
| Load balancing | Hecho | Spring Cloud LoadBalancer vía `lb://` y Feign |
| Tracing distribuido con Zipkin | Verificado en vivo | Micrometer Tracing + Brave en todos los servicios, `ZIPKIN_URL` en Compose |

## Fase 03 · auth-service

| Punto | Estado | Dónde |
| --- | --- | --- |
| Usuarios, auth, perfil, JWT, rate limit y blacklist movidos | Hecho | `auth-service/` |
| `User.branch` → `branchId` | Hecho | `model/user/User.java` |
| Base de datos `authdb` | Hecho | `schema.sql`, `data.sql` |
| `GET /auth/validate` (200/401 sin cuerpo) | Hecho | `AuthController`; la identidad viaja en cabeceras |
| `AuditService` dentro de auth en la fase intermedia | Hecho y superado | En el estado final auth publica eventos (Fase 11) |
| Tests con Redis real | Hecho | `RateLimitIntegrationTest`, `SecurityIntegrationTest` |
| Levantar auth-service y probar endpoints | Verificado en vivo | login, login fallido, logout con token revocado |
| `./mvnw test` en verde | Hecho | |

## Fase 04 · api-gateway

| Punto | Estado | Dónde |
| --- | --- | --- |
| Gateway reactivo | Hecho | `api-gateway/` |
| Rutas por servicio (strangler fig) | Hecho | Estado final: todas las rutas apuntan a su servicio; el monolito ya no se enruta |
| `JwtValidationGatewayFilterFactory` | Hecho | `filter/`; test con WireMock |
| `StripPrefix` | Hecho | `/api/**` → `/**` |
| Swagger agregado | Hecho | `/api/swagger-ui.html` + rutas `/api-docs/<servicio>` |
| CORS centralizado | Hecho | `globalcors` en el gateway; los servicios lo desactivan |
| Login y validación a través del gateway | Verificado en vivo | 401 sin token y tras logout |

## Fase 05 · catalog-service

| Punto | Estado | Dónde |
| --- | --- | --- |
| Product y su capa movidos, sin `@OneToMany` | Hecho | `catalog-service/` |
| `catalogdb` | Hecho | |
| Ruta `/api/products/**` | Hecho | Gateway |
| Filtros `name`, `category`, precio | Hecho | `ProductSpecifications`, tests de controlador |

## Fase 06 · branch-service

| Punto | Estado | Dónde |
| --- | --- | --- |
| Branch e inventario movidos, `productId` plano | Hecho | `branch-service/` |
| OpenFeign a catalog-service | Hecho | `CatalogClient`, `CatalogLookupService` |
| Timeout y retry con backoff | Hecho | Resilience4j + timeouts Feign; `CatalogLookupResilienceTest` |
| `branchdb` y rutas | Hecho | |
| Error controlado si catalog cae | Hecho | 503 al escribir, nombres de repuesto al leer |

## Fase 07 · sales-service

| Punto | Estado | Dónde |
| --- | --- | --- |
| Venta, detalle, pago y caja movidos con ids planos | Hecho | `sales-service/` |
| Flujo rediseñado en 5 pasos | Hecho | `SaleServiceImpl.create` |
| `Idempotency-Key` en el descuento de stock | Hecho | branch-service `StockMovementService`; test concurrente |
| Nunca una venta a medias | Hecho | Compensación si falla la persistencia local |
| Circuit breaker, retry y timeout documentados | Hecho | `application.yml`, `config-repo/sales-service.yml` |
| Tests con catalog y branch caídos | Hecho | `SaleResilienceIntegrationTest` (WireMock) |
| `salesdb` y ciclo completo | Hecho | Tests unitarios y MySQL real |

## Fase 08 · transfer-service

| Punto | Estado | Dónde |
| --- | --- | --- |
| StockTransfer movido con ids planos | Hecho | `transfer-service/` |
| Feign a catalog y branch con idempotencia | Hecho | `BranchRemoteService`, `CatalogRemoteService` |
| Compensación explícita documentada e implementada | Hecho | Saga con estado persistido |
| Test que fuerza el fallo a mitad del `complete` | Hecho | `TransferCompletionSagaTest` |
| `transferdb` | Hecho | |

## Fase 09 · Kafka

| Punto | Estado | Dónde |
| --- | --- | --- |
| Kafka KRaft en Compose | Hecho | Servicio `kafka` |
| Catálogo de eventos | Hecho | `docs/events.md` (se añaden `product.created` y `product.deleted`) |
| Convención de topics | Hecho | `<dominio>.<evento>` |
| Un DTO por evento, JSON, campos mínimos | Hecho | Paquetes `event/` de cada servicio |
| Eventos documentados | Hecho | `docs/events.md` |
| Reintentos con backoff y DLT | Hecho | `CommonsKafkaAutoConfiguration`; test con mensaje envenenado |
| Productores en branch, sales, transfer y auth | Hecho | |
| Ver cada evento llegar con `kafka-console-consumer` | Verificado en vivo | Los 12 topics |

## Fase 10 · notification-service

| Punto | Estado | Dónde |
| --- | --- | --- |
| Notificaciones movidas | Hecho | `notification-service/` |
| Listeners Kafka en lugar de `NotificationEventService` | Hecho | `NotificationEventListener`, `NotificationEventHandler` |
| Decisión sobre los destinatarios | Hecho | Opción A (Feign a auth-service) |
| `notificationdb` y ruta | Hecho | |
| Notificaciones generadas solo por eventos | Verificado en vivo | `NotificationKafkaIntegrationTest` |

## Fase 11 · audit-service

| Punto | Estado | Dónde |
| --- | --- | --- |
| AuditLog movido | Hecho | `audit-service/` |
| Consumo de `auth.*` | Hecho | `AuthActivityListener`; `AuditKafkaIntegrationTest` |
| `AuditService` retirado de auth | Hecho | auth publica eventos |
| `auditdb` y filtros de `GET /api/audit-logs` | Hecho | |

## Fase 12 · report-service

| Punto | Estado | Dónde |
| --- | --- | --- |
| Informes movidos | Hecho | `report-service/` |
| Estrategia de datos | Hecho | Opción (a), composición en caliente |
| Ruta `/api/reports/**` | Hecho | |
| Mismos números que el monolito | Hecho salvo un defecto del monolito | Rendimiento de productos contaba ventas canceladas; ver decisiones |

## Fase 13 · Redis, observabilidad y Testcontainers

| Punto | Estado | Dónde |
| --- | --- | --- |
| Redis en auth sin cambios | Hecho | |
| Caché en catalog y bajo stock en branch | Hecho | `CacheConfig` en ambos; invalidación en cada escritura; test con Redis real en catalog |
| Actuator y healthchecks reales | Hecho | Healthcheck `curl /actuator/health` en Compose |
| `traceId` propagado por gateway, Feign y Kafka | Verificado en vivo | Micrometer Tracing, `feign-micrometer`, `observation-enabled` en Kafka |
| Testcontainers MySQL | Hecho | `SalesReportMySqlIntegrationTest` con el seed completo |
| Testcontainers Kafka | Hecho | notification y audit |

## Fase 14 · Orquestación final

| Punto | Estado | Dónde |
| --- | --- | --- |
| Compose final con todo | Hecho | `docker-compose.yml` |
| `supermarket-back` intacto | Hecho | No se ha modificado |
| Recorrido de humo completo por el gateway | Verificado en vivo | |
| `docker stop catalog-service` durante una venta | Verificado en vivo | También `SaleResilienceIntegrationTest` |
| Decisión de `DemoResetService` aplicada | Hecho | No existe en ningún servicio |
| README con diagrama, puertos y arranque | Hecho | `README.md` |

## Verificación extremo a extremo

Hecha con el `docker-compose.yml` del repositorio y la base de datos sembrada desde cero. Las imágenes se construyeron a partir de los jar compilados en local, porque en el entorno de trabajo los contenedores de `docker build` no tenían salida HTTPS hacia Maven Central. Queda por comprobar en una máquina con red normal que `docker compose up --build -d` compila las imágenes con los Dockerfile multietapa.

Arranque: los 15 contenedores llegan a `healthy` y los 9 servicios aparecen registrados en Eureka.

Recorrido de humo por `http://localhost:8080/api`: petición sin token (401), login, crear sucursal, crear producto (el evento `product.created` crea la fila de inventario), fijar stock, abrir caja, crear venta, comprobar la alerta de stock bajo, ver la auditoría del login, pedir el resumen de ventas (devuelve la venta), cerrar la caja con un descuadre de -25 y ver la notificación de descuadre. Después: transferencia desde el almacén central solicitada, aprobada y completada (el stock de destino sube 5), otra transferencia rechazada, cancelación de la venta (el stock vuelve), login fallido y logout (el token deja de servir).

Kafka: `kafka-console-consumer` muestra eventos en los 12 topics (`auth.login.success`, `auth.login.failed`, `auth.logout`, `product.created`, `stock.low`, `sale.completed`, `sale.cancelled`, `cashregister.discrepancy` y los cuatro `transfer.*`). No se crea ninguna DLT durante el recorrido.

Trazas: una misma traza de Zipkin recorre api-gateway → sales-service → branch-service y catalog-service por Feign → `stock.low` por Kafka → notification-service → auth-service. La cabecera `traceparent` viaja en cada mensaje de Kafka.

Resiliencia: con `docker stop catalog-service`, la primera venta responde 503 "catalog-service is temporarily unavailable. Please try again later." en unos 6 s (tres intentos con 2 s de timeout de conexión). Las siguientes se rechazan en unos 0,1 s con el circuito abierto, sin reintentos. No se crea ninguna venta ni se descuenta stock. Tras `docker start catalog-service`, las ventas vuelven a funcionar solas en unos 30 s.

Swagger: `/api/swagger-ui.html` lista los 8 servicios y cada `/api-docs/<servicio>` devuelve su contrato con el servidor `/api`.

Defectos encontrados en esta verificación y corregidos (detalle en `docs/decisiones-migracion.md`): rutas inexistentes que devolvían 500, fechas de los eventos serializadas como array, circuito abierto que seguía reintentando y descubrimiento en Eureka de más de un minuto tras reiniciar un servicio.
