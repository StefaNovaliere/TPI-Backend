-- ============================================================================
-- Datos de prueba. Idempotente (ON CONFLICT DO NOTHING) para soportar reinicios.
-- Los usernames coinciden con los usuarios del realm de Keycloak.
-- ============================================================================

INSERT INTO usuarios (id, username, email, keycloak_id) VALUES
    (1, 'admin', 'admin@tpi.com', 'admin'),
    (2, 'ana',   'ana@tpi.com',   'ana'),
    (3, 'bruno', 'bruno@tpi.com', 'bruno')
ON CONFLICT (id) DO NOTHING;

INSERT INTO cuentas (id, saldo_ars, usuario_id) VALUES
    (1, 1000000.00, 1),
    (2, 5000000.00, 2),
    (3, 2000000.00, 3)
ON CONFLICT (id) DO NOTHING;

-- Bruno ya posee acciones para poder venderlas; Ana posee algunas AAPL.
INSERT INTO portafolios (id, simbolo, cantidad, usuario_id) VALUES
    (1, 'NVDA', 100, 3),
    (2, 'AAPL', 50,  3),
    (3, 'AAPL', 10,  2)
ON CONFLICT (id) DO NOTHING;

-- Reajustamos las secuencias de identidad para evitar colisiones de IDs.
SELECT setval(pg_get_serial_sequence('usuarios', 'id'),    (SELECT COALESCE(MAX(id), 1) FROM usuarios));
SELECT setval(pg_get_serial_sequence('cuentas', 'id'),     (SELECT COALESCE(MAX(id), 1) FROM cuentas));
SELECT setval(pg_get_serial_sequence('portafolios', 'id'), (SELECT COALESCE(MAX(id), 1) FROM portafolios));
