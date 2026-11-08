# Motion Graphics & Video Generation (Remotion)

Entorno de producción audiovisual y animación programática para **Mural (Yamy)** basado en **Remotion 4.0**, React 19 y TypeScript.

---

## 1. Ubicación del Proyecto

- **Directorio raíz de Remotion**: [`motion-graphics/remotion-mural`](remotion-mural)

---

## 2. Habilidades Oficiales de Remotion (Skills)

El entorno cuenta con el conjunto oficial de Remotion Skills instalado a nivel global para agentes de IA:

- `remotion-best-practices`: Reglas de arquitectura, diseño determinístico y rendimiento en React/Remotion.
- `remotion-create`: Creación de nuevas composiciones, secuencias y proyectos.
- `remotion-render`: Comandos de renderizado headless, parámetros de concurrencia y codecs.
- `remotion-studio`: Inspección y depuración interactiva en el navegador.
- `remotion-docs`: Referencia técnica y búsqueda en la documentación oficial de Remotion.
- `remotion-captions`: Manejo de subtítulos, transcripciones y sincronización temporal.
- `remotion-interactivity`: Manejo de transiciones y eventos.
- `remotion-multimedia`: Procesamiento de audio, SFX y video.
- `remotion-markup`, `remotion-maps`, `remotion-saas`, `remotion-upgrade`.

---

## 3. Comandos de Uso

Dentro del directorio `motion-graphics/remotion-mural`:

```powershell
# 1. Iniciar Remotion Studio para vista previa interactiva
npm run dev

# 2. Renderizar una composición a video MP4
npx remotion render <composition-id> out/<nombre_archivo>.mp4

# 3. Comprobar sintaxis y tipos TypeScript
npx tsc --noEmit

# 4. Listar composiciones disponibles
npx remotion compositions
```
