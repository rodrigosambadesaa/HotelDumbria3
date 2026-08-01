# Backend Fastify

La API requiere Node 22 y PostgreSQL 17. El flujo soportado es Docker Compose;
no se debe reutilizar el `node_modules` del host si usa otra versión de Node.

## Migraciones y tests

```bash
docker compose --profile backend up -d postgres
docker compose run --rm api node src/migrate.js
docker build -t hotel-api-test ./backend
```

El `Dockerfile` ejecuta los tests unitarios y `npm audit --omit=dev` durante el
build. La integración PostgreSQL requiere una base desechable cuyo nombre
contenga `test`:

```bash
TEST_DATABASE_URL=postgresql://user:password@postgres:5432/hotel_test npm test
```

Las migraciones son incrementales. `001_initial.sql` crea el modelo moderno y
`002_security_and_integrity.sql` añade familias de sesión, recuperación,
anonimización, historial de estados, restricciones e índices. No edites una
migración ya aplicada; crea la siguiente.

## Contratos de autenticación

- `POST /api/v1/auth/web/login|refresh|logout`: cookie refresh HttpOnly, CSRF y
  origen exacto. No se devuelve refresh en JSON.
- `POST /api/v1/auth/mobile/login|refresh|logout`: refresh explícito en JSON.
- `GET /auth/verify-email` y `GET /auth/password/reset`: vista informativa.
- Los POST equivalentes consumen el token y cambian estado en transacción.

Los listados de reservas, usuarios y auditoría devuelven
`{ data, nextCursor }`; `limit` está acotado a 100 y `cursor` es opaco.

## Configuración operativa

Parte de `.env.example`. En producción son obligatorios HTTPS,
`COOKIE_SECURE=true` y al menos un `TRUSTED_PROXIES`. Este último es una lista
separada por comas de IP/CIDR del proxy real, nunca `0.0.0.0/0`. El proxy debe
sobrescribir, no concatenar ciegamente, `X-Forwarded-For`.

Las reglas de reserva se configuran con `HOTEL_TIME_ZONE`,
`MAX_RESERVATION_NIGHTS`, `BOOKING_HORIZON_MONTHS`,
`CANCELLATION_NOTICE_HOURS` y `CHECK_IN_HOUR_LOCAL`. La base limita siempre a 30
noches como defensa adicional.

El outbox procesa como máximo `OUTBOX_BATCH_SIZE`, reintenta hasta
`OUTBOX_MAX_ATTEMPTS` y deja fallos terminales en `dead`. Monitoriza ese estado.
La semántica SMTP es al menos una vez; el `Message-ID` estable reduce duplicados
pero no los hace matemáticamente imposibles.
