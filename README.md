# Minu

Reproductor de música personal para Android (Kotlin + Jetpack Compose) que reproduce desde YouTube con NewPipe Extractor, descarga para escuchar sin conexión, respalda la biblioteca en la nube y permite escuchar música en pareja.

- `app/` — la app de Android.
- `server/` — servidor Node.js (Express + WebSocket) con MongoDB Atlas: cuentas, respaldo de playlists, pareja, dedicatorias y "escuchar juntos".
- `render.yaml` — despliegue del servidor en Render.

## 1. Base de datos en MongoDB Atlas (gratis)

1. En [cloud.mongodb.com](https://cloud.mongodb.com) crea un clúster **M0 (Free)**.
2. **Database Access** → crea un usuario con contraseña.
3. **Network Access** → agrega `0.0.0.0/0` (Render no tiene IP fija).
4. **Connect → Drivers** → copia la cadena `mongodb+srv://usuario:contraseña@.../?retryWrites=true&w=majority`.

## 2. Servidor en Render

1. Sube este repositorio a GitHub (privado):
   ```bash
   git remote add origin https://github.com/TU_USUARIO/minu.git
   git push -u origin main
   ```
2. En [render.com](https://render.com): **New → Blueprint** → elige el repositorio. Render lee `render.yaml`.
3. Te pedirá:
   - `MONGODB_URI`: la cadena de Atlas.
   - `REGISTRATION_CODE`: un código secreto que inventes. Se pide al crear una cuenta, así nadie más puede registrarse en tu servidor.
   - `JWT_SECRET` se genera solo.
4. Cuando termine, copia la URL del servicio (por ejemplo `https://minu-server.onrender.com`) y ábrela: debe responder `{"app":"minu","ok":true}`.

> En el plan gratis Render duerme el servidor tras 15 minutos sin uso. La primera conexión después puede tardar alrededor de un minuto; la app lo avisa.

## 3. Conectar la app con el servidor

En `gradle.properties` cambia:

```properties
minu.serverUrl=https://TU-SERVICIO.onrender.com
```

y vuelve a compilar. (También se puede cambiar dentro de la app: Ajustes → Cuenta y nube → Servidor.)

## 4. Compilar el APK firmado

La firma usa `keystore/reproductor-release.jks` y `keystore.properties`. **Ninguno de los dos se sube a git.** Guarda una copia de ambos en un lugar seguro: sin ellos no podrás instalar actualizaciones encima de la app.

```bash
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.

## Uso en dos celulares

1. Cada persona instala Minu y crea su cuenta (Ajustes → Cuenta y nube) con el código de invitación.
2. En la pestaña **Juntos**, uno escribe el usuario del otro y el otro acepta la invitación.
3. Ya pueden **dedicarse canciones** (con mensaje, y si la otra persona tiene Minu abierta suena al instante), mandarse **reacciones** y **escuchar juntos**: quien controla pone la música y el otro celular la sigue sincronizada.

Si pierdes el celular: instala Minu, inicia sesión con tu usuario y tus playlists vuelven solas. Las descargas se bajan de nuevo desde cada playlist.

## Desarrollo

- Pruebas de la app: `./gradlew testDebugUnitTest`
- Capturas de todos los estilos sin teléfono: `./gradlew testDebugUnitTest -Pscreenshots --tests "*StyleScreenshots*"`
- Pruebas del servidor: `cd server && npm install && npm test`
- Cuando YouTube cambie algo y deje de reproducir, actualiza `newpipeExtractor` en `gradle/libs.versions.toml`.
