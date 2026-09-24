INSERT IGNORE INTO stock_transfers (id, source_branch_id, target_branch_id, product_id, quantity, status, requested_by_id, approved_by_id, requested_at, approved_at, completed_at, rejection_reason, version, source_branch_name, target_branch_name, product_name, requested_by_username, approved_by_username) VALUES
(1, 6, 1, 57, 29, 'COMPLETED', 15, 15, '2026-07-22 07:19:00', '2026-07-22 08:01:00', '2026-07-22 09:27:00', NULL, 0, 'Almacen Central', 'Sucursal Centro', 'Papel Higienico Pack 12', 'bnavarro', 'bnavarro'),
(2, 6, 2, 51, 31, 'COMPLETED', 15, 15, '2026-07-23 07:01:00', '2026-07-23 07:35:00', '2026-07-23 08:33:00', NULL, 0, 'Almacen Central', 'Sucursal Ruzafa', 'Azucar Blanco 1kg', 'bnavarro', 'bnavarro'),
(3, 6, 3, 24, 21, 'COMPLETED', 15, 15, '2026-07-24 07:01:00', '2026-07-24 08:00:00', '2026-07-24 09:17:00', NULL, 0, 'Almacen Central', 'Sucursal Benimaclet', 'Lomo de Cerdo 500g', 'bnavarro', 'bnavarro'),
(4, 6, 4, 3, 29, 'COMPLETED', 15, 15, '2026-07-25 07:29:00', '2026-07-25 07:49:00', '2026-07-25 08:23:00', NULL, 0, 'Almacen Central', 'Sucursal Patraix', 'Yogur Natural Pack 4', 'bnavarro', 'bnavarro'),
(5, 6, 5, 22, 38, 'COMPLETED', 15, 15, '2026-07-26 07:06:00', '2026-07-26 07:51:00', '2026-07-26 09:16:00', NULL, 0, 'Almacen Central', 'Sucursal Malvarrosa', 'Carne Picada Mixta 500g', 'bnavarro', 'bnavarro'),
(6, 6, 1, 68, 11, 'COMPLETED', 15, 15, '2026-07-27 07:21:00', '2026-07-27 08:11:00', '2026-07-27 09:32:00', NULL, 0, 'Almacen Central', 'Sucursal Centro', 'Frutos Secos Mix 200g', 'bnavarro', 'bnavarro'),
(7, 6, 3, 30, 35, 'APPROVED', 15, 15, '2026-07-28 07:11:00', '2026-07-28 07:26:00', NULL, NULL, 0, 'Almacen Central', 'Sucursal Benimaclet', 'Pan de Barra Unidad', 'bnavarro', 'bnavarro'),
(8, 6, 4, 35, 18, 'APPROVED', 15, 15, '2026-07-29 07:05:00', '2026-07-29 07:20:00', NULL, NULL, 0, 'Almacen Central', 'Sucursal Patraix', 'Agua Mineral 1.5L', 'bnavarro', 'bnavarro'),
(9, 2, 5, 45, 23, 'COMPLETED', 4, 15, '2026-07-30 07:28:00', '2026-07-30 08:10:00', '2026-07-30 09:04:00', NULL, 0, 'Sucursal Ruzafa', 'Sucursal Malvarrosa', 'Arroz Redondo 1kg', 'testuser', 'bnavarro'),
(10, 1, 4, 26, 30, 'PENDING', 6, NULL, '2026-07-31 07:15:00', NULL, NULL, NULL, 0, 'Sucursal Centro', 'Sucursal Patraix', 'Filete de Merluza 400g', 'jgomez', NULL),
(11, 6, 2, 17, 24, 'PENDING', 15, NULL, '2026-08-01 07:21:00', NULL, NULL, NULL, 0, 'Almacen Central', 'Sucursal Ruzafa', 'Patatas 2kg', 'bnavarro', NULL),
(12, 6, 5, 54, 29, 'REJECTED', 15, 15, '2026-08-02 07:11:00', '2026-08-02 07:42:00', NULL, 'Producto proximo a caducar, se cancela el envio', 0, 'Almacen Central', 'Sucursal Malvarrosa', 'Detergente Liquido 1.5L', 'bnavarro', 'bnavarro'),
(13, 3, 1, 19, 17, 'CANCELLED', 10, NULL, '2026-08-03 07:27:00', NULL, NULL, NULL, 0, 'Sucursal Benimaclet', 'Sucursal Centro', 'Aguacates Pack 2', 'pramirez', NULL),
(14, 6, 3, 11, 39, 'COMPLETED', 15, 15, '2026-08-04 07:18:00', '2026-08-04 07:54:00', '2026-08-04 08:15:00', NULL, 0, 'Almacen Central', 'Sucursal Benimaclet', 'Platanos de Canarias 1kg', 'bnavarro', 'bnavarro');

