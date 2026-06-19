-- ============================================================================
-- Datos de prueba. Idempotente (ON CONFLICT DO NOTHING) para soportar reinicios.
-- Los usernames coinciden con los usuarios del realm de Keycloak.
-- ============================================================================

INSERT INTO usuarios (id, username, email, keycloak_id)
VALUES (1, 'admin', 'admin@tpi.com', 'admin'),
       (2, 'ana', 'ana@tpi.com', 'ana'),
       (3, 'bruno', 'bruno@tpi.com', 'bruno'),
       (4, 'carlos', 'carlos@tpi.com', 'carlos'),
       (5, 'diana', 'diana@tpi.com', 'diana') ON CONFLICT (id) DO NOTHING;

INSERT INTO cuentas (id, saldo_ars, usuario_id)
VALUES (1, 1000000.00, 1),
       (2, 5000000.00, 2),
       (3, 2000000.00, 3),
       (4, 3000000.00, 4),
       (5, 1000000.00, 5) ON CONFLICT (id) DO NOTHING;

INSERT INTO portafolios (id, simbolo, cantidad, usuario_id)
VALUES (1, 'NVDA', 100, 3), -- Bruno vende a 145k
       (2, 'AAPL', 50, 3),
       (3, 'AAPL', 10, 2),
       (4, 'NVDA', 200, 4), -- Carlos vende BARATO (140k)
       (5, 'NVDA', 50, 5)   -- Diana vende CARO (155k)
    ON CONFLICT (id) DO NOTHING;

-- Reajustamos las secuencias de identidad para evitar colisiones de IDs.
SELECT setval(pg_get_serial_sequence('usuarios', 'id'), (SELECT COALESCE(MAX(id), 1) FROM usuarios));
SELECT setval(pg_get_serial_sequence('cuentas', 'id'), (SELECT COALESCE(MAX(id), 1) FROM cuentas));
SELECT setval(pg_get_serial_sequence('portafolios', 'id'), (SELECT COALESCE(MAX(id), 1) FROM portafolios));
