# Hotel Dumbría

Modernización integral del proyecto de gestión hotelera:

- aplicación Android compatible con API 16–36;
- aplicación iOS SwiftUI independiente de Docker;
- API REST Node.js/Fastify y PostgreSQL;
- panel web React para gestionar reservas, habitaciones, usuarios y auditoría;
- correo de confirmación con cola y reintentos;
- migrador controlado desde el antiguo MySQL/PHP;
- compilación y tres emuladores Android dockerizados, ejecutados uno por vez.

La contraseña viaja únicamente en el cuerpo HTTPS de registro, login o
confirmación de borrado. El servidor la deriva con Argon2id
(`m=19456,t=2,p=1`); los clientes no almacenan contraseñas y los hashes SHA del
proyecto histórico no se importan.

Empieza por [EJECUCION.md](EJECUCION.md). El modelo de amenazas y controles se
documenta en [SECURITY.md](SECURITY.md), y la migración histórica en
[legacy/MIGRACION.md](legacy/MIGRACION.md).

## Estructura

| Ruta | Contenido |
|---|---|
| `app/` | cliente Android |
| `ios/` | cliente iOS SwiftUI |
| `backend/` | API, correo, migraciones PostgreSQL |
| `admin-web/` | administración web |
| `legacy-import/` | importador MySQL → PostgreSQL |
| `docker/android-emulator/` | imágenes AVD con KVM |
| `scripts/` | operación segura desde PowerShell/WSL2 |
| `legacy/` | trazabilidad y diagrama del sistema original |
