# Instalación de Yamy

Repositorio oficial: **https://github.com/KelmerOC/Yamy**

## 1. Obtener el código

```sh
git clone https://github.com/KelmerOC/Yamy.git
cd Yamy
```

También puedes descargar el archivo ZIP desde el botón **Code > Download ZIP** de este repositorio.

## 2. Aplicación Android (recomendado)

### Requisitos

- JDK 17 o superior (configura `JAVA_HOME`, o `org.gradle.java.home` en tu `~/.gradle/gradle.properties`; nunca dentro del repositorio).
- Android SDK 36 (Android Studio lo instala automáticamente).
- Teléfono con Android 8.0 (API 26) o superior.
- Una clave de API de Google Gemini, gratuita en [Google AI Studio](https://aistudio.google.com).

### Compilar

```sh
cd apps/android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

En Windows usa `gradlew.bat` en lugar de `./gradlew`. El APK se genera en:

```text
apps/android/app/build/outputs/apk/debug/app-debug.apk
```

### Instalar en el teléfono

**Opción A: copiar el APK.** Transfiere `app-debug.apk` al teléfono, ábrelo y permite la instalación.

**Opción B: por cable con `adb`.**

1. En el teléfono activa **Opciones de desarrollador > Depuración USB** (toca siete veces **Número de compilación** en *Información del teléfono*).
2. Conecta el cable USB y acepta la autorización en el teléfono.
3. Desde la carpeta `Yamy`:

```sh
adb devices -l
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

`-r` actualiza la aplicación conservando tu historial y vocabulario.

### Primer arranque

Abre **Yamy**, elige tu idioma de aprendizaje y el idioma de los significados, y guarda tu clave en **Ajustes > Cuenta de Google AI Studio**. Consulta el [manual de usuario](manual-de-usuario.md).

## 3. Backend API (`services/api`, opcional)

La aplicación móvil funciona de forma autónoma con tu propia clave de Google AI Studio. Si deseas ejecutar localmente el servidor backend (`services/api`):

```sh
cd services/api
npm ci
cp .env.example .env
npm test
npm run dev
```

Recuerda mantener tu archivo `.env` exclusivamente en tu entorno local (está excluido en `.gitignore`).
