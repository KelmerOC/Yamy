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
   - Uso exclusivo de modelos Google Gemini (`models/gemini-3.8-live` para voz interactiva en tiempo real y `gemini-3.5-flash-lite` para el tutor Maestro, definiciones y evaluaciones).
   - Preservación del streaming continuo de audio PCM a 16 kHz, ganancia por software de `1.5f` y carga dual en WebSocket.
   - Uso exclusivo de iconografía vectorial nativa en `MuralIcons.kt` (sin emojis de texto en controles de interfaz).
4. Ejecuta las pruebas correspondientes antes de abrir un Pull Request:
   - **Android**: `cd apps/android && ./gradlew testDebugUnitTest`
   - **Backend API**: `cd services/api && npm run check && npm test`
   - **Contratos y Paridad**: `python3 -m unittest discover -s scripts/tests -t . && python3 scripts/check_cross_platform.py`
5. Nunca incluyas claves de API, archivos `.env`, rutas locales absolutas ni certificados de firma en tus commits.

## Licencia y Créditos

Al contribuir código a este repositorio, aceptas distribuirlo bajo la [Licencia MIT](LICENSE), preservando los créditos oficiales al proyecto base **Mural** (Hackmamba y colaboradores originales) detallados en [`docs/creditos.md`](docs/creditos.md).
