# Línea base de la auditoría técnica

Fecha de captura: 2026-08-01. Rama inspeccionada: `master`, commit inicial
`8e4b299`.

## Estructura confirmada

- `backend/`: API Fastify, PostgreSQL, migración SQL, outbox y tests Node.
- `admin-web/`: panel React/Vite y prueba de renderizado.
- `app/`: aplicación Android, tests JVM e instrumentación.
- `docker/`, `compose.yaml` y `scripts/`: compilación, servicios y emuladores.
- `legacy-import/`: importador aislado del sistema histórico.
- `README.md`, `EJECUCION.md` y `SECURITY.md`: documentación operativa.

## Resultado inicial reproducible

| Comprobación | Resultado inicial |
|---|---|
| `docker compose config --quiet` | Correcto |
| Build Docker del backend | Correcto; ejecuta sus tests con Node 22 |
| Build Docker del panel | Correcto; ejecuta tests y build Vite con Node 22 |
| `npm audit --omit=dev --audit-level=high` (backend) | 0 vulnerabilidades |
| `npm audit --omit=dev --audit-level=high` (panel) | 0 vulnerabilidades |
| Android `assembleDebug` y tests JVM | Correcto |
| Android `lintDebug` | Correcto, 0 errores y 190 advertencias iniciales |

El host tiene Node 20, mientras ambos paquetes exigen Node 22. Por ello el test
local del backend termina dentro del binario nativo Argon2 y el `node_modules`
local del panel no contiene Vite. No son fallos del código desplegable: las
imágenes fijadas a Node `22.23.1` compilan y prueban ambos proyectos. Las
comprobaciones de esta auditoría se ejecutan en Docker para evitar esa
inconsistencia del host.

## Problemas confirmados antes de modificar

- bloqueo de cuenta renovable mediante intentos durante el bloqueo;
- rotación de refresh token no transaccional y sin detección de reutilización;
- rutas de sesión web y móvil mezcladas;
- `trustProxy: true` y exención global de `127.0.0.1` en rate limiting;
- aceptación directa de `X-Request-ID`;
- verificación de correo mutadora por GET y dividida en dos consultas;
- ausencia de recuperación de contraseña;
- fechas de reserva sin límite de pasado, duración u horizonte;
- estados de reserva actualizables sin máquina de estados;
- borrado físico en cascada de personas y reservas;
- avatares almacenados sin reconstrucción y con carrera entre subidas;
- outbox limitado a un mensaje por ciclo y sin estado terminal;
- permisos de ubicación y backup Android innecesarios;
- URL HTTP debug y configuración release/R8 incompletas.

## Controles ya presentes y no eliminados

- Argon2id con los parámetros declarados;
- SQL parametrizado en las rutas inspeccionadas;
- exclusión GiST `[)` contra solapamientos activos;
- detección del tipo declarado de avatar por contenido;
- almacenamiento Android del refresh token mediante Keystore desde API 18;
- configuración de red Android debug limitada a `10.0.2.2`;
- secretos locales excluidos de Git y auditorías npm sin hallazgos.
