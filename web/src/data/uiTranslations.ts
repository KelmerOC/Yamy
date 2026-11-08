export interface UiTranslation {
  nav_home: string;
  nav_showcase: string;
  nav_scenarios: string;
  nav_infra: string;
  nav_tests: string;
  nav_install: string;
  github_btn: string;

  nav_menu_open: string;
  nav_menu_close: string;

  hero_title_prefix: string;
  hero_title_highlight: string;
  hero_title_suffix: string;
  hero_desc: string;
  btn_showcase: string;
  btn_install: string;
  btn_github: string;

  ticker_label: string;
  ticker_tag: string;

  strip_title: string;

  showcase_badge: string;
  showcase_title: string;
  showcase_lead: string;
  showcase_interactive_label: string;
  showcase_gallery_label: string;

  scenarios_badge: string;
  scenarios_title: string;
  scenarios_lead: string;
  cat_all: string;
  cat_everyday: string;
  cat_connection: string;
  cat_local: string;
  cat_interests: string;
  scenario_sample_greeting: string;
  scenario_situation: string;
  scenarios_show_more: string;
  scenarios_show_less: string;

  infra_badge: string;
  infra_title: string;
  infra_lead: string;
  card1_title: string;
  card1_desc: string;
  card2_title: string;
  card2_desc: string;
  card3_title: string;
  card3_desc: string;
  step1_title: string;
  step1_desc: string;
  step2_title: string;
  step2_desc: string;
  step3_title: string;
  step3_desc: string;
  step4_title: string;
  step4_desc: string;

  tests_badge: string;
  tests_title: string;
  tests_lead: string;
  recall_title: string;
  recall_desc: string;

  install_badge: string;
  install_title: string;
  install_lead: string;
  install_tab_android: string;
  install_tab_ios: string;
  install_key_title: string;
  install_key_desc: string;

  footer_copy: string;
  footer_rights: string;
  footer_credit: string;
  footer_credit_url: string;
  footer_repo: string;
  footer_issues: string;
  footer_aistudio: string;
}

