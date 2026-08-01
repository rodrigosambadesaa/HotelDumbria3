# Android

## Compatibilidad

Se mantiene `minSdk 16` para conservar el objetivo histórico. Esto obliga a:

- `NetworkInfo` exclusivamente en API 16–20 y `NetworkCapabilities` desde 21;
- TLS 1.2 explícito para API 16–20;
- core-library desugaring con Java 17;
- sesión recordada solo desde API 18, porque API 16/17 no ofrecen un almacén
  criptográfico adecuado;
- ramas modernas/antiguas de WebView y selector de documentos.

La instrumentación también soporta Dalvik: `androidx.tracing` se conserva en el
dex primario porque `AndroidJUnitRunner` lo usa antes de instalar los dex
secundarios. Las pruebas de Activities no dependen del proceso auxiliar de
`ActivityScenario`, que no inicializa multidex de forma fiable en API 16.

Subir avatar usa `ACTION_OPEN_DOCUMENT` desde API 19 y `ACTION_GET_CONTENT` en
versiones anteriores. Solo persiste el permiso URI cuando el proveedor concede
también `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`; después decodifica y reencodifica
PNG antes del upload. No necesita permiso de almacenamiento ni ubicación.

## Red y sesiones

`debug` usa `http://10.0.2.2:8080/api/v1`; Network Security Config limita el
tráfico claro a ese host desde API 24. En API 23 el sistema no admite reglas por
dominio: el manifest debug habilita cleartext y el único endpoint compilado
sigue siendo `10.0.2.2`; API 16–22 ignora este atributo. `release` exige una URL HTTPS mediante
`verifyReleaseApiUrl`, desactiva tráfico claro y activa R8/shrinkResources.

Las comprobaciones de las Activities son asíncronas y prueban primero DNS
(Cloudflare, Google, Quad9, OpenDNS), después Google, Facebook, Wolfram Alpha,
Apple y Amazon. Son secuenciales y se detienen tras el primer éxito. El callback
se entrega en el hilo principal y cada pantalla cancela su petición al salir.

Access token vive en memoria. Refresh se cifra con Keystore API 18+ y las
rotaciones 401 concurrentes se agrupan. JSON, avatar y descarga de avatar
reintentan una vez tras renovar; un segundo 401 cierra la sesión lógica.

## Mapa, backups y release

La localización fija de Dumbría no usa GPS. Se intenta Google Maps con clave de
build y se recurre a OpenStreetMap restringido. En Google Cloud restringe cada
clave por package `org.hoteldumbria`, SHA-1/SHA-256 de certificados debug/release
y Maps SDK for Android; usa cuotas y claves separadas. No versions una clave.

`allowBackup=false`, por lo que preferencias, tokens y datos personales no
entran en Auto Backup ni device-to-device transfer. Si se habilita en el futuro,
deben añadirse simultáneamente `fullBackupContent` y `dataExtractionRules` que
excluyan todos esos datos.

La release generada aquí usa firma debug si no se configura una firma externa:
sirve para comprobar R8, no para publicar. Configura el keystore en CI mediante
secretos y prueba el APK/AAB firmado en un dispositivo antes de distribuir.
