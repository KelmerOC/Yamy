# Yamy para Android

Cliente nativo de **Yamy** desarrollado en **Kotlin** y **Jetpack Compose** para Android 8.0 (API 26) o superior, basado en la arquitectura de **Mural** (Hackmamba).

Ofrece conversación oral en tiempo real a 16 kHz (`models/gemini-3.8-live`), tutor Maestro y evaluaciones (`gemini-3.5-flash-lite`), 12 idiomas de aprendizaje, 24 escenarios situacionales calibrados por nivel MCER (A1 a C2), subtítulos de significado instantáneo, vocabulario con barras de recuerdo (`RecallBars`) e historial 100% local utilizando la clave personal de Google AI Studio del usuario.

## Compilación y Pruebas

Consulta la [Guía de Instalación](../../docs/instalacion.md) y el [Documento de Arquitectura](../../docs/arquitectura.md).

```sh
git clone https://github.com/KelmerOC/Yamy.git
cd Yamy/apps/android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Requisitos: JDK 17+, Android SDK 36. La clave de API se introduce dentro de la aplicación (**Ajustes > Cuenta de Google AI Studio**) y se almacena cifrada en Android Keystore; nunca debe colocarse en archivos de Gradle ni en el repositorio.
