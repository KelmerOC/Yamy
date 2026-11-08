import {
  SupportedLanguage,
  AppScreenShowcaseItem,
  ScenarioItem,
  TestModalityItem,
  PublicAppCatalog,
  RecallLevel,
  Language,
  Scenario,
  TestModality,
  ShowcaseScreen
} from '../types/catalog';

export const SUPPORTED_LANGUAGES: SupportedLanguage[] = [
  {
    id: 'es',
    name: 'Spanish',
    nativeName: 'Español',
    variety: 'Spain',
    locale: 'es-ES',
    greeting: '¡Hola!',
    assistantTagline: 'Tu asistente de aprendizaje de idiomas'
  },
  {
    id: 'en',
    name: 'English',
    nativeName: 'English',
    variety: 'International',
    locale: 'en',
    greeting: 'Hi!',
    assistantTagline: 'Your language learning assistant'
  },
  {
    id: 'nb',
    name: 'Norwegian',
    nativeName: 'Norsk',
    variety: 'Bokmål',
    locale: 'nb-NO',
    greeting: 'Hei!',
    assistantTagline: 'Din språklæringsassistent'
  },
  {
    id: 'fr',
    name: 'French',
    nativeName: 'Français',
    variety: 'France',
    locale: 'fr-FR',
    greeting: 'Salut !',
    assistantTagline: "Votre assistant d'apprentissage des langues"
  },
  {
    id: 'de',
    name: 'German',
    nativeName: 'Deutsch',
    variety: 'Germany',
    locale: 'de-DE',
    greeting: 'Hallo!',
    assistantTagline: 'Dein Sprachlernassistent'
  },
  {
    id: 'it',
    name: 'Italian',
    nativeName: 'Italiano',
    variety: 'Italy',
    locale: 'it-IT',
    greeting: 'Ciao!',
    assistantTagline: "Il tuo assistente per l'apprendimento delle lingue"
  },
  {
    id: 'pt',
    name: 'Portuguese',
    nativeName: 'Português',
    variety: 'Brazil',
    locale: 'pt-BR',
    greeting: 'Olá!',
    assistantTagline: 'Seu assistente de aprendizado de idiomas'
  },
  {
    id: 'zh',
    name: 'Mandarin Chinese',
    nativeName: '普通话',
    variety: 'Mainland China',
    locale: 'zh-CN',
    greeting: '你好！',
    assistantTagline: '你的语言学习助手'
  },
  {
    id: 'qu',
    name: 'Quechua Colla',
    nativeName: 'Runa Simi',
    variety: 'Cusco-Collao',
    locale: 'qu-PE',
    greeting: '¡Allillanchu!',
    assistantTagline: 'Simikuna yachanaykipaq yanapaqniyki'
  },
  {
    id: 'ko',
    name: 'Korean',
    nativeName: '한국어',
    variety: 'South Korea',
    locale: 'ko-KR',
    greeting: '안녕하세요!',
    assistantTagline: '당신의 언어 학습 도우미'
  },
  {
    id: 'ja',
    name: 'Japanese',
    nativeName: '日本語',
    variety: 'Japan',
    locale: 'ja-JP',
    greeting: 'こんにちは！',
    assistantTagline: 'あなたの語学学習アシスタント'
  },
  {
    id: 'hi',
    name: 'Hindi',
    nativeName: 'हिन्दी',
    variety: 'India',
    locale: 'hi-IN',
    greeting: 'नमस्ते!',
    assistantTagline: 'आपका भाषा सीखने वाला सहायक'
  }
];

