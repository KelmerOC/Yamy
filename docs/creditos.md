# Créditos Oficiales

## Proyecto Base: Mural

**Yamy** (`https://github.com/KelmerOC/Yamy`) es una aplicación de código abierto **basada en Mural**, la aplicación nativa para aprender idiomas mediante conversación oral.

- **Creadores Originales:** **Hackmamba** y los colaboradores originales del proyecto **Mural**.
- **Licencia Original:** MIT License — `Copyright (c) 2026 Hackmamba`.

La arquitectura base de los clientes nativos (Android e iOS), el sistema de temas conversacionales, el seguimiento léxico mediante barras de recuerdo (`RecallBars`), los contratos compartidos y la estructura fundacional del proyecto provienen de **Mural**. Se conserva íntegramente el aviso de copyright original en [`LICENSE`](../LICENSE) y se agradece a **Hackmamba** y a todos los colaboradores originales de Mural (incluyendo la contribución del módulo de mandarín con pinyin por `richardguerre`).

## Evolución e Ingeniería en Yamy

Sobre la base de Mural, **Yamy** incorpora:

- **Integración Exclusiva con Google Gemini**:
  - Voz bidireccional en tiempo real a 16 kHz PCM mediante `models/gemini-3.8-live`.
  - Tutor Maestro, significados instantáneos y evaluaciones con `gemini-3.5-flash-lite`.
- **Expansión a 12 Idiomas Reales**: Español, Inglés, Noruego (`Bokmål`), Francés, Alemán, Italiano, Portugués, Chino Mandarín, Quechua Colla (`Runa Simi`), Coreano, Japonés e Hindi.
- **Calibración Pedagógica MCER (A1 a C2)** y bifurcación entre **Modo Educación** y **Modo Conversación**.
- **Suite de Evaluaciones Interactivas (Tests)** en 5 modalidades y selector de timbres vocales.
- **Aplicación Web Modular en React 19 + TypeScript + Vite** (`web/`) conectada al catálogo de datos del repositorio.

## Licencias de Terceros

- **Tipografía Nunito**: SIL Open Font License (`apps/android/app/src/main/assets/Nunito-OFL.txt`).
- **Diccionario `phrase-pinyin-data`**: Licencia MIT (`apps/android/app/src/main/resources/mandarin/`).
- **Avisos de componentes adicionales**: [`apps/android/app/src/main/assets/THIRD-PARTY-NOTICES.txt`](../apps/android/app/src/main/assets/THIRD-PARTY-NOTICES.txt).
