import Foundation

extension LanguageModule {
    public static let korean = LanguageModule(
        id: "ko", name: "Korean", nativeName: "한국어", variety: "South Korea", locale: "ko-KR",
        greeting: "안녕하세요!", greetingWord: "안녕하세요",
        speechGuidance: "Use clear, natural Standard Korean (Seoul) pronunciation. Use polite conversational style (해요체, e.g., -아요/-어요) for friendly interaction, and formal polite style (하십시오체, e.g., -ㅂ니다/-습니다) when appropriate for situations requiring higher formality. Model natural intonation and speech rhythm.",
        writingGuidance: "Use standard Korean Hangul orthography, spacing (띄어쓰기), and modern punctuation. Match politeness levels (존댓말) to the role and situation.",
        lemmaGuidance: "Give nouns in their dictionary form without particles (조사) and verbs/adjectives in their basic dictionary infinitive ending in -다 (e.g., 가다, 먹다, 예쁘다). Keep honorific forms distinct when meaningful.",
        teachingFocus: [
            "기본 인사, 자기소개 및 핵심 표현 (안녕하세요, 제 이름은..., ~하고 싶어요).",
            "일상 질문, 기본 조사(은/는, 이/가, 을/를) 및 해요체 현재 시제.",
            "과거 시제(~았/었어요), 일상 이야기, 연결 어미(~고, ~아서/어서) 및 익숙한 상황.",
            "이유와 의견 표현(~(으)니까, ~는 것 같아요), 미래 계획(~(으)ㄹ 거예요) 및 자연스러운 연결.",
            "간접화법, 존댓말의 뉘앙스, 관용 표현 및 상황별 격식.",
            "자연스럽고 풍부한 어휘를 바탕으로 한 고급 한국어 회화와 토론."
        ],
        topicPlaceholder: "음식, 여행, 드라마, 한국 생활…",
        lookupUnavailableReply: "지금은 확인할 수 없습니다. 원하시면 이 주제에 대해 편하게 대화해 볼 수 있습니다.",
        themeOverrides: [
            "coffee": .init("coffee", "커피 한잔?", "Something warm, please", "cup.and.saucer", "Everyday", "동네 카페에서 만납니다. 음료를 주문하고 한국어로 편안하게 하루 일과와 관심사에 대해 대화합니다.", 0),
            "groceries": .init("groceries", "전통시장에서", "A little of everything", "basket", "Everyday", "한국의 전통시장이나 마트에서 장을 봅니다. 수량, 가격, 정중한 요청 표현을 연습합니다.", 2),
            "travel": .init("travel", "다음 행선지", "A ticket to somewhere", "tram", "Everyday", "한국 여행을 계획합니다. 대중교통, 길 찾기, 예매에 대해 대화합니다.", 1),
            "cabin": .init("cabin", "주말 힐링 여행", "A change of scene", "mountain.2", "Local life", "주말 여행을 함께 계획하며 자연, 음식, 휴식에 대해 대화합니다.", 2),
            "traditions": .init("traditions", "일상과 풍습", "Small customs, big stories", "flag", "Local life", "한국의 사계절 풍습과 일상 문화를 이야기하며 경험을 나눕니다.", 2)
        ]
    )
}