export const APP_SCREENS: AppScreenShowcaseItem[] = [
  {
    id: 'talk',
    imagePath: '/imagenes/Inicio.jpg',
    aspectRatio: '9:16',
    tabLabel: {
      es: 'Hablar',
      en: 'Talk',
      qu: 'Rimay',
      pt: 'Falar',
      fr: 'Parler'
    },
    title: {
      es: 'Conversación Fluida en Tiempo Real',
      en: 'Real-Time Fluent Conversation',
      qu: 'Kawsaypi Chiqap Rimay',
      pt: 'Conversação Fluida em Tempo Real',
      fr: 'Conversation Fluide en Temps Réel'
    },
    description: {
      es: 'Práctica oral interactiva con el orbe nebuloso activo, soporte de audio bidireccional continuo a 16 kHz y retroalimentación pedagógica instantánea.',
      en: 'Interactive spoken practice with the active nebula orb, continuous 16 kHz two-way streaming, and instant pedagogical feedback.',
      qu: 'Kikin simiwan rimanakuy orbe nisqawan, 16 kHz uyarina puririyninwan, yachachina yanapaywan ima.',
      pt: 'Prática oral interativa com o orbe nebuloso ativo, streaming contínuo a 16 kHz e feedback pedagógico instantâneo.',
      fr: 'Pratique orale interactive avec orbe nébuleux actif, streaming audio continu à 16 kHz et retours pédagogiques instantanés.'
    },
    highlights: {
      es: [
        'Orbe nebuloso reactivo a la voz',
        'Nivel MCER calibrado de A1 a C2',
        'Traducción y significado en un toque',
        'Modo Enfoque Vocal para máxima concentración'
      ],
      en: [
        'Voice-reactive nebula orb',
        'CEFR level calibrated from A1 to C2',
        'One-tap translation and meaning',
        'Clean Voice Mode for deep focus'
      ],
      qu: [
        'Kunkaykiman t ikrakuq orbe',
        'MCER yachay tupu A1-manta C2-kama',
        'Huk ñit iypi t ikray sut inchayninwan',
        'Ch uya rimay yanapakuq'
      ],
      pt: [
        'Orbe nebuloso reativo à voz',
        'Nível QECR calibrado de A1 a C2',
        'Tradução e significado com um toque',
        'Modo Foco Vocal para máxima imersão'
      ],
      fr: [
        'Orbe nébuleux réactif à la voix',
        'Niveau CECRL calibré de A1 à C2',
        'Traduction et sens en un geste',
        'Mode Focus Vocal pour une immersion totale'
      ]
    }
  },
  {
    id: 'themes',
    imagePath: '/imagenes/Temas.jpg',
    aspectRatio: '9:16',
    tabLabel: {
      es: 'Temas',
      en: 'Themes',
      qu: 'Yachaykuna',
      pt: 'Temas',
      fr: 'Thèmes'
    },
    title: {
      es: 'Escenarios Situacionales y Role-Playing',
      en: 'Situational Scenarios & Role-Playing',
      qu: 'Kawsay Chawpipi Yachapakuy',
      pt: 'Cenários Situacionais e Dramatização',
      fr: 'Mises en Situation & Jeux de Rôle'
    },
    description: {
      es: '24 escenarios cuidadosamente ambientados con tarjetas pasteles para desenvolverse en cafés, viajes, compras, trámites y vida social.',
      en: '24 handcrafted pastel-themed scenarios for coffee shops, travels, grocery markets, housing, and social connection.',
      qu: '24 kawsay yachaykuna k acha llimp ikunawan: café wasipi, puriykunapi, qhatupi, tiyay wasipi rimanakunapaq.',
      pt: '24 cenários cuidadosamente ambientados em tons pastel para cafeteria, viagens, feira, moradia e convívio.',
      fr: '24 scénarios soigneusement contextualisés aux teintes pastel: cafés, voyages, marchés, logement et vie locale.'
    },
    highlights: {
      es: [
        '24 situaciones del día a día',
        'Conversación libre o temática guiada',
        'Creador de temas personalizados con IA',
        'Paletas pastel visualmente relajantes'
      ],
      en: [
        '24 real-life everyday situations',
        'Free chat or guided thematic role-play',
        'Custom AI topic generator',
        'Calming pastel visual palettes'
      ],
      qu: [
        '24 sapa p punchaw kawsay yachaykuna',
        'Qasi rimay icha yachachisqa rimay',
        'Musuq yachay paqarichiq IA yanapakuqwan',
        'Thak llimp ikuna qhawarinapaq'
      ],
      pt: [
        '24 situações do cotidiano',
        'Conversa livre ou guiada por temas',
        'Criador de temas com IA',
        'Paletas pastel suaves e elegantes'
      ],
      fr: [
        '24 situations authentiques du quotidien',
        'Discussion libre ou guidée',
        'Générateur de thèmes personnalisé par IA',
        'Palettes pastel douces et reposantes'
      ]
    }
  },
  {
    id: 'tests',
    imagePath: '/imagenes/Test.jpg',
    aspectRatio: '9:16',
    tabLabel: {
      es: 'Tests',
      en: 'Tests',
      qu: 'Tupuykuna',
      pt: 'Testes',
      fr: 'Tests'
    },
    title: {
      es: 'Evaluaciones de Aprendizaje y Práctica',
      en: 'Learning Tests & Diagnostic Quizzes',
      qu: 'Yachay Taripaykuna',
      pt: 'Avaliações de Aprendizagem e Testes',
      fr: 'Évaluations et Tests Pédagogiques'
    },
    description: {
      es: 'Generador de pruebas formativas adaptativas con opción múltiple de 4 alternativas, banco de palabras interactivo, comprensión lectora y expresión oral.',
      en: 'Adaptive formative tests featuring 4-option multiple choice, interactive cloze bank, multi-speaker reading comprehension, and oral challenges.',
      qu: 'Yachay tupuna qallariy 4 akllana simiwan, qillqa simi hunt achiywan, ñawinchay hamut aywan, rimay tupuywan ima.',
      pt: 'Gerador de testes adaptativos com múltipla escolha de 4 opções, banco de palavras interativo, interpretação de texto e fala.',
      fr: 'Générateur d évaluations adaptatives avec QCM à 4 choix, banque de mots interactive, compréhension de lecture et pratique orale.'
    },
    highlights: {
      es: [
        'Opción múltiple con 4 alternativas',
        'Banco de palabras táctil',
        'Comprensión lectora con diálogos auténticos',
        'Expresión oral con evaluación inmediata'
      ],
      en: [
        '4-option multiple choice format',
        'Interactive word bank chips',
        'Authentic multi-speaker dialogues',
        'Immediate spoken pronunciation evaluation'
      ],
      qu: [
        '4 akllana simikuna',
        'Taktil simi huñu huntachina',
        'Chiqap runakunaq rimayninta ñawinchay',
        'Kikillanpi rimay allinchay'
      ],
      pt: [
        'Múltipla escolha com 4 opções',
        'Banco de palavras interativo',
        'Compreensão de leitura com diálogos reais',
        'Avaliação oral em tempo real'
      ],
      fr: [
        'QCM clair à 4 options',
        'Banque de mots interactive',
        'Compréhension de lecture dialoguée',
        'Évaluation orale en direct'
      ]
    }
  },
  {
    id: 'words',
    imagePath: '/imagenes/Palabras.jpg',
    aspectRatio: '9:16',
    tabLabel: {
      es: 'Palabras',
      en: 'Words',
      qu: 'Simikuna',
      pt: 'Palavras',
      fr: 'Mots'
    },
    title: {
      es: 'Memoria Léxica y Barras de Recuerdo',
      en: 'Lexical Memory & Recall Tracking',
      qu: 'Simi Yuyarina Tupuqkuna',
      pt: 'Memória Lexical e Barras de Retenção',
      fr: 'Mémoire Lexicale & Suivi de Mémorisation'
    },
    description: {
      es: 'Gestión viva del vocabulario descubierto durante las conversaciones, con lemas contextualizados y sistema de progresión de recuerdo.',
      en: 'Active vocabulary library gathered from conversations, featuring contextual lemmas and visual recall progress bars.',
      qu: 'Rimanakuymanta lloqsimuq musuq simikuna, allin yuyariypi puririnankupaq tupuna suykunawan.',
      pt: 'Vocabulário vivo extraído das conversas, com lemas contextualizados e barras visuais de retenção.',
      fr: 'Gestion dynamique du vocabulaire acquis lors des échanges, avec lemmes contextualisés et barres de progression.'
    },
    highlights: {
      es: [
        'Vocabulario automático desde el chat',
        'Barras visuales de recuerdo activo',
        'Pronunciación y ejemplos en contexto',
        'Fichas de repaso espaciado'
      ],
      en: [
        'Automated vocabulary capture from chat',
        'Visual active recall bars',
        'In-context examples and audio guidance',
        'Spaced repetition flashcards'
      ],
      qu: [
        'Rimanakuymanta kikin simi pallana',
        'Yuyay rikuchiq suykuna',
        'Chiqap kawsaypi simi k askachiy',
        'Yuyariy kutipakuna tarjetas'
      ],
      pt: [
        'Captura automática a partir das conversas',
        'Barras visuais de retenção',
        'Exemplos e pronúncia em contexto',
        'Flashcards com repetição espaçada'
      ],
      fr: [
        'Capture automatique depuis les conversations',
        'Barres visuelles de mémorisation active',
        'Prononciation et exemples contextualisés',
        'Flashcards de répétition espacée'
      ]
    }
  },
  {
    id: 'options',
    imagePath: '/imagenes/Opciones.jpg',
    aspectRatio: '9:16',
    tabLabel: {
      es: 'Ajustes',
      en: 'Settings',
      qu: 'Allichaykuna',
      pt: 'Ajustes',
      fr: 'Réglages'
    },
    title: {
      es: 'Personalización y Pedagogía a Medida',
      en: 'Personalized Pedagogical Preferences',
      qu: 'Kikin Yachay Allichay',
      pt: 'Preferências e Pedagogia Personalizada',
      fr: 'Personnalisation & Pédagogie Adaptée'
    },
    description: {
      es: 'Configura tu idioma de aprendizaje entre los 12 disponibles, voz de síntesis (Puck), subtítulos de ayuda, Modo Voz Limpia y bifurcación entre Educación y Conversación.',
      en: 'Configure your target language among 12 options, voice timbre (Puck), subtitles, Clean Voice Mode, and switch between Education and Conversation modes.',
      qu: '12 simikunamanta akllay, Puck kunka, qillqa yanapakuq, Ch uya Rimay allichay, yachachina icha rimanallapaq akllay.',
      pt: 'Configure seu idioma entre os 12 disponíveis, timbre vocal (Puck), legendas, Modo Voz Limpa e alternância entre Educação e Conversação.',
      fr: 'Choisissez parmi les 12 langues disponibles, la voix Puck, les sous-titres, le Mode Focus Vocal et l alternance Éducation ou Conversation.'
    },
    highlights: {
      es: [
        '12 idiomas con dialectos auténticos',
        'Modo Educación vs Modo Conversación',
        'Voz natural Puck y velocidad regulable',
        'Modo Voz Limpia sin distracciones'
      ],
      en: [
        '12 supported languages with regional nuances',
        'Education Mode vs Conversation Mode',
        'Natural Puck voice with cadence control',
        'Distraction-free Clean Voice Mode'
      ],
      qu: [
        '12 simikuna kikin llaqtakunapaq',
        'Yachachina simi icha qasi rimay',
        'Puck allin kunka utqay tupunawan',
        'Ch uya Rimay qasi llamk anapaq'
      ],
      pt: [
        '12 idiomas com variedades autênticas',
        'Modo Educação vs Modo Conversação',
        'Voz natural Puck com controle de ritmo',
        'Modo Voz Limpa sem distrações'
      ],
      fr: [
        '12 langues avec variétés authentiques',
        'Mode Éducation vs Mode Conversation',
        'Voix naturelle Puck et cadence ajustable',
        'Mode Focus Vocal épuré et sans distraction'
      ]
    }
  }
];

