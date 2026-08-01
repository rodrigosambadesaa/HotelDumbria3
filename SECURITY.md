# Seguridad

Este documento describe controles técnicos, no asesoramiento jurídico. El
despliegue debe completar análisis de impacto, retención y obligaciones locales.

## Identidad, contraseñas y bloqueo

- Las contraseñas nuevas se derivan con Argon2id (`m=19456`, `t=2`, `p=1`) y
  sal aleatoria; se recalculan al iniciar sesión si cambian los parámetros.
- El mínimo es 12 y el máximo 128 caracteres. Registro, login, recuperación,
  verificación, refresh, avatar y mutaciones administrativas tienen límites por
  ruta; login, registro y recuperación combinan IP real y HMAC del email.
- Cinco fallos dentro de 15 minutos fijan un bloqueo suave de 15 minutos. Los
  fallos durante el bloqueo no cambian `locked_until`; una contraseña correcta
  sí puede entrar y limpia el bloqueo, evitando que conocer un email permita
  denegar el acceso. Al vencer comienza una ventana nueva; recuperar la
  contraseña sigue disponible y restablece el contador.
- Login, registro y recuperación usan respuestas neutras para no distinguir una
  cuenta ausente, deshabilitada, no verificada o temporalmente bloqueada.
- DNI/NIE se normaliza y valida con letra de control. El género es opcional y se
  elimina al anonimizar.

## Sesiones web y móvil

Los access tokens duran 15 minutos e incluyen `jti`, identificador de sesión,
tipo de cliente y versión de credenciales. Cada petición comprueba que la sesión
sigue activa, por lo que una revocación invalida también el access token.

- Web: `/auth/web/*`; refresh exclusivamente en cookie `HttpOnly`,
  `SameSite=Strict` y `Secure` en producción. El access token solo vive en
  memoria. Refresh y logout exigen origen exacto y coincidencia entre cookie
  CSRF y `X-CSRF-Token`; nunca se acepta refresh en JSON.
- Android: `/auth/mobile/*`; refresh en JSON y cifrado mediante Android Keystore
  desde API 18. API 16/17 lo conservan solo en memoria. Las respuestas 401
  concurrentes comparten una única rotación y se reintentan una vez.
- La rotación bloquea la fila con `FOR UPDATE`, crea el sucesor, enlaza
  `replaced_by` y revoca el anterior en una transacción. Reutilizar un token
  rotado compromete y revoca toda la familia.
- Cambio/recuperación de contraseña, reutilización y anonimización revocan todas
  las sesiones. El hash de User-Agent se conserva como señal de auditoría; no se
  usa como bloqueo rígido.

## Correo, verificación y recuperación

Registro y recuperación insertan el correo en el outbox dentro de la misma
transacción. Los tokens tienen alta entropía y solo se guarda SHA-256. El GET de
verificación/recuperación muestra un formulario con CSP estricta; únicamente el
POST consume el token mediante `SELECT … FOR UPDATE`, actualiza la cuenta y
confirma la transacción.

Varios workers reclaman filas con `FOR UPDATE SKIP LOCKED`, procesan lotes,
aplican espera exponencial y terminan en `dead`. El payload sensible se vacía al
enviar o agotar reintentos. La entrega es deliberadamente **al menos una vez**:
SMTP y PostgreSQL no comparten transacción, de modo que un fallo después de que
SMTP acepte el mensaje podría repetirlo. Se usa un `Message-ID` estable para que
proveedores compatibles dedupliquen; no se promete exactamente una vez.

## Privacidad, reservas y avatares

- Eliminar una cuenta anonimiza DNI, email, nombre, género y dirección, elimina
  avatar y tokens, incrementa la versión de credenciales y deshabilita el
  usuario. Las reservas mínimas se conservan y su FK es `ON DELETE RESTRICT`.
- Las reservas se validan en `Europe/Madrid`: no pasado, máximo 30 noches y
  horizonte de 18 meses. PostgreSQL aplica fechas, valores y exclusión GiST
  `[)` para impedir solapamientos incluso con solicitudes concurrentes.
- La cancelación del cliente comprueba propiedad, estado y 48 horas exactas
  antes de la entrada local de las 15:00. Los cambios administrativos siguen una
  máquina de estados y registran estado anterior/nuevo, actor y motivo.
- El avatar se detecta por bytes, decodifica con límite de píxeles, redimensiona,
  elimina metadatos y reencodifica como WebP. Un bloqueo asesor transaccional
  serializa subidas; compensación y limpieza periódica retiran huérfanos.
  Se sirve autenticado, sin `sniffing`, sin traversal y sin caché compartida.

## Perímetro y OWASP

- SQL parametrizado y validación Zod; errores públicos enumerados y 5xx
  genéricos con UUID de petición.
- CORS usa orígenes exactos; no hay comodín con credenciales. En producción se
  exigen HTTPS, cookies Secure y `TRUSTED_PROXIES` explícito. El tráfico directo
  no confía en `X-Forwarded-For`.
- JWT y HMAC de auditoría usan secretos distintos. Logs redactan Authorization,
  cookies, contraseñas y refresh tokens; IP y User-Agent se auditan con HMAC.
- Helmet aplica CSP, HSTS en producción, `nosniff` y política de referencia. Los
  contenedores de API/panel son no-root, sin capacidades y con raíz de solo
  lectura.
- Backups Android están desactivados. Release bloquea HTTP, valida endpoint
  HTTPS y usa R8; debug permite HTTP solo a `10.0.2.2`.
- La WebView de mapa restringe host/ruta, archivos, popups, contenido mixto y
  cookies, y se destruye con la Activity. No se solicitan permisos de ubicación.

## Secretos y despliegue

No publiques `.env`, dumps, APK, keystores ni claves. Usa un gestor de secretos,
un proxy TLS que sobrescriba cabeceras reenviadas y configura
`TRUSTED_PROXIES` con CIDR/IP exactos. Separa redes de API, base de datos y SMTP;
no expongas PostgreSQL ni Mailpit. Restringe la clave de Google Maps por package
y huellas debug/release, limita APIs/cuotas y usa claves diferentes por entorno.

Define copias cifradas, retención, restauración probada, alertas de sesiones
comprometidas/outbox `dead`, SAST/SCA/secret scanning, DAST efímero y respuesta a
incidentes. Reporta vulnerabilidades de forma privada y sin datos reales.
