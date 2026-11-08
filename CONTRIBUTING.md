# Guía de Contribución a Yamy

Gracias por tu interés en contribuir a **Yamy** (`https://github.com/KelmerOC/Yamy`).

## Flujo de Trabajo

1. Clona el repositorio oficial y configura tu entorno siguiendo la [Guía de Instalación](docs/instalacion.md):
   ```sh
   git clone https://github.com/KelmerOC/Yamy.git
   cd Yamy
   ```
2. Crea una rama descriptiva para tu cambio:
   ```sh
   git checkout -b feat/descripcion-corta
   ```
3. Mantén las invariantes arquitectónicas documentadas en [`AGENTS.md`](AGENTS.md) y [`docs/arquitectura.md`](docs/arquitectura.md):
   - Uso exclusivo de modelos Google Gemini (`models/gemini-3.8-live` para voz en tiempo real y `gemini-3.5-flash-lite` para el tutor Maestro, definiciones y evaluaciones).
   - En la aplicación web (`web/`): cero estilos en línea (`style=`), cero emojis Unicode (usar exclusivamente iconos vectoriales SVG en `MuralSvgIcons.tsx`) y fuente única Nunito.
4. Ejecuta las pruebas correspondientes antes de abrir un Pull Request:
   - **Android**: `cd apps/android && ./gradlew testDebugUnitTest`
   - **API**: `cd services/api && npm test`
   - **Web**: `cd web && npm run build`
5. Nunca incluyas claves de API, archivos `.env`, rutas locales absolutas ni certificados de firma en tus commits.

## Licencia y Créditos

Al contribuir código a este repositorio, aceptas distribuirlo bajo la [Licencia MIT](LICENSE), preservando los créditos oficiales al proyecto base **Mural** (Hackmamba y colaboradores originales) detallados en [`docs/creditos.md`](docs/creditos.md).
