# Actualizaciones con el código privado

El código de Minu vive en este repositorio, privado. Las APK se publican además en **[Minu-releases](https://github.com/Yadirtf/Minu-releases)**, un repositorio público que solo guarda las versiones (sin código). Obtainium lee ese repositorio, así que las actualizaciones siguen siendo gratis y automáticas.

No hay que subir nada a mano: el workflow **APK firmada** compila, firma y publica la versión en los dos repositorios.

Haz los pasos en este orden. El repositorio principal se pone privado al final, cuando todo ya funciona.

## 1. Crear el repositorio de actualizaciones

1. Entra a https://github.com/new
2. **Repository name:** `Minu-releases`
3. Marca **Public**.
4. Marca **Add a README file** (el repositorio necesita al menos un commit para poder crear versiones).
5. **Create repository**.

Si quieres, edita el README con algo como: "Versiones de Minu. Instálala con Obtainium: Add app → `https://github.com/Yadirtf/Minu-releases`. Necesitas una cuenta autorizada para usarla."

## 2. Crear el token que deja publicar ahí

1. GitHub → tu foto → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**.
2. **Token name:** `Minu releases`.
3. **Expiration:** la más larga que te deje. Anota la fecha: cuando venza, el workflow falla con un error 401 y solo hay que crear otro.
4. **Repository access:** **Only select repositories** → `Minu-releases`.
5. **Permissions** → **Repository permissions** → **Contents:** `Read and write`. Nada más.
6. **Generate token** y copia el texto (empieza con `github_pat_`). GitHub no lo vuelve a mostrar.

El token solo puede escribir en `Minu-releases`. Aunque se filtrara, no da acceso al código.

## 3. Guardar el token en este repositorio

En **Minu** → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Nombre | Valor |
|---|---|
| `RELEASES_TOKEN` | el token del paso 2 |

Si algún día cambias el nombre del repositorio de actualizaciones, crea en la pestaña **Variables** la variable `RELEASES_REPO` con `Yadirtf/<nuevo-nombre>`.

## 4. Publicar una versión de prueba

**Actions** → **APK firmada** → **Run workflow** → escribe una versión nueva (mayor que la última) → **Run workflow**.

En unos 10 minutos la versión aparece en https://github.com/Yadirtf/Minu-releases/releases con la APK adjunta.

## 5. Cambiar Obtainium en cada celular

En cada celular que ya tiene Minu:

1. Abre Obtainium, mantén presionado Minu y elige quitarlo de la lista. **No desinstales la app.**
2. **Add app** → `https://github.com/Yadirtf/Minu-releases` → **Add**.
3. Obtainium detecta la versión instalada. Si hay una más nueva, toca **Update**: como la firma es la misma, se instala encima sin perder nada.

A los clientes nuevos solo les compartes esa dirección.

## 6. Poner privado este repositorio

Cuando el paso 4 funcionó y los celulares ya apuntan a `Minu-releases`:

**Minu** → **Settings** → **General** → al final, **Danger Zone** → **Change repository visibility** → **Make private**.

A partir de ahí, si falta `RELEASES_TOKEN`, el workflow se detiene con un error en vez de publicar una versión que nadie recibiría.

## Bueno saber

- Con el repositorio privado, GitHub Actions da 2.000 minutos gratis al mes. Cada APK tarda unos 10 a 15 minutos, así que alcanza de sobra.
- Las versiones viejas que estaban en los Releases de este repositorio dejan de verse en público. No hace falta copiarlas: los celulares solo necesitan la última.
- Los secrets, los workflows y la rama `capturas-minu` siguen funcionando igual con el repositorio privado.
