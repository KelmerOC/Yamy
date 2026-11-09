# Yamy — Guía de Arquitectura e Invariantes para Agentes (`AGENTS.md`)

## 1. Envío Conjunto de Cambios de Interfaz y Servidor

Al modificar la interfaz o las aplicaciones nativas de **Yamy**, verifica si la experiencia depende de cambios en el servidor (`services/api`): contratos de API, respuestas de error, prompts, capacidades, configuración, migraciones o permisos de tiempo de ejecución.

- Inspecciona la revisión actual y preserva la configuración privada, las funciones habilitadas, las credenciales y los datos retenidos.
- Prueba el comportamiento del servidor afectado y la compatibilidad entre la aplicación y el servidor antes de fusionar o desplegar.
- Despliega en un orden compatible: no actives una función en el cliente antes de que el comportamiento requerido esté disponible en el servidor.

---

## 2. Política Exclusiva de Modelos Google Gemini

**Yamy** utiliza estricta y exclusivamente modelos de **Google Gemini** en todas las plataformas (Android, iOS y Backend):
- **Voz Interactiva en Tiempo Real (`BidiGenerateContent` WebSocket)**: `models/gemini-3.8-live` (`gemini-3.8-live`).
- **Tutor Maestro, Significados y Evaluaciones (`generateContent` y SSE)**: `gemini-3.5-flash-lite` como modelo primario de alto rendimiento, con respaldo automático multi-candidato a `gemini-3.5-flash` y `gemini-3.8-flash` ante errores HTTP 404, 429 o 503.

Bajo ninguna circunstancia deben utilizarse modelos deprecados o experimentales antiguos en el código, pruebas o documentación.

---

## 3. Invariantes Protegidas de Audio, Voz y Entrada (NO ALTERAR)

Las siguientes funcionalidades centrales han sido verificadas al 100% en dispositivos físicos y deben preservarse sin alteración:

1. **Streaming Continuo de Micrófono**:
   - La captura de audio del micrófono (16 000 Hz, mono, PCM de 16 bits mediante `VOICE_COMMUNICATION` / `AudioRecord`) debe transmitirse de forma continua a `models/gemini-3.8-live` mientras la llamada esté activa (`!mutedState && setupComplete && !isGreeting`).
   - **Nunca** reintroducir compuertas de silencio en el cliente ni filtros de umbral RMS (`SPEECH_RMS_THRESHOLD`) que descarten tramas PCM antes de la transmisión. El VAD neuronal del servidor de Google gestiona los turnos conversacionales.
2. **Preamplificación por Software con Limitador Suave**:
   - Mantener la ganancia por software de `1.5f` (`applyGain`) con limitación suave a `32 000` en [`GeminiLiveTransport.kt`](apps/android/app/src/main/java/chat/mural/network/GeminiLiveTransport.kt) para que las voces bajas y micrófonos Bluetooth entreguen amplitud óptima sin recorte digital.
3. **Carga Dual para Entrada en Tiempo Real**:
   - En las transmisiones WebSocket, enviar siempre ambos campos: `audio: { mimeType, data }` y `mediaChunks: [{ mimeType, data }]` para compatibilidad universal con Google AI Studio.
4. **Entrada Oral y Escrita Simultánea**:
   - La conversación oral (streaming de micrófono) y el comentario escrito ("Dilo a tu manera" / inyección de turno `clientContent`) deben operar simultáneamente sin bloquearse entre sí.
5. **Saludo Inicial Autónomo**:
   - La IA saluda primero tras `session.started` inyectando un turno inicial con `turnComplete: true` en el idioma activo (`TeachingPolicy.greeting`), hablando de inmediato sin requerir que el estudiante hable primero.
6. **Optimización de Latencia y Modo de Pensamiento del Maestro**:
   - Las consultas a `gemini-3.5-flash-lite` / `gemini-3.5-flash` configuran `thinkingConfig: { thinkingLevel: "low" }` para minimizar la latencia de razonamiento a ~1s y evitar el agotamiento de tokens.
   - Asignar límites amplios de tokens (`maxOutputTokens: 500` para significados, `1000` para respuestas escritas, `2000` para temas).
   - Forzar `responseMimeType: "application/json"` en la creación de temas y solicitudes con esquema JSON.