export const REAL_SCENARIOS: ScenarioItem[] = [
  {
    id: 'coffee',
    category: 'everyday',
    palette: 'peach',
    mcer: 'A1',
    title: 'Un café',
    subtitle: 'Algo caliente, por favor',
    situation: 'Trabajas en una cafetería acogedora. Ayuda a pedir y conversa de forma natural.',
    sampleGreeting: '¡Hola! Bienvenido a la cafetería. ¿Qué te gustaría tomar hoy?'
  },
  {
    id: 'weekend',
    category: 'connection',
    palette: 'lilac',
    mcer: 'B1',
    title: 'El fin de semana',
    subtitle: 'Cuéntame del tuyo',
    situation: 'Pregunta sobre el fin de semana. Practica eventos pasados y sigue sus intereses.',
    sampleGreeting: '¡Qué alegría verte! ¿Qué tal estuvo tu fin de semana? ¿Hiciste algo especial?'
  },
  {
    id: 'walk',
    category: 'local_life',
    palette: 'sage',
    mcer: 'A2',
    title: 'Un paseo',
    subtitle: 'Al aire fresco',
    situation: 'Da un paseo imaginario por el barrio o un parque local. Habla de la naturaleza y la vida diaria.',
    sampleGreeting: 'Hace un día estupendo para caminar. ¿Te gusta más pasear por el parque o por el bosque?'
  },
  {
    id: 'dinner',
    category: 'everyday',
    palette: 'butter',
    mcer: 'A2',
    title: 'Planes de cena',
    subtitle: 'Cocinemos algo',
    situation: 'Planead la cena juntos. Pregunta por ingredientes, recetas sencillas y qué hay en la cocina.',
    sampleGreeting: 'Se está haciendo tarde y tengo un poco de hambre. ¿Qué te apetece preparar para cenar?'
  },
  {
    id: 'introductions',
    category: 'connection',
    palette: 'peach',
    mcer: 'A1',
    title: 'Mucho gusto',
    subtitle: 'Empecemos por conocernos',
    situation: 'Conoce a alguien nuevo en una reunión o clase. Comparte de dónde eres y qué haces.',
    sampleGreeting: '¡Mucho gusto! Es mi primera vez aquí. ¿Cómo te llamas y de dónde eres?'
  },
  {
    id: 'groceries',
    category: 'everyday',
    palette: 'sage',
    mcer: 'A1',
    title: 'En el mercado',
    subtitle: 'Buscando lo mejor',
    situation: 'Ayuda a comprar en un mercado local. Practica cantidades, precios y preguntas de compra.',
    sampleGreeting: '¡Buenos días! Hoy los tomates y las verduras están muy frescos. ¿Cuánto necesitas llevar?'
  },
  {
    id: 'travel',
    category: 'everyday',
    palette: 'lilac',
    mcer: 'A2',
    title: 'Próxima parada',
    subtitle: 'Un billete a algún lugar',
    situation: 'Planea un viaje en tren. Habla de rutas y billetes sin inventar horarios reales.',
    sampleGreeting: 'Taquilla de la estación, dígame. ¿A qué ciudad viajas y qué billete prefieres?'
  },
  {
    id: 'home',
    category: 'everyday',
    palette: 'butter',
    mcer: 'A2',
    title: 'Tu propio rincón',
    subtitle: 'Siéntete como en casa',
    situation: 'Habla sobre el hogar, habitaciones, mudanzas y qué hace que un lugar sea cómodo.',
    sampleGreeting: 'Pasa, pasa, bienvenido a mi casa. ¿Qué es lo que más te gusta de tu propio hogar?'
  },
  {
    id: 'friends',
    category: 'connection',
    palette: 'peach',
    mcer: 'B1',
    title: 'Nuevas amistades',
    subtitle: 'Una invitación',
    situation: 'Eres una amistad nueva y simpática. Organizad un plan para hacer juntos.',
    sampleGreeting: '¡Hola! Nos conocimos el otro día. Estaba pensando en ir al cine el sábado, ¿te gustaría venir?'
  },
  {
    id: 'work',
    category: 'everyday',
    palette: 'lilac',
    mcer: 'B1',
    title: 'Lunes por la mañana',
    subtitle: 'En la oficina',
    situation: 'Habla como colegas. Comenta el trabajo, reuniones y un pequeño reto por resolver.',
    sampleGreeting: 'Buen inicio de semana. Tenemos reunión a las diez, ¿tuviste tiempo de revisar el informe?'
  },
  {
    id: 'weather',
    category: 'local_life',
    palette: 'lilac',
    mcer: 'A1',
    title: '¿Lluvia otra vez?',
    subtitle: 'Haga el tiempo que haga',
    situation: 'Habla del clima, ropa adecuada y planes al aire libre.',
    sampleGreeting: 'Parece que el cielo está muy nublado hoy. ¿Crees que vaya a llover más tarde?'
  },
  {
    id: 'cabin',
    category: 'local_life',
    palette: 'sage',
    mcer: 'A2',
    title: 'Una escapada',
    subtitle: 'Días tranquilos',
    situation: 'Planea un fin de semana lejos: viajes, comida, paseos y descanso juntos.',
    sampleGreeting: 'Tengo muchas ganas de desconectar en la cabaña este fin de semana. ¿Qué planes tienes tú?'
  },
  {
    id: 'music',
    category: 'interests',
    palette: 'peach',
    mcer: 'B1',
    title: 'En bucle',
    subtitle: '¿Qué estás escuchando?',
    situation: 'Pregunta por la música que le gusta al usuario. Habla de sentimientos y conciertos.',
    sampleGreeting: 'Llevo todo el día con una canción en la cabeza. ¿Qué tipo de música sueles escuchar cuando trabajas?'
  },
  {
    id: 'film',
    category: 'interests',
    palette: 'lilac',
    mcer: 'B1',
    title: 'Un episodio más',
    subtitle: 'Algo que merezca la pena',
    situation: 'Habla de películas y series. Pide opiniones evitando destripes indeseados.',
    sampleGreeting: 'Ayer terminé una serie buenísima. ¿Has visto alguna película o serie interesante últimamente?'
  },
  {
    id: 'books',
    category: 'interests',
    palette: 'butter',
    mcer: 'B2',
    title: 'Entre páginas',
    subtitle: 'Una historia memorable',
    situation: 'Habla sobre libros, personajes, historias y por qué importan al lector.',
    sampleGreeting: 'Acabo de empezar una novela fascinante. ¿Prefieres las historias de ficción o las biografías?'
  },
  {
    id: 'design',
    category: 'interests',
    palette: 'peach',
    mcer: 'B2',
    title: 'Buenas cosas',
    subtitle: 'Hechas con esmero',
    situation: 'Explora el diseño, la arquitectura y los objetos que admiras. Pide opiniones concretas.',
    sampleGreeting: 'Me apasionan los objetos diseñados con sencillez y funcionalidad. ¿Qué diseño admiras tú?'
  },
  {
    id: 'technology',
    category: 'interests',
    palette: 'lilac',
    mcer: 'B2',
    title: 'Lo que viene',
    subtitle: 'Ideas, herramientas y el mañana',
    situation: 'Habla sobre tecnología y cómo transforma el día a día.',
    sampleGreeting: 'Las herramientas de inteligencia artificial están avanzando rapidísimo. ¿Cómo utilizas tú la tecnología?'
  },
  {
    id: 'travelstories',
    category: 'interests',
    palette: 'sage',
    mcer: 'B2',
    title: 'En otro lugar',
    subtitle: 'Un lugar que recuerdas',
    situation: 'Comparte historias de viajes y destinos soñados. Invita a comparar experiencias.',
    sampleGreeting: 'Siempre hay un lugar que se queda grabado en la memoria. ¿Cuál ha sido tu viaje más inolvidable?'
  },
  {
    id: 'restaurant',
    category: 'everyday',
    palette: 'peach',
    mcer: 'A2',
    title: 'Una mesa para dos',
    subtitle: 'Pide el postre',
    situation: 'Simula una comida en un restaurante. Practica pedidos, preferencias y resolver dudas con amabilidad.',
    sampleGreeting: 'Buenas noches. Bienvenidos a nuestro restaurante. ¿Tienen reserva o prefieren mesa en terraza?'
  },
  {
    id: 'neighbours',
    category: 'connection',
    palette: 'butter',
    mcer: 'A2',
    title: 'Puerta con puerta',
    subtitle: 'Un rostro familiar',
    situation: 'Habla como vecinos del barrio. Comenta el entorno y pide pequeños favores cotidianos.',
    sampleGreeting: '¡Hola, vecino! Qué coincidencia encontrarte aquí en la entrada. ¿Cómo va todo por el edificio?'
  },
  {
    id: 'traditions',
    category: 'local_life',
    palette: 'sage',
    mcer: 'B1',
    title: 'Costumbres del día a día',
    subtitle: 'Pequeños gestos, grandes historias',
    situation: 'Explora costumbres cotidianas y tradiciones con matices.',
    sampleGreeting: 'Cada cultura tiene rituales maravillosos para empezar el día. ¿Cuál es una costumbre típica de tu país?'
  },
  {
    id: 'opinions',
    category: 'connection',
    palette: 'lilac',
    mcer: 'B2',
    title: '¿Qué opinas?',
    subtitle: 'Espacio para otro punto de vista',
    situation: 'Elige un dilema cotidiano e invita a dar razones explorando otra perspectiva.',
    sampleGreeting: 'A veces es enriquecedor ver las cosas desde un ángulo diferente. ¿Qué opinas sobre el trabajo remoto frente al presencial?'
  },
  {
    id: 'future',
    category: 'connection',
    palette: 'butter',
    mcer: 'B2',
    title: 'De aquí a un año',
    subtitle: 'Planes que ilusionan',
    situation: 'Habla sobre sueños y planes futuros. Explora posibilidades y pasos prácticos.',
    sampleGreeting: 'Pensar en el futuro nos da energía. ¿Qué proyecto personal te gustaría haber completado dentro de un año?'
  },
  {
    id: 'today',
    category: 'interests',
    palette: 'peach',
    mcer: 'C1',
    title: 'El mundo hoy',
    subtitle: 'Algo de qué hablar',
    situation: 'Pregunta qué tema de actualidad le interesa al estudiante para explorar hechos recientes.',
    sampleGreeting: 'El mundo cambia cada día. ¿Hay alguna noticia o tendencia global que haya llamado tu atención esta semana?'
  }
];

