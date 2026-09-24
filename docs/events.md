# Catálogo de eventos

Todos los eventos son JSON, llevan `eventId` (UUID, se usa para deduplicar), `eventType` (igual al topic) y `occurredAt` (fecha y hora ISO-8601, igual que el resto de fechas del evento). La clave del registro Kafka va indicada en cada caso: todos los eventos de una misma clave caen en la misma partición y se procesan en orden.

La cabecera `__TypeId__` lleva un tipo lógico (columna "Tipo") en lugar del nombre de la clase Java, de modo que productor y consumidor tienen cada uno su propia clase.

## auth.login.success, auth.login.failed, auth.logout

Productor: auth-service. Consumidor: audit-service. Tipo: `authActivity`. Clave: email del usuario.

| Campo | Descripción |
| --- | --- |
| `username` | Email con el que se intentó el login o se hizo logout |
| `action` | `LOGIN_SUCCESS`, `LOGIN_FAILED` o `LOGOUT` |
| `details` | Texto libre (`Invalid credentials`, `Rate limit exceeded`, ...) |
| `ipAddress` | IP del cliente según `X-Forwarded-For` añadido por el gateway |
| `status` | `SUCCESS` o `FAILED` |

## product.created, product.deleted

Productor: catalog-service. Consumidor: branch-service. Tipo: `productEvent`. Clave: id del producto.

| Campo | Descripción |
| --- | --- |
| `productId`, `name`, `category`, `price` | Datos del producto en el momento del evento |

`product.created` crea una fila de inventario con stock 0 y mínimo 5 en cada sucursal que aún no la tenga. `product.deleted` borra las filas del producto.

## stock.low

Productor: branch-service. Consumidor: notification-service. Tipo: `stockLow`. Clave: `<branchId>-<productId>`.

| Campo | Descripción |
| --- | --- |
| `branchId`, `branchName` | Sucursal |
| `productId`, `productName` | Producto (nombre obtenido de catalog-service) |
| `currentStock`, `minStock` | Stock tras la operación y mínimo configurado |

Se publica cada vez que una operación deja el stock en el mínimo o por debajo.

## sale.completed

Productor: sales-service. Sin consumidor actual. Tipo: `saleCompleted`. Clave: id de sucursal.

| Campo | Descripción |
| --- | --- |
| `saleId`, `branchId`, `cashRegisterId`, `createdById`, `date`, `total` | Cabecera de la venta |
| `lines[]` | `productId`, `quantity`, `unitPrice` |

## sale.cancelled

Productor: sales-service. Consumidor: notification-service. Tipo: `saleCancelled`. Clave: id de sucursal.

| Campo | Descripción |
| --- | --- |
| `saleId`, `branchId`, `branchName` | Venta y sucursal |
| `reason` | Motivo de la anulación |
| `cancelledById`, `cancelledByUsername` | Quién anuló |

## cashregister.discrepancy

Productor: sales-service. Consumidor: notification-service. Tipo: `cashRegisterDiscrepancy`. Clave: id de sucursal.

| Campo | Descripción |
| --- | --- |
| `cashRegisterId`, `branchId`, `branchName` | Caja cerrada |
| `variance` | Cierre real menos esperado (apertura + ventas registradas) |

## transfer.requested, transfer.approved, transfer.rejected, transfer.completed

Productor: transfer-service. Consumidor: notification-service. Tipo: `transferEvent`. Clave: id de la transferencia.

| Campo | Descripción |
| --- | --- |
| `transferId`, `quantity` | Transferencia |
| `productId`, `productName` | Producto |
| `sourceBranchId`, `sourceBranchName`, `targetBranchId`, `targetBranchName` | Origen y destino |
| `requestedById`, `requestedByUsername` | Solicitante (destinatario de `approved` y `rejected`) |
| `rejectionReason` | Solo en `transfer.rejected` |

## Errores

Un mensaje que no se puede deserializar o que falla tras 3 reintentos (1 s, 2 s y 4 s) se publica en `<topic>.DLT` con las cabeceras de diagnóstico de Spring Kafka (`kafka_dlt-exception-message`, topic, partición y offset originales).