7. **Prevención de Regresiones**:
   - No alterar ni eliminar las pruebas unitarias existentes en `apps/android/app/src/test/` ni modificar las tuberías centrales de audio.

---

## 4. Reglas de Oro de Conducta Pedagógica y Juego de Roles

1. **Cumplimiento Estricto del Rol (Anti-Breakout)**:
   - La IA mantiene el 100% de adherencia a la situación definida por el usuario, el rol del personaje, el tema y el idioma activo.
   - **Nunca romper el personaje** ni admitir ser un modelo genérico de IA. Si el usuario hace preguntas fuera de contexto, reconducir suavemente la conversación dentro del personaje y la situación.
   - **Excepción de Asistencia Lingüística**: Si el estudiante pregunta explícitamente cómo se dice una palabra o expresión (*"¿Cómo se dice X?"*), explicarla brevemente en el idioma objetivo e incorporarla de inmediato al escenario en curso sin abandonar el rol.
2. **Modos Pedagógicos (Bifurcación Pedagógica)**:
   - **Modo Educación (`education`)**: Microcorrecciones activas (máximo 1 corrección clave por turno) seguidas inmediatamente de una respuesta conversacional para mantener el diálogo en movimiento.
   - **Modo Conversación (`conversation`)**: Inmersión total con flujo natural, sin interrupciones gramaticales.
3. **Calibración Estricta de Niveles MCER (A1 a C2)**:
   - **A1**: Frases ultracortas (3–7 palabras), tiempo presente, vocabulario cotidiano y cadencia pausada (~100–110 ppm).
   - **A2**: Oraciones simples, pasado/futuro básico y conectores elementales.
   - **B1**: Tiempos pasados conversacionales, expresión de opiniones y condicional básico (~130 ppm).
   - **B2**: Cláusulas complejas, subjuntivo/voz pasiva y expresiones idiomáticas (~140 ppm).
   - **C1/C2**: Velocidad nativa (~160–180 ppm), vocabulario matizado, modismos culturales y debates avanzados.

---

## 5. Estándares de UI/UX e Iconografía

1. **Iconos Vectoriales Puros (`MuralIcons.kt`)**:
   - Nunca utilizar emojis de texto como iconos o botones de la interfaz. Toda la iconografía de la aplicación (Corazón, Micrófono, Teclado, Traducir/Significado, Ajustes, Cerrar, Reproducir/Detener, Birrete, Globos de chat) debe usar vectores nativos Canvas/Bézier en `MuralIcons.kt`.
2. **Modo Enfoque Vocal (Voz Limpia)**:
   - Mantener el interruptor en Ajustes para ocultar el texto de transcripción del usuario en pantalla, ofreciendo una interfaz de práctica oral libre de distracciones centrada en la esfera nebulosa y la voz del tutor.

---

## 6. Arquitectura Multiplataforma y Comandos de Verificación

1. **Android (`apps/android`)**:
   - Stack: Kotlin, Jetpack Compose, Coroutines & Flow, AudioRecord / AudioTrack, OkHttp WebSocket.
   - Pruebas unitarias: `./gradlew testDebugUnitTest` en `apps/android`.
   - Compilar APK: `./gradlew assembleDebug` en `apps/android`.
   - Regla de regresión: Todas las pruebas unitarias en `apps/android/app/src/test/` deben aprobar al 100%.
2. **iOS (`apps/ios`)**:
   - Stack: Swift, SwiftUI, AVFoundation, URLSessionWebSocketTask.
   - Pruebas del paquete Core: `swift test --package-path apps/ios`.
3. **Backend (`services/api`)**:
   - Stack: Node.js, TypeScript, Fastify, proxy de streaming WebSocket, PostgreSQL.
   - Verificación de tipos y pruebas: `npm run check` y `npm test` en `services/api`.
4. **Contratos y Paridad Multiplataforma (`scripts/` y `shared/`)**:
   - Verificación de scripts y paridad: `python3 -m unittest discover -s scripts/tests -t .` y `python3 scripts/check_cross_platform.py`.

---

## 7. Créditos Oficiales

**Yamy** es una aplicación de código abierto **basada en Mural**, creada originalmente por **Hackmamba** y sus colaboradores originales bajo la licencia MIT. Consulta [`LICENSE`](LICENSE) y [`docs/creditos.md`](docs/creditos.md).
