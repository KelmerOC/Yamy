# Instalación

Repositorio oficial: **https://github.com/KelmerOC/Yamy**

## 1. Obtener el código

```sh
git clone https://github.com/KelmerOC/Yamy.git
cd Yamy
```

También puedes descargar el ZIP desde el botón **Code > Download ZIP** del repositorio.

## 2. App Android (recomendado)

### Requisitos

- JDK 17 o superior (configura `JAVA_HOME`, o `org.gradle.java.home` en tu `~/.gradle/gradle.properties`; nunca en el repositorio).
- Android SDK 36 (Android Studio lo instala automáticamente).
- Teléfono con Android 8.0 o superior.
- Una clave de API de Google Gemini, gratuita en [Google AI Studio](https://aistudio.google.com).

### Compilar

```sh
cd apps/android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

En Windows usa `gradlew.bat` en lugar de `./gradlew`. El APK queda en:

```text
apps/android/app/build/outputs/apk/debug/app-debug.apk
```

### Instalar en el teléfono

**Opción A: copiar el APK.** Pasa `app-debug.apk` al teléfono, ábrelo y permite la instalación desde esa fuente.

**Opción B: por cable con `adb`.**

1. En el teléfono activa **Opciones de desarrollador > Depuración USB** (toca siete veces **Número de compilación** en *Información del teléfono*).
2. Conecta el cable y acepta la autorización del equipo.
3. Desde la carpeta `Yamy`:

```sh
adb devices -l
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

`-r` actualiza la app conservando tus datos. Para conservar el historial entre actualizaciones, no desinstales la app y usa siempre la misma firma de compilación.

### Primer arranque

Abre Yamy, elige tus idiomas y guarda tu clave en **Ajustes > Cuenta de Google AI Studio**. Consulta el [manual de usuario](manual-de-usuario.md).

## 3. Proyecto iOS

El código fuente de iOS (Swift, SwiftUI) está en `apps/ios`. Requiere un Mac con Xcode.

```sh
cd Yamy/apps/ios
open Mural.xcodeproj
```

En Xcode, añade tu cuenta de Apple en **Settings > Accounts**, elige tu equipo en **Signing & Capabilities** y un identificador de paquete propio, conecta el iPhone y pulsa **Run**. La plataforma verificada en dispositivo real es Android; trata la compilación iOS como experimental.

## 4. Página web

Requiere Node.js 20 o superior.

```sh
cd Yamy/web
npm install
npm run dev        # desarrollo en http://localhost:5173
npm run build      # compilación de producción en web/dist
npm run preview    # previsualizar la compilación en http://localhost:4173
```

Más detalles en [web.md](web.md).

## 5. API (opcional)

La app Android funciona sin servidor: se conecta directamente a Google Gemini con tu propia clave. La API de `services/api` solo es necesaria para el catálogo público de la web (`GET /v1/public/catalog`) y para funciones de servidor heredadas.

```sh
cd Yamy/services/api
npm install
npm test
```

Para ejecutarla localmente copia `.env.example` a `.env` y rellena tus propios valores. **Nunca subas `.env` al repositorio**: está excluido en `.gitignore`.
