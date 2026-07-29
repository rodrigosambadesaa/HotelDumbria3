# Seguridad

## Contraseñas y sesiones

- Argon2id con 19 MiB, 2 iteraciones y paralelismo 1; cada hash contiene una sal
  aleatoria. El servidor vuelve a derivar automáticamente hashes cuyos
  parámetros queden obsoletos.
- Mínimo 12 y máximo 128 caracteres; rate limit, bloqueo temporal y respuesta
  uniforme reducen fuerza bruta y enumeración.
- Access tokens de 15 minutos; refresh tokens aleatorios, rotatorios, revocables
  y almacenados como SHA-256 porque son secretos de alta entropía.
- Android guarda únicamente el refresh token cifrado con Android Keystore desde
  API 18; en API 16/17 no persiste sesión. iOS usa Keychain. La web mantiene el
  access token solo en memoria y usa cookie `HttpOnly`, `SameSite=Strict`.
- La contraseña nunca se registra, serializa en el perfil ni se guarda en las
  preferencias del cliente.

## OWASP Top 10

| Riesgo | Controles implementados |
|---|---|
| Acceso roto | autorización por rol y propiedad del recurso; rutas admin separadas |
| Fallos criptográficos | Argon2id, tokens CSPRNG, Keystore/Keychain, TLS obligatorio en producción |
| Inyección | validación Zod y SQL exclusivamente parametrizado |
| Diseño inseguro | reservas transaccionales y exclusión PostgreSQL contra solapamientos |
| Mala configuración | secretos fuera de Git, CORS de lista cerrada, CSP, HSTS y contenedores no root |
| Componentes vulnerables | lockfiles, imágenes versionadas y comandos de auditoría reproducibles |
| Autenticación rota | verificación de email, rate limit, bloqueo, rotación y revocación |
| Integridad de software | checksum del SDK Android, lockfiles y builds inmutables |
| Registro insuficiente | auditoría de login, perfil, avatar, reservas y acciones administrativas |
| SSRF | no existen URLs arbitrarias proporcionadas por usuarios en llamadas del servidor |

## Controles adicionales

- Upload limitado, nombre de almacenamiento aleatorio, detección por contenido
  real y solo JPEG/PNG/WebP; el fichero nunca se ejecuta.
- Borrado de cuenta autenticado con contraseña y transaccional. `persons` es la
  raíz; sus claves foráneas `ON DELETE CASCADE` eliminan usuario, reservas,
  sesiones, tokens y avatar de base de datos.
- Errores internos se registran sin devolver consultas, rutas ni credenciales.
- CSP, `X-Content-Type-Options`, `Referrer-Policy`, permisos restringidos y
  protección CSRF para cookies.
- API y panel se ejecutan sin capacidades Linux, sin privilegios adicionales,
  como usuarios no root y con filesystem de solo lectura.

## Antes de producción

1. Usa un proxy TLS con certificados válidos y cambia todos los endpoints a
   `https://`; establece `NODE_ENV=production` y `COOKIE_SECURE=true`.
2. Cambia credenciales bootstrap, JWT, PostgreSQL y SMTP; almacénalas en un
   gestor de secretos, no en `.env`.
3. Limita exposición de PostgreSQL/Mailpit, configura copias cifradas y prueba
   restauración y borrado de backups conforme a la política de retención.
4. Activa análisis SAST, dependencias, imágenes y secretos en CI, además de
   pruebas DAST sobre un entorno efímero.
5. Define retención del log de auditoría, alertas, respuesta a incidentes,
   protección anti-DDoS y política de privacidad/RGPD.

Reporta vulnerabilidades de forma privada al responsable del repositorio; no
incluyas datos reales, tokens ni credenciales en un issue público.