export const UI_TRANSLATIONS: Record<string, UiTranslation> = {
  es: {
    nav_home: 'Inicio',
    nav_showcase: 'La app',
    nav_scenarios: 'Escenarios',
    nav_infra: 'Infraestructura',
    nav_tests: 'Tests y Palabras',
    nav_install: 'Instalación',
    github_btn: 'GitHub',

    nav_menu_open: 'Abrir menú de navegación',
    nav_menu_close: 'Cerrar menú de navegación',

    hero_title_prefix: 'Aprende un idioma hasta que ',
    hero_title_highlight: 'dejes de necesitar la app.',
    hero_title_suffix: '',
    hero_desc: 'Yamy es la aplicación nativa de conversación oral a 16 kHz mono. Conversas con un orbe esférico nebuloso que te escucha sin compuertas de silencio y resguarda tu memoria 100% en tu teléfono.',
    btn_showcase: 'Explorar la app',
    btn_install: 'Cómo instalar',
    btn_github: 'Repositorio GitHub',

    ticker_label: 'Tu asistente de aprendizaje de idiomas',
    ticker_tag: '12 Idiomas Reales',

    strip_title: '12 Idiomas de aprendizaje disponibles en la aplicación',

    showcase_badge: 'Vista previa en celular',
    showcase_title: '5 Pantallas reales de la aplicación',
    showcase_lead: 'Explora la interfaz táctil con las 5 capturas auténticas de la aplicación: Hablar, Temas, Tests, Palabras y Opciones.',
    showcase_interactive_label: 'Escenario interactivo principal',
    showcase_gallery_label: 'Todas las pantallas',

    scenarios_badge: 'Catálogo de Inmersión',
    scenarios_title: '24 Escenarios Reales de Conversación',
    scenarios_lead: 'Situaciones de habla cotidiana estructuradas en 4 paletas pasteles oficiales con calibración de nivel MCER de A1 a C2.',
    cat_all: 'Todos los escenarios',
    cat_everyday: 'Día a día',
    cat_connection: 'Conexión',
    cat_local: 'Vida local',
    cat_interests: 'Intereses',
    scenario_sample_greeting: 'Saludo inicial del tutor:',
    scenario_situation: 'Situación contextual:',
    scenarios_show_more: 'Mostrar más',
    scenarios_show_less: 'Mostrar menos',

    infra_badge: 'Infraestructura de Conversación',
    infra_title: 'Cómo procesa Yamy tu habla en tiempo real',
    infra_lead: 'Una arquitectura directa sin intermediarios opacos, optimizada para responder con latencia mínima y naturalidad.',
    card1_title: 'Tu Voz · 16 kHz PCM',
    card1_desc: 'Streaming continuo de audio mono a 16.000 Hz vía WebSocket sin compuertas VAD de software que corten tus palabras.',
    card2_title: 'Yamy Core · Gemini 3.8 Live',
    card2_desc: 'Google BidiGenerateContent procesa el habla con VAD neuronal del servidor, manteniendo un diálogo fluido y con acento nativo.',
    card3_title: 'Maestro Tutor · Gemini 3.5 Flash Lite',
    card3_desc: 'Tutoría de alta velocidad con thinkingLevel low (~1s), generación de situaciones a medida en JSON puro y evaluación sin pausas.',

    step1_title: '1. Captura Continua',
    step1_desc: 'El micrófono transmite tramas de 50 ms ininterrumpidas a la nube de Google.',
    step2_title: '2. Análisis de Intención',
    step2_desc: 'Gemini 3.8 Live distingue respiraciones y pausas naturales sin pérdida de dicción.',
    step3_title: '3. Bifurcación Pedagógica',
    step3_desc: 'Modo Educación (micro-correcciones activas) o Modo Conversación (inmersión total).',
    step4_title: '4. Memoria 100% Local',
    step4_desc: 'Tu progreso, vocabulario y grabaciones se resguardan únicamente en tu teléfono.',

    tests_badge: 'Evaluación y Memoria',
    tests_title: '5 Modalidades de Tests y Vocabulario por Evidencia',
    tests_lead: 'Genera exámenes basados en lo conversado recientemente y monitorea la retención con RecallBars.',
    recall_title: 'RecallBars: Evidencia Real de Recuerdo',
    recall_desc: 'Las palabras no se memorizan con listas vacías. Cada vez que recuerdas una palabra espontáneamente en una charla, suma solidez en sus 3 barras.',

    install_badge: 'Código Abierto y Compilación',
    install_title: 'Cómo compilar e instalar Yamy',
    install_lead: 'Yamy es software libre bajo licencia MIT. Solo necesitas tu propia clave de API personal de Google AI Studio.',
    install_tab_android: 'Android (Kotlin / Compose)',
    install_tab_ios: 'iOS (Swift / SwiftUI)',
    install_key_title: 'Configurar clave de Google AI Studio',
    install_key_desc: 'Genera tu clave gratuita en aistudio.google.com y pégala en Ajustes > Cuenta de Google AI Studio. Se guarda en Android Keystore o iOS Keychain.',

    footer_copy: 'Yamy · Aplicación de práctica oral nativa de código abierto bajo licencia MIT. Exclusivamente impulsada por Google Gemini 3.8 Live y 3.5 Flash Lite.',
    footer_rights: '© 2026 Yamy Project. Todos los derechos reservados.',
    footer_credit: 'Aplicación basada en Mural',
    footer_credit_url: 'https://github.com/KelmerOC/Yamy#creditos',
    footer_repo: 'Repositorio',
    footer_issues: 'Reportar un problema',
    footer_aistudio: 'Google AI Studio'
  },
  en: {
    nav_home: 'Home',
    nav_showcase: 'The app',
    nav_scenarios: 'Scenarios',
    nav_infra: 'Infrastructure',
    nav_tests: 'Tests & Recall',
    nav_install: 'Installation',
    github_btn: 'GitHub',

    nav_menu_open: 'Open navigation menu',
    nav_menu_close: 'Close navigation menu',

    hero_title_prefix: 'Learn a language until you ',
    hero_title_highlight: 'no longer need the app.',
    hero_title_suffix: '',
    hero_desc: 'Yamy is the native oral practice app streaming continuous 16 kHz audio. Talk to an interactive nebula orb that listens with zero silence gates and stores your memory 100% locally on your phone.',
    btn_showcase: 'Explore the app',
    btn_install: 'How to Install',
    btn_github: 'GitHub Repository',

    ticker_label: 'Your language learning assistant',
    ticker_tag: '12 Real Languages',

    strip_title: '12 Learning languages available in the application',

    showcase_badge: 'Mobile preview',
    showcase_title: '5 Real app screens',
    showcase_lead: 'Explore the mobile touch interface with 5 authentic application screenshots: Talk, Themes, Tests, Words, and Options.',
    showcase_interactive_label: 'Main interactive stage',
    showcase_gallery_label: 'All screens',

    scenarios_badge: 'Immersion Catalog',
    scenarios_title: '24 Real Conversation Scenarios',
    scenarios_lead: 'Everyday speech situations structured into 4 official pastel palettes with CEFR level calibration from A1 to C2.',
    cat_all: 'All scenarios',
    cat_everyday: 'Everyday',
    cat_connection: 'Connection',
    cat_local: 'Local life',
    cat_interests: 'Interests',
    scenario_sample_greeting: 'Tutor initial greeting:',
    scenario_situation: 'Contextual situation:',
    scenarios_show_more: 'Show more',
    scenarios_show_less: 'Show less',

    infra_badge: 'Conversation Infrastructure',
    infra_title: 'How Yamy processes your speech in real time',
    infra_lead: 'A direct architecture without opaque intermediaries, engineered for ultra-low latency and authentic cadence.',
    card1_title: 'Your Voice · 16 kHz PCM',
    card1_desc: 'Continuous 16,000 Hz mono audio streaming via WebSocket with no local silence gates cutting off your sentences.',
    card2_title: 'Yamy Core · Gemini 3.8 Live',
    card2_desc: 'Google BidiGenerateContent analyzes speech with server-side neural VAD, delivering fluid dialogue with native cadence.',
    card3_title: 'Master Tutor · Gemini 3.5 Flash Lite',
    card3_desc: 'High-speed tutoring with low thinking level (~1s), pure JSON topic synthesis and non-blocking evaluations.',

    step1_title: '1. Continuous Stream',
    step1_desc: 'The microphone transmits 50 ms frames uninterruptedly to Google Cloud.',
    step2_title: '2. Intent Analysis',
    step2_desc: 'Gemini 3.8 Live distinguishes breaths and pauses without dropping diction.',
    step3_title: '3. Pedagogical Fork',
    step3_desc: 'Education Mode (active micro-corrections) or Conversation Mode (full immersion).',
    step4_title: '4. 100% Local Storage',
    step4_desc: 'Your vocabulary, progress and session histories stay solely on your device.',

    tests_badge: 'Assessment & Retention',
    tests_title: '5 Test Modalities & Evidence-Based Vocabulary',
    tests_lead: 'Generate dynamic tests based on recent dialogues and monitor retention with RecallBars.',
    recall_title: 'RecallBars: Real Evidence of Memory',
    recall_desc: 'Words are not memorized with empty lists. Every time you recall a word spontaneously, it gains strength in its 3 bars.',

    install_badge: 'Open Source & Build',
    install_title: 'How to build and install Yamy',
    install_lead: 'Yamy is free software licensed under MIT. You only need your personal Google AI Studio API key.',
    install_tab_android: 'Android (Kotlin / Compose)',
    install_tab_ios: 'iOS (Swift / SwiftUI)',
    install_key_title: 'Configure Google AI Studio Key',
    install_key_desc: 'Generate your free key at aistudio.google.com and paste it in Settings > Google AI Studio Account. Saved in Android Keystore or iOS Keychain.',

    footer_copy: 'Yamy · Native open-source oral language practice app under MIT License. Exclusively powered by Google Gemini 3.8 Live and 3.5 Flash Lite.',
    footer_rights: '© 2026 Yamy Project. All rights reserved.',
    footer_credit: 'An app based on Mural',
    footer_credit_url: 'https://github.com/KelmerOC/Yamy#creditos',
    footer_repo: 'Repository',
    footer_issues: 'Report an issue',
    footer_aistudio: 'Google AI Studio'
  }
};
