# Trazabilidad del proyecto legado

El código PHP y los volcados originales se auditaron desde la copia local
facilitada, pero no se incorporan literalmente al historial Git: contienen
credenciales de base de datos, consultas vulnerables y datos personales. Este
directorio conserva el modelo y la correspondencia funcional, mientras que
`legacy-import/` realiza una importación controlada.

## Correspondencia de endpoints

| PHP histórico | Sustitución |
|---|---|
| `persona.php` | `POST /api/v1/auth/register`, `POST /auth/login`, `GET/PUT /users/me` |
| `borrar_perfil.php` | `DELETE /api/v1/users/me` autenticado y transaccional |
| `habitaciones.php` / `reserva_habitaciones.php` | `/api/v1/rooms/availability` y `/reservations` |
| `listado_reservas.php` | `GET /api/v1/reservations/me` |
| `upload.php` | `POST /api/v1/users/me/avatar` |
| `conexion.php` | variables de entorno y pool PostgreSQL |
| `poblaciones_provincias.php` | tablas normalizadas `provinces`, `towns`, `postal_codes` |

## Problemas corregidos

- SQL injection por interpolación directa y uso de `extract($_GET)`.
- Posibilidad de ejecutar borrados sin filtro.
- Borrado de `usuario` y `persona` sin transacción ni integridad referencial.
- Hash SHA-512 rápido y reutilizable enviado por los clientes.
- Errores SQL, consultas y entradas del usuario devueltos al cliente.
- Upload con extensión/ruta elegida por el cliente, permisos `0777` y URL HTTP.
- Ausencia de autenticación/autorización en operaciones de perfil y reservas.
- Confirmación de correo desacoplada y sin reintentos.

## Importación

1. Copia uno de los volcados a `legacy-import/input/hotel_dumbria.sql`. Esa
   carpeta está ignorada por Git.
2. Levanta primero el backend.
3. Ejecuta el perfil de migración descrito en `EJECUCION.md`.

Las contraseñas antiguas no se importan. Las cuentas quedan con
`password_reset_required=true`; convertir SHA-512 a Argon2id solo envolvería un
secreto reproducible y no solucionaría el riesgo de repetición.
