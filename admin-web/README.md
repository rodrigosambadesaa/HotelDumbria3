# Administración web

SPA React/Vite para personal autorizado. Consume exclusivamente la API REST y
mantiene el token de acceso en memoria; la renovación usa cookie `HttpOnly` y
protección CSRF.

Desde la raíz del repositorio:

```powershell
.\scripts\backend.ps1 Start
```

La interfaz queda disponible en `http://localhost:3000`. Las credenciales
iniciales se generan en `.env` con `scripts/dev-setup.ps1`; cámbialas antes de
publicar el sistema.

Comprobaciones locales:

```bash
npm ci
npm test
npm run build
npm audit --omit=dev
```
