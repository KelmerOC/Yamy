# Página web

Sitio de presentación de Yamy en `web/`, construido con **React 19, TypeScript y Vite**.

## Comandos

```sh
cd web
npm install
npm run dev       # http://localhost:5173
npm run build     # genera web/dist
npm run preview   # http://localhost:4173
```

## Estructura

```text
web/
├── index.html
├── public/imagenes/      Capturas reales de la app servidas en /imagenes
├── imagenes/             Capturas originales de referencia
└── src/
    ├── main.tsx, App.tsx
    ├── components/       Header, HeroFullBleed, NebulaOrbCanvas, MultilingualTicker,
    │                     LanguagesStrip, AppShowcase9x16, ScenariosGrid,
    │                     InfrastructureFlow, AssessmentRecallSection,
    │                     InstallationGuide, MonumentalFooter, icons/MuralSvgIcons
    ├── data/             repositoryData.ts (idiomas, pantallas, escenarios), uiTranslations.ts
    ├── services/         catalogService.ts (consume /v1/public/catalog con respaldo local)
    ├── types/            catalog.ts
    └── styles/           tokens.css, layout.css, components.css, animations.css
```

## Reglas de diseño

- Sin estilos en línea: todo el estilo vive en `src/styles/`.
- Sin emojis: los iconos son SVG en `components/icons/MuralSvgIcons.tsx`.
- Una sola fuente: Nunito.
- Las vistas de la app mantienen proporción vertical de teléfono mediante CSS (`aspect-ratio`).
- Los datos (12 idiomas, 24 escenarios, pantallas) provienen del código de la app; no se inventan funciones.
- En móvil la navegación se abre con un menú de tres barras.

## Datos

`catalogService.ts` intenta leer `GET /v1/public/catalog` de `services/api`. Si no está disponible, usa `data/repositoryData.ts`, de modo que la web funciona como sitio estático.
