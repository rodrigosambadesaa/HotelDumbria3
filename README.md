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

La comprobación de Internet de Android incorpora
[`ConnectivityAndInternetAccess.java`](https://gist.github.com/rodrigosambadesaa/729cca29a031fef4e2f15751863b655f):
consulta secuencialmente Cloudflare, Google DNS, Quad9 y OpenDNS y se detiene
ante la primera respuesta DNS válida. Solo si fallan todos los servidores DNS
prueba, también en orden y deteniéndose tras el primer éxito, los dominios de
Google, Facebook, Wolfram Alpha, Apple y Amazon. Las Activities y Fragments
usan el API asíncrono y cancelan las comprobaciones pendientes al abandonar su
ciclo de vida visible.

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

La licencia y atribución de la implementación de conectividad importada se
conservan en [docs/CONNECTIVITY_LICENSE.md](docs/CONNECTIVITY_LICENSE.md).
