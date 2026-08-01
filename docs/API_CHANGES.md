# Cambios de contrato API de la modernización

- Sesiones separadas en `/auth/web/*` y `/auth/mobile/*`. Los alias históricos
  `/auth/login` y `/auth/refresh` conservan semántica móvil temporalmente.
- Web ya no recibe refresh token en JSON y debe enviar cookie, origen y
  `X-CSRF-Token` en refresh/logout.
- Verificación de email y recuperación: GET no modifica estado; POST consume.
- Registro y recuperación responden de forma neutra y el email se entrega por
  outbox.
- `GET /locations/postal-code?postalCode=NNNNN` sustituye el PHP histórico y
  devuelve `{ data: [...] }`; el identificador de población es texto.
- Listados devuelven `{ data, nextCursor }` y aceptan cursor/limit acotado.
- `POST /reservations/:id/cancel` permite cancelación propia idempotente.
- `POST /reservations/bulk` crea de una a diez habitaciones en una sola
  transacción; si cualquiera deja de estar disponible no queda una selección
  parcialmente reservada.
- `DELETE /users/me` y `DELETE /admin/users/:id` anonimizan en lugar de borrar
  reservas. La ruta propia exige contraseña; el administrador no puede borrarse
  a sí mismo desde el panel.
- `PUT /users/me/password` exige contraseña actual, deriva la nueva con Argon2id
  y revoca todas las sesiones, incluida la utilizada para el cambio.
- Avatar solo acepta imagen real JPEG/PNG/WebP y la respuesta almacenada se
  entrega siempre como WebP autenticado.

Android y el panel incluidos en este repositorio ya consumen estos contratos.
