# Supermarket Management System — Microservicios

Evolución a microservicios del backend [supermarket-back](https://github.com/guillerdguez/supermarket-back): la misma API REST (mismas rutas, mismos DTO, mismos códigos de estado) repartida en servicios independientes con Spring Boot 3.4 y Spring Cloud.

El monolito sigue siendo la pieza de referencia de arquitectura en capas; este repositorio muestra qué problemas aparecen al distribuirlo y cómo se resuelven: descubrimiento de servicios, configuración centralizada, puerta de entrada única, seguridad centralizada, comunicación síncrona resiliente, idempotencia, sagas con compensación, mensajería con Kafka y trazabilidad distribuida.

**Frontend:** [supermarket-front](https://github.com/guillerdguez/supermarket-front) (Angular 20). Funciona sin cambios contra `http://localhost:8080/api`.

## Arquitectura

```
                          ┌──────────────┐
  cliente ──────────────► │ api-gateway  │ :8080  (única puerta pública)
                          └──────┬───────┘
          valida el JWT en       │  lb://  (Eureka)
          auth-service y         │
          reenvía la identidad   ▼
   ┌────────────┬────────────┬────────────┬────────────┬──────────────┬─────────────┬──────────────┐
   │auth-service│catalog-svc │branch-svc  │sales-svc   │transfer-svc  │notification │audit-service │ report-service
   │   :8081    │   :8082    │   :8083    │   :8084    │   :8085      │   :8086     │   :8087      │   :8088
   └─────┬──────┴─────┬──────┴─────┬──────┴─────┬──────┴──────┬───────┴──────┬──────┴──────┬───────┘
         │ Redis      │ Redis      │ Redis      │             │              ▲             ▲
         │            │            │            │             │              │  Kafka      │
         └──── MySQL (un schema por servicio) ──┘             └──── eventos ─┴─────────────┘

  eureka-server :8761 · config-server :8888 (config-repo/) · zipkin :9411 · kafka :29092 · mysql :3307 · redis :6379
```

| Servicio | Puerto | Responsabilidad | Datos |
| --- | --- | --- | --- |
| api-gateway | 8080 | Enrutado `lb://`, validación de JWT delegada, CORS, circuit breaker por ruta, Swagger agregado | — |
| auth-service | 8081 | Login, logout, `/auth/validate`, usuarios y perfil, rate limiting y blacklist | `authdb` + Redis |
| catalog-service | 8082 | Productos, publica `product.*` | `catalogdb` + caché Redis |
| branch-service | 8083 | Sucursales e inventario, movimientos de stock idempotentes, publica `stock.low` | `branchdb` + caché Redis |
| sales-service | 8084 | Ventas, pagos y cajas; flujo distribuido con compensación | `salesdb` |
| transfer-service | 8085 | Transferencias entre sucursales como saga | `transferdb` |
| notification-service | 8086 | Notificaciones alimentadas por eventos | `notificationdb` |
| audit-service | 8087 | Auditoría de login/logout alimentada por eventos | `auditdb` |
| report-service | 8088 | Informes por composición de otros servicios | — |
| eureka-server | 8761 | Registro y descubrimiento | — |
| config-server | 8888 | Configuración centralizada desde `config-repo/` | — |
| libs-supermarket-commons | — | Librería: seguridad por cabeceras, errores, Feign, Kafka, OpenAPI | — |

Documentación de diseño:

- [docs/decisiones-migracion.md](docs/decisiones-migracion.md): cada decisión tomada y por qué.
- [docs/bounded-contexts.md](docs/bounded-contexts.md): reparto de dominios, llamadas entre servicios y rutas del gateway.
- [docs/events.md](docs/events.md): catálogo de eventos Kafka.

## Credenciales de prueba

Los `data.sql` de cada servicio reparten el mismo seed del monolito, así que existen los mismos usuarios:

| Rol | Email | Password |
| --- | --- | --- |
| Admin | `admin@supermarket.com` | `password` |
| Cajero | `cashier@supermarket.com` | `password` |

## Cómo arrancarlo

### Prerrequisitos

* Docker Desktop activo.

### Un solo comando

```bash
docker compose up --build -d
```

Levanta MySQL (con los siete schemas), Redis, Kafka en modo KRaft, Zipkin, Eureka, Config Server, los ocho servicios y el gateway. Cada contenedor tiene healthcheck contra `/actuator/health` y los servicios esperan a que sus dependencias estén sanas (`depends_on: condition: service_healthy`), no solo arrancadas. El primer arranque tarda unos minutos porque compila cada servicio dentro de su imagen.

Solo se publican hacia el host el gateway (8080), Eureka (8761), Zipkin (9411) y la infraestructura (MySQL 3307, Redis 6379, Kafka 29092). Los servicios de negocio solo son alcanzables dentro de la red de Compose.

### Probar la API

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@supermarket.com","password":"password"}' | jq -r '.token')

curl -s http://localhost:8080/api/products -H "Authorization: Bearer $TOKEN"
```

### Servicios fuera de Docker

Para depurar con Maven, levanta solo la infraestructura en Docker y arranca en local Eureka, Config Server y los servicios que necesites (cada uno en su carpeta):

```bash
docker compose up -d mysql redis kafka zipkin
cd libs-supermarket-commons && ./mvnw install -DskipTests && cd ..
cd eureka-server && ./mvnw spring-boot:run
```

Los valores por defecto de cada `application.yml` apuntan a `localhost` (MySQL 3307, Redis 6379, Kafka 29092, Eureka 8761, Config Server 8888). No mezcles servicios en Docker y en local para una misma prueba: los que corren en contenedores se registran en Eureka con IPs de la red de Compose, que no son alcanzables desde el host.

## Accesos

| Recurso | URL |
| --- | --- |
| API | `http://localhost:8080/api/...` |
| Swagger UI agregado | `http://localhost:8080/api/swagger-ui.html` (selector con la documentación de cada servicio) |
| Eureka | `http://localhost:8761` |
| Zipkin | `http://localhost:9411` |
| MySQL | `jdbc:mysql://localhost:3307/<schema>` (`authdb`, `catalogdb`, `branchdb`, `salesdb`, `transferdb`, `notificationdb`, `auditdb`) |

## Cómo viaja una petición

1. El cliente llama a `POST /api/sales` con `Authorization: Bearer <token>`.
2. El gateway descarta cualquier cabecera `X-User-*` que venga del cliente y valida el token contra `auth-service /auth/validate`.
3. `auth-service` comprueba firma, expiración, blacklist de Redis y que el usuario siga activo, y devuelve la identidad en cabeceras.
4. El gateway quita el prefijo `/api` y reenvía a `lb://sales-service` con esas cabeceras. `@PreAuthorize` funciona igual que en el monolito.
5. sales-service valida sucursal y caja, pide precios a catalog-service, descuenta stock en branch-service con `Idempotency-Key` y guarda la venta. Si algo falla después de descontar, devuelve el stock.
6. Toda la cadena queda como una única traza en Zipkin.

## La venta en microservicios

En el monolito caja, stock, precios y venta iban en una sola transacción. Ahora cada dato vive en una base distinta, así que el flujo se reordena para no dejar nunca una venta a medias:

1. **Sucursal y caja**: la sucursal debe existir y estar activa (branch-service); la caja abierta es un dato local.
2. **Precios**: siempre desde catalog-service, ignorando cualquier precio del cliente.
3. **Importe**: debe coincidir con el total antes de tocar el stock.
4. **Stock**: branch-service descuenta en bloque con una `Idempotency-Key` única por venta. Si sales-service reintenta por un timeout, el stock no se descuenta dos veces.
5. **Persistencia**: venta, detalle y pago en una transacción local.
6. **Compensación**: si el paso 5 falla, sales-service devuelve el stock con otra clave idempotente.

Las llamadas a catalog-service y branch-service tienen timeout, reintentos con backoff y circuit breaker (se abre con un 50 % de fallos en una ventana de 10 llamadas y responde de inmediato con 503 mientras está abierto).

## Transferencias como saga

`POST /api/transfers/{id}/complete` descuenta el origen y suma en el destino. El estado de cada paso se guarda en la propia transferencia; si falla el alta en destino se devuelve el stock al origen y la transferencia sigue `APPROVED` para reintentarla. Un reintento nunca descuenta dos veces gracias a claves idempotentes por intento. Detalle en [docs/decisiones-migracion.md](docs/decisiones-migracion.md).

## Endpoints

Las rutas son exactamente las del monolito, con el prefijo `/api` servido por el gateway:

* Auth (`/api/auth`): `POST /login`, `POST /logout`.
* Sucursales (`/api/branches`) e inventario (`/api/inventory`).
* Productos (`/api/products`).
* Ventas (`/api/sales`), cajero (`/api/cashier`), cajas (`/api/cash-registers`) y pagos (`/api/payments`).
* Transferencias (`/api/transfers`).
* Informes (`/api/reports`).
* Usuarios (`/api/users`) y perfil (`/api/profile`).
* Auditoría (`/api/audit-logs`).
* Notificaciones (`/api/notifications`).

El listado completo con permisos por rol está en el README de `supermarket-back` y en el Swagger agregado.

## Tests

Cada servicio se prueba de forma independiente:

```bash
cd libs-supermarket-commons && ./mvnw install && cd ..
cd sales-service && ./mvnw test
```

| Servicio | Qué cubren además de los unitarios migrados |
| --- | --- |
| auth-service | Rate limiting, blacklist tras logout y `/auth/validate` con Redis real (Testcontainers) |
| api-gateway | Filtro JWT contra un auth-service simulado con WireMock, cabeceras falsificadas y fallback del circuito |
| catalog-service | Caché de productos e invalidación con Redis real |
| branch-service | Idempotencia de stock con peticiones concurrentes, timeouts, 404 sin reintento y degradación ante catalog caído |
| sales-service | catalog o branch caídos, timeouts con reintento de la misma clave, apertura del circuito y consultas de informes sobre MySQL real con el seed completo |
| transfer-service | Saga: fallo a mitad, fallo de la compensación y caída tras el débito, con un inventario simulado que aplica idempotencia |
| notification-service | Ciclo publicar → consumir con Kafka real, deduplicación y mensaje envenenado enviado a la DLT |
| audit-service | Eventos de login y logout consumidos desde Kafka real |
| report-service | Composición sobre HTTP con WireMock, degradación de nombres y 503 si falta sales-service |

Los tests de integración usan Testcontainers 1.21 (compatible con Docker Engine 29).

El recorrido completo con el sistema levantado (humo por el gateway, eventos en Kafka, trazas en Zipkin y `docker stop catalog-service` en mitad de una venta) está descrito en `docs/estado-checklist.md`.

## Tech stack

| Área | Tecnología |
| --- | --- |
| Core | Java 17, Spring Boot 3.4, Spring Cloud 2024.0 |
| Descubrimiento y configuración | Eureka, Config Server |
| Puerta de entrada | Spring Cloud Gateway (reactivo) |
| Comunicación síncrona | OpenFeign + Apache HttpClient 5, Resilience4j |
| Mensajería | Apache Kafka (KRaft), Spring Kafka |
| Persistencia | Spring Data JPA, MySQL 8 (H2 en tests unitarios) |
| Caché y seguridad | Redis, Spring Security, JWT |
| Observabilidad | Actuator, Micrometer Tracing (Brave), Zipkin |
| Documentación | springdoc-openapi, Swagger agregado en el gateway |
| Testing | JUnit 5, Mockito, AssertJ, WireMock, Testcontainers, Awaitility |
| DevOps | Docker, Docker Compose |

---

**Autor:** Guillermo — Java Backend Developer
