# Mapa de bounded contexts

Los 13 dominios del monolito se reparten en 8 servicios de negocio más el gateway. El reparto no se cambia a mitad de migración.

| Servicio | Dominios del monolito | Tablas propias (schema) |
| --- | --- | --- |
| auth-service | User, UserRole, Auth, Profile, JWT, RateLimitService, TokenBlacklistService | `users` (`authdb`) + Redis |
| catalog-service | Product | `product` (`catalogdb`) + caché Redis |
| branch-service | Branch, BranchInventory | `branch`, `branch_inventory`, `processed_stock_operations` (`branchdb`) + caché Redis |
| sales-service | Sale, SaleDetail, Payment, CashRegister | `sale`, `sale_detail`, `payments`, `cash_registers` (`salesdb`) |
| transfer-service | StockTransfer | `stock_transfers` (`transferdb`) |
| notification-service | Notification | `notifications`, `processed_events` (`notificationdb`) |
| audit-service | AuditLog | `audit_logs`, `processed_events` (`auditdb`) |
| report-service | Report | ninguna, compone datos de otros servicios |
| api-gateway | sin dominio | ninguna |

## Quién llama a quién (síncrono, OpenFeign)

| Llamante | Destino | Para qué | Si el destino falla |
| --- | --- | --- | --- |
| api-gateway | auth-service | Validar el token de cada petición protegida | 503 |
| auth-service | branch-service | Validar la sucursal al crear o editar usuarios; nombre de sucursal en respuestas | 503 al escribir, nombre `null` al leer |
| catalog-service | sales-service | Saber si un producto tiene ventas antes de borrarlo | 503, no se borra |
| branch-service | catalog-service | Validar el producto al crear filas de inventario; nombres y categorías en listados | 503 al escribir, nombres de repuesto al leer |
| branch-service | sales, transfer, auth | Saber si una sucursal tiene actividad antes de borrarla | 503, no se borra |
| sales-service | branch-service | Sucursal activa; descontar y devolver stock con `Idempotency-Key` | 503, no se crea la venta |
| sales-service | catalog-service | Precios actuales | 503, no se crea la venta |
| transfer-service | branch-service | Sucursales, almacén central, stock y movimientos de la saga | 503, la transferencia sigue en su estado |
| transfer-service | catalog-service | Existencia y nombre del producto | 503 |
| notification-service | auth-service | Usuarios ADMIN y MANAGER destinatarios | reintento Kafka y DLT |
| report-service | sales, branch, catalog, auth | Agregados, stock, precios y nombres | 503 si falta sales/stock/precios, nombres congelados si fallan los demás |

## Quién publica y quién consume (asíncrono, Kafka)

| Productor | Topics | Consumidores |
| --- | --- | --- |
| auth-service | `auth.login.success`, `auth.login.failed`, `auth.logout` | audit-service |
| catalog-service | `product.created`, `product.deleted` | branch-service |
| branch-service | `stock.low` | notification-service |
| sales-service | `sale.completed`, `sale.cancelled`, `cashregister.discrepancy` | notification-service (`sale.cancelled`, `cashregister.discrepancy`) |
| transfer-service | `transfer.requested`, `transfer.approved`, `transfer.rejected`, `transfer.completed` | notification-service |

`sale.completed` no tiene consumidor todavía: queda publicado para una futura proyección de informes (opción b de report-service).

## Rutas públicas del gateway

| Ruta | Servicio | JWT |
| --- | --- | --- |
| `/api/auth/**` | auth-service | no |
| `/api/users/**`, `/api/profile/**` | auth-service | sí |
| `/api/products/**` | catalog-service | sí |
| `/api/branches/**`, `/api/inventory/**` | branch-service | sí |
| `/api/sales/**`, `/api/cashier/**`, `/api/cash-registers/**`, `/api/payments/**` | sales-service | sí |
| `/api/transfers/**` | transfer-service | sí |
| `/api/notifications/**` | notification-service | sí |
| `/api/audit-logs/**` | audit-service | sí |
| `/api/reports/**` | report-service | sí |

El contrato público (rutas, DTO y códigos de estado) es el mismo que el del monolito, así que el frontend no necesita cambios.