export const TEST_MODALITIES: TestModalityItem[] = [
  {
    id: 'mcq',
    name: 'Opción Múltiple',
    badge: '4 Opciones',
    palette: 'peach',
    desc: '4 opciones con 1 única respuesta correcta y retroalimentación inmediata sobre la regla gramatical.'
  },
  {
    id: 'cloze',
    name: 'Banco de Palabras',
    badge: 'Cloze Bank',
    palette: 'lilac',
    desc: 'Fichas táctiles interactivas para completar espacios que se pueden reubicar con un solo toque.'
  },
  {
    id: 'reading',
    name: 'Comprensión Lectora',
    badge: 'Diálogo Real',
    palette: 'sage',
    desc: 'Conversaciones auténticas entre 2 y 3 interlocutores seguidas de preguntas de deducción contextual.'
  },
  {
    id: 'clean_correction',
    name: 'Corrección sin Asteriscos',
    badge: 'Pedagógico',
    palette: 'butter',
    desc: 'Frases completas corregidas limpiamente sin símbolos agresivos, manteniendo la cadencia natural de habla.'
  },
  {
    id: 'speaking',
    name: 'Expresión Oral con Micrófono',
    badge: 'Audio en Vivo',
    palette: 'peach',
    desc: 'Pronuncia en voz alta. Gemini 3.8 Live calibra tu entonación, ritmo y naturalidad al instante.'
  }
];

