<div align="center">

<img src="apps/android/app/src/main/res/drawable-nodpi/mural_icon.png" width="140" alt="Esfera nebulosa de Yamy" />

# Yamy

**Tu asistente de aprendizaje de idiomas. Habla, escucha y aprende conversando.**

[![Licencia MIT](https://img.shields.io/badge/licencia-MIT-2484E0?style=flat-square)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-091838?style=flat-square&logo=android&logoColor=white)](docs/instalacion.md)
[![Kotlin + Compose](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](apps/android)
[![Google Gemini](https://img.shields.io/badge/Google-Gemini%20Live-F6D04F?style=flat-square&logo=google&logoColor=091838)](docs/arquitectura.md)
[![12 idiomas](https://img.shields.io/badge/idiomas-12-2484E0?style=flat-square)](#idiomas)

[Instalación](docs/instalacion.md) · [Manual de usuario](docs/manual-de-usuario.md) · [Arquitectura](docs/arquitectura.md) · [Página web](docs/web.md) · [Créditos](#creditos)

</div>

---

## Qué es Yamy

Yamy es una aplicación nativa para aprender idiomas **mediante conversación oral**. Hablas con una esfera nebulosa animada que te escucha en tiempo real, te responde con voz natural y te ayuda con el significado justo cuando lo necesitas. Tus conversaciones, palabras y progreso se guardan **solo en tu dispositivo**.

<table>
  <tr>
    <td align="center" width="20%"><img src="web/imagenes/Inicio.jpg" alt="Hablar" /><br /><b>Hablar</b><br /><sub>Conversación por voz con la esfera</sub></td>
    <td align="center" width="20%"><img src="web/imagenes/Temas.jpg" alt="Temas" /><br /><b>Temas</b><br /><sub>Escenarios y temas propios</sub></td>
    <td align="center" width="20%"><img src="web/imagenes/Test.jpg" alt="Tests" /><br /><b>Tests</b><br /><sub>Evaluaciones de práctica</sub></td>
    <td align="center" width="20%"><img src="web/imagenes/Palabras.jpg" alt="Palabras" /><br /><b>Palabras</b><br /><sub>Vocabulario con barras de recuerdo</sub></td>
    <td align="center" width="20%"><img src="web/imagenes/Opciones.jpg" alt="Ajustes" /><br /><b>Ajustes</b><br /><sub>Idiomas, voz y modos</sub></td>
  </tr>
</table>

## Funciones principales

| Función | Descripción |
| :--- | :--- |
| **Conversación oral en vivo** | Audio continuo a 16 kHz con `gemini-3.8-live`. La IA saluda primero y puedes hablar o escribir en cualquier momento ("Dilo a tu manera"). |
| **Significado al instante** | Subtítulos de significado en el idioma que elijas, con un toque. |
| **Temas y escenarios** | 24 escenarios cotidianos (café, mercado, viajes, vecinos, opiniones...) y creación de temas propios con el Maestro. |
| **Niveles MCER A1 a C2** | El ritmo, el vocabulario y la gramática se ajustan a tu nivel. |
| **Modo educación / conversación** | Microcorrecciones durante la charla o inmersión total con correcciones al final. |
| **Tests** | Opción múltiple, banco de palabras, comprensión lectora, corrección y expresión oral. |
| **Palabras** | Vocabulario recogido de tus conversaciones con barras de recuerdo. |
| **Privacidad** | Tu clave de API se cifra con Android Keystore; el historial vive en el teléfono y nunca incluye la clave en las copias. |

<a id="idiomas"></a>

## Idiomas

Español, English, Norsk (Bokmål), Français, Deutsch, Italiano, Português, 普通话 (Mandarín con pinyin), Runa Simi (Quechua Cusco-Collao), 한국어, 日本語 y हिन्दी.

## Instalación rápida (Android)

Necesitas JDK 17 o superior, Android SDK 36 y un teléfono con Android 8.0 o superior.

```sh
git clone https://github.com/KelmerOC/Yamy.git
cd Yamy/apps/android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

El APK se genera en `apps/android/app/build/outputs/apk/debug/app-debug.apk`. Instálalo en tu teléfono y, en **Ajustes > Cuenta de Google AI Studio**, guarda tu clave de API de Gemini (se obtiene gratis en [Google AI Studio](https://aistudio.google.com)).

La guía completa, con instalación por cable (`adb`), iOS y la página web, está en [docs/instalacion.md](docs/instalacion.md).

## Estructura del repositorio

```text
Yamy/
├── apps/
│   ├── android/        App nativa Android (Kotlin, Jetpack Compose)
│   └── ios/            Código fuente iOS (Swift, SwiftUI)
├── services/api/       API opcional (Node.js, TypeScript, Fastify)
├── web/                Página web (React 19, TypeScript, Vite)
├── shared/             Contratos y datos de prueba compartidos
├── scripts/            Utilidades de generación y verificación
├── motion-graphics/    Animaciones de marca (Remotion)
└── docs/               Documentación técnica y manual de usuario
```

## Documentación

- [Instalación](docs/instalacion.md): Android, iOS, página web y API.
- [Manual de usuario](docs/manual-de-usuario.md): cómo usar cada pestaña de la app.
- [Arquitectura](docs/arquitectura.md): componentes, modelos de IA y flujo de datos.
- [Página web](docs/web.md): estructura y comandos del sitio en React.
- [Contribuir](CONTRIBUTING.md) y [Seguridad](SECURITY.md).

<a id="creditos"></a>

## Créditos

Yamy es una aplicación de código abierto **basada en Mural**, la aplicación nativa para aprender idiomas conversando creada originalmente por **Hackmamba** y los colaboradores de Mural. Yamy conserva su licencia MIT y su aviso de copyright original, reconociendo a sus creadores originales el trabajo fundacional sobre el que se construye este proyecto. Más detalles en [docs/creditos.md](docs/creditos.md).

## Licencia

Distribuido bajo la [licencia MIT](LICENSE). Los componentes de terceros conservan sus propias licencias.
