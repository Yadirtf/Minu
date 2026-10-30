# APK firmada con GitHub Actions

El workflow `.github/workflows/apk-release.yml` compila la APK release y la firma con **tu keystore de siempre**. Así el SHA-1 no cambia, Firebase y "Continuar con Google" siguen funcionando, y la APK se instala encima de la que ya tienes.

## 1. ¿Qué archivos son?

| Archivo | Qué es | ¿Va a GitHub? |
|---|---|---|
| `keystore.properties` | Texto con **rutas y contraseñas**. No es la llave. | No. Solo copias sus valores a Secrets. |
| `keystore/reproductor-release.jks` (o `.keystore`) | **La llave de firma real.** Es la que define el SHA-1. | Sí, en base64, como secret `KEYSTORE_BASE64`. |
| `app/google-services.json` | Configuración de Firebase para la app. | Sí, en base64, como secret `GOOGLE_SERVICES_JSON_BASE64`. |
| `minu-apk-firebase-adminsdk-....json` | Credencial **del servidor** (cuenta de servicio). | **No.** Nada que ver con este flujo. Va solo en Render. |

### Encontrar el `.jks`

Abre `keystore.properties` con el Bloc de notas. Se ve así:

```properties
storeFile=keystore/reproductor-release.jks
storePassword=********
keyAlias=reproductor
keyPassword=********
```

`storeFile` es la ruta del archivo de la llave, relativa a la carpeta del proyecto (la misma donde está `keystore.properties`). Normalmente es `keystore/reproductor-release.jks`. Si no lo ves, búscalo:

- Windows (PowerShell, en la carpeta del proyecto): `Get-ChildItem -Recurse -Include *.jks,*.keystore`
- Linux / Mac: `find . -name "*.jks" -o -name "*.keystore"`

Si no aparece en ningún lado, no sigas: un keystore nuevo cambia el SHA-1 y la app no se podrá actualizar encima de la instalada. Avísame y vemos qué hacer.

### El JSON `firebase-adminsdk`

Es la llave privada con la que **el servidor** manda notificaciones push. Se pega completo en Render, en la variable `FIREBASE_SERVICE_ACCOUNT` (ver `render.yaml`). **No** se sube a GitHub (ni al repo ni a Secrets de este flujo); el `.gitignore` ya lo bloquea. Si algún día se filtra, revócalo en Firebase → Configuración del proyecto → Cuentas de servicio.

### `google-services.json`

Lo descargas de Firebase → Configuración del proyecto → Tus apps → la app Android (`com.adrian.minu`) → **google-services.json**. Sin él la APK compila igual, pero sin notificaciones push.

## 2. Convertir a base64

El resultado es una sola línea larga de texto. Cópiala entera.

**Windows (PowerShell)**, en la carpeta del proyecto:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\keystore\reproductor-release.jks")) | Set-Clipboard
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\app\google-services.json")) | Set-Clipboard
```

Cada comando deja el texto en el portapapeles: pégalo en el secret antes de ejecutar el siguiente.

**Linux:**

```bash
base64 -w 0 keystore/reproductor-release.jks
base64 -w 0 app/google-services.json
```

**Mac:**

```bash
base64 -i keystore/reproductor-release.jks | pbcopy
base64 -i app/google-services.json | pbcopy
```

## 3. Crear los Secrets

En GitHub: repo **Minu → Settings → Secrets and variables → Actions → New repository secret**. Crea estos:

| Nombre | Valor |
|---|---|
| `KEYSTORE_BASE64` | base64 del `.jks` |
| `KEYSTORE_PASSWORD` | `storePassword` de `keystore.properties` |
| `KEY_ALIAS` | `keyAlias` de `keystore.properties` |
| `KEY_PASSWORD` | `keyPassword` de `keystore.properties` |
| `GOOGLE_SERVICES_JSON_BASE64` | base64 de `google-services.json` (recomendado) |
| `GOOGLE_WEB_CLIENT_ID` | "ID de cliente web" para "Continuar con Google". Opcional si tu `google-services.json` ya lo trae (la app lo toma de ahí); si el workflow avisa que falta, agrégalo. Está en Firebase → Authentication → Método de acceso → Google → Configuración del SDK web, termina en `.apps.googleusercontent.com` |

Los nombres deben ser exactos (mayúsculas incluidas). GitHub nunca vuelve a mostrar un secret; si te equivocas, lo reemplazas.

## 4. Generar la APK

- **Publicar una versión (lo normal):** pestaña **Actions → APK firmada → Run workflow**, escribe la versión (por ejemplo `1.2`) y pulsa **Run workflow**. En unos 10 minutos aparece en **Releases** como `Minu v1.2` con la APK adjunta, y Obtainium la detecta.
  - No hay que editar `app/build.gradle.kts`: el `versionCode` sale de la versión (`1.2` → `10200`, `1.2.3` → `10203`).
  - Cada versión debe ser mayor que la anterior; si repites una, el workflow falla y te avisa.
  - También funciona con un tag desde la terminal: `git tag v1.2 && git push origin v1.2`.
- **Solo compilar para probar:** lo mismo pero con la versión vacía. La APK queda abajo en **Artifacts** (30 días) y no se publica Release.

## 5. Comprobar el SHA-1

En el log del paso **Restaurar keystore** se imprimen el SHA1 y el SHA256 del keystore. Deben ser iguales a los que tienes en Firebase → Configuración del proyecto → Tus apps → Huellas digitales del certificado. Si el paso falla con "password was incorrect" o "Alias does not exist", revisa `KEYSTORE_PASSWORD` y `KEY_ALIAS`.

La compilación local con `keystore.properties` sigue funcionando igual que antes.