export const RECALL_LEVELS: RecallLevel[] = [
  {
    id: 'new',
    title: 'Palabra Recién Aprendida',
    bars: 1,
    badge: 'Nivel 1',
    desc: 'Descubierta en la última conversación con el tutor Gemini.'
  },
  {
    id: 'familiar',
    title: 'En Práctica',
    bars: 2,
    badge: 'Nivel 2',
    desc: 'Reconocida en lecturas o respuestas breves.'
  },
  {
    id: 'recalled',
    title: 'Recuerdo Espaciado',
    bars: 3,
    badge: 'Nivel 3',
    desc: 'Utilizada con éxito en pruebas de aprendizaje o respuestas orales.'
  },
  {
    id: 'mastered',
    title: 'Dominio Activo',
    bars: 4,
    badge: 'Nivel 4',
    desc: 'Incorporada con soltura y precisión en conversaciones espontáneas.'
  }
];

export const SCENARIO_CATEGORIES = [
  { id: 'all', labelKey: 'cat_all', labelDefault: 'Todos los escenarios' },
  { id: 'everyday', labelKey: 'cat_everyday', labelDefault: 'Día a día' },
  { id: 'connection', labelKey: 'cat_connection', labelDefault: 'Conexión' },
  { id: 'local_life', labelKey: 'cat_local', labelDefault: 'Vida local' },
  { id: 'interests', labelKey: 'cat_interests', labelDefault: 'Intereses' }
];

export const DEFAULT_APP_CATALOG: PublicAppCatalog = {
  appName: 'Yamy',
  liveModel: 'models/gemini-3.8-live',
  tutorModel: 'gemini-3.5-flash-lite',
  audioSampleRateHz: 16000,
  tickerIntervalMs: 5500,
  languages: SUPPORTED_LANGUAGES,
  appScreens: APP_SCREENS,
  scenarios: REAL_SCENARIOS,
  testModalities: TEST_MODALITIES
};

export const REAL_LANGUAGES: Language[] = SUPPORTED_LANGUAGES;
export const SHOWCASE_SCREENS: ShowcaseScreen[] = APP_SCREENS;
export const SCENARIOS_LIST: Scenario[] = REAL_SCENARIOS;
export const MODALITIES_LIST: TestModality[] = TEST_MODALITIES;
