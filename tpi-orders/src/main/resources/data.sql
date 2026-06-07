-- ============================================================================
-- Órdenes de venta de ejemplo para poder probar compras de inmediato.
-- Bruno (usuario 3) vende acciones que ya posee en su portfolio.
-- Precios en ARS. Idempotente para soportar reinicios.
-- ============================================================================

INSERT INTO ordenes_venta (id, usuario_id, simbolo, cantidad, cantidad_restante, precio_min_ars, estado, fecha_creacion) VALUES
    (1, 3, 'NVDA', 50, 50, 145000.00, 'ABIERTA', NOW()),
    (2, 3, 'NVDA', 30, 30, 150000.00, 'ABIERTA', NOW()),
    (3, 3, 'AAPL', 40, 40, 250000.00, 'ABIERTA', NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('ordenes_venta', 'id'), (SELECT COALESCE(MAX(id), 1) FROM ordenes_venta));
