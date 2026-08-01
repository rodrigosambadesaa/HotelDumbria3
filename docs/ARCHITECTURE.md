# Arquitectura y decisiones de seguridad

```text
Android (sesión móvil) ─┐
iOS (fuera de Docker) ──┼─ HTTPS ─ Fastify ─ PostgreSQL
Panel React (sesión web)┘              ├─ almacenamiento WebP
                                      └─ outbox ─ SMTP
```

## Identidad y sesiones

`persons` contiene los datos personales; `users` conserva credenciales, rol y
estado. Una cuenta web recibe el refresh token solo como cookie HttpOnly y usa
CSRF/origen. Una cuenta móvil recibe el refresh en JSON. Ambos modelos comparten
familias transaccionales de sesiones, pero cada fila identifica `web` o
`mobile`. El access token referencia la sesión (`sid`) y la versión de
credenciales, por lo que una revocación tiene efecto inmediato en la API.

La IP y el User-Agent solo se guardan como HMAC para auditoría. El User-Agent no
se usa como vínculo rígido porque puede cambiar legítimamente. Fastify solo
interpreta cabeceras de proxy si la IP/CIDR está en `TRUSTED_PROXIES`.

## Persistencia y privacidad

Las reservas referencian `persons` con `ON DELETE RESTRICT`. La eliminación es
una anonimización transaccional: revoca sesiones/tokens, elimina el avatar,
deshabilita credenciales y sustituye los campos personales por identificadores
no reutilizables. Se conserva el mínimo histórico de reservas y auditoría.

PostgreSQL aplica constraints de fechas/valores y una exclusión GiST sobre
`daterange(check_in, check_out, '[)')` para estados activos. La aplicación añade
la máquina de estados, propiedad, horizonte y reglas de cancelación.

## Correo y ficheros

Crear una cuenta o solicitar recuperación inserta el mensaje y el token hash en
la misma transacción. Workers independientes reclaman outbox con
`FOR UPDATE SKIP LOCKED`; la entrega es al menos una vez y usa `Message-ID`
estable. El outbox no bloquea `listen()` y el apagado espera el lote activo.

Los avatares usan un identificador de almacenamiento generado. La imagen se
decodifica y reconstruye antes de escribir; un bloqueo asesor por persona
serializa el cambio de referencia. Si falla la transacción se borra el fichero
nuevo; si falla el borrado posterior, la limpieza periódica reconcilia el
directorio con la base de datos.

## Clientes

Android mantiene API 16 por compatibilidad histórica. `NetworkInfo` queda
aislado a API 16–20; desde API 21 se usan `NetworkCapabilities`. Las Activities
comprueban Internet fuera del hilo principal mediante DNS públicos secuenciales
y, solo si todos fallan, dominios HTTPS secuenciales. El primer éxito detiene la
prueba. Release bloquea HTTP; debug lo permite únicamente hacia `10.0.2.2`.

El panel conserva access tokens solo en memoria. Coordina una única petición de
refresh ante 401 concurrentes y usa cursores estables en listados grandes. iOS
es un proyecto SwiftUI independiente que debe generarse y probarse en macOS.
