# Informe de auditoría técnica

Fecha de cierre: 2026-08-02. Punto de partida: rama `master`, commit `8e4b299`.
La captura previa a los cambios se conserva en `AUDIT_BASELINE.md`.

## Hallazgos confirmados y corregidos

- autenticación vulnerable a bloqueo renovable, refresh no atómico y sesiones
  web/móvil mezcladas;
- ausencia de detección de reutilización, revocación tras cambios críticos,
  recuperación de contraseña y verificación POST transaccional;
- confianza indiscriminada en proxies, rate limit eludible, request IDs sin
  validar y exposición potencial de secretos en logs;
- reglas incompletas para reservas, cancelación, estados y paginación;
- borrado físico que eliminaba `persona`/historial de forma incorrecta;
- carrera y almacenamiento inseguro de avatares;
- outbox de un solo mensaje sin coordinación ni estado terminal;
- configuración Android incoherente entre HTTP debug/release, permisos y
  backup innecesarios, ciclo de vida, sesión y selección de imágenes;
- panel web con flujo de refresh frágil ante carreras y sin paginación real;
- migrador susceptible a dos ejecutores simultáneos.

Las soluciones y contratos resultantes se detallan en `SECURITY.md`,
`backend/README.md`, `ARCHITECTURE.md`, `ANDROID.md` y `API_CHANGES.md`.

## Puntos revisados que ya estaban resueltos

- PostgreSQL ya tenía una exclusión GiST con rango `[)` para impedir
  solapamientos activos;
- las rutas inspeccionadas ya utilizaban parámetros SQL en vez de concatenar
  entradas, aunque faltaba el endpoint moderno de códigos postales;
- las contraseñas nuevas ya usaban Argon2id; se conservaron y fijaron
  explícitamente los parámetros OWASP, el rehash y la migración sin importar
  hashes débiles históricos;
- la URL debug ya estaba orientada a `10.0.2.2`; faltaba hacer coherentes el
  manifest por versión, la validación release y las pruebas;
- el refresh recordado usaba Keystore en versiones compatibles; se completó la
  política API 16/17 sin persistencia y la protección ante carreras.

## Evidencia final

| Área | Resultado |
|---|---|
| Backend unitario/seguridad | 24 tests: 23 correctos, 1 integración omitida sin URL de test |
| Backend con PostgreSQL 17 real | 12/12 correctos |
| Migraciones `001` + `002` | aplican, se reejecutan de forma idempotente |
| Dependencias backend | 0 vulnerabilidades de producción |
| Panel web | test, TypeScript y Vite correctos; 0 vulnerabilidades de producción |
| Android JVM/lint/debug/tests/release R8 | build completo correcto, 118 tareas |
| Android instrumentado API 16 | 7/7 correctos |
| Android instrumentado API 29 | 7/7 correctos |
| Android instrumentado API 36 | 7/7 correctos |
| URL release HTTP | rechazo esperado y comprobado |
| Docker Compose | configuración de todos los perfiles válida |

Las instrumentaciones cubren permisos, cleartext moderno, token recordado,
recreación de pantallas, URI `content://` no persistible y comprobación real de
Internet DNS-first con callback asíncrono. API 16, 29 y 36 son los extremos y el
punto histórico solicitado; no equivalen a probar literalmente cada nivel API.

## Riesgos residuales

- iOS está implementado en SwiftUI pero no se puede compilar ni ejecutar en
  Windows; debe verificarse con Xcode según `ios/README.md`;
- el envío SMTP usa entrega *at least once*: `Message-ID` estable reduce
  duplicados, pero el servidor receptor debe tolerarlos;
- una release Android publicable requiere keystore de CI y claves de Google
  Maps restringidas fuera del repositorio;
- API 16 se conserva por compatibilidad histórica, sin persistir sesión en API
  16/17. Para un producto nuevo se recomienda elevar el mínimo a API 23;
- la retención de reservas tras anonimización es una decisión técnica de
  minimización, no asesoramiento jurídico, y debe alinearse con la política real
  del negocio antes de producción.
