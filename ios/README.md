# Hotel Dumbría para iOS

Este proyecto es independiente del entorno Docker Android. Requiere macOS,
Xcode 16 o posterior y [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
cd ios
brew install xcodegen
xcodegen generate
open HotelDumbria.xcodeproj
```

Selecciona un simulador iPhone, configura el equipo de firma si se va a instalar
en un dispositivo físico y ejecuta el esquema `HotelDumbria`.

El simulador usa por defecto `http://127.0.0.1:8080/api/v1`. Antes de una
compilación de distribución, sustituye `API_BASE_URL` en `project.yml` por el
endpoint HTTPS real. iOS no forma parte de ninguna imagen Docker: se genera y
compila únicamente en macOS.

Las contraseñas se envían únicamente a la API bajo TLS y el servidor las deriva
con Argon2id. El llavero de iOS almacena solo el refresh token cuando se activa
«Recordar sesión»; nunca almacena la contraseña.
