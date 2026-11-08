import Foundation

extension LanguageModule {
    public static let quechua = LanguageModule(
        id: "qu", name: "Quechua", nativeName: "Runa Simi", variety: "Cusco-Collao", locale: "qu-PE",
        greeting: "¡Allillanchu!", greetingWord: "allillanchu",
        speechGuidance: "Use natural Southern Quechua (Quechua Colla / Cusco-Collao) pronunciation. Accept valid regional variants (Ayacucho, Ancash) warmly. Speak with clarity, respect and conversational warmth.",
        writingGuidance: "Use standard Southern Quechua (Quechua Colla) orthography (trivocalic: a, i, u) with valid suffix agglutination. Keep meanings accessible.",
        lemmaGuidance: "Give nominal roots and verb infinitives ending in -y (e.g., rimay, mikuy, wasi). Explain suffixes clearly when the learner asks.",
        teachingFocus: [
            "Greetings and basic introductions (Allillanchu, imaynallam, sutiyqa).",
            "Everyday questions, basic noun suffixes (-mi, -qa, -kuna) and present tense.",
            "Connected past narratives (-rqa suffix), family, and everyday Andean life.",
            "Reasons and opinions (-rayku, -manta connectors) and collaborative activities (ayni).",
            "Cultural nuance, rich metaphorical expressions, and respectful register.",
            "Fluent, authentic dialogue in Runa Simi across traditional and modern themes."
        ],
        topicPlaceholder: "Ayllu, kawsay, chakra, puriy, mikhuy…",
        lookupUnavailableReply: "Manam kunanqa chayta qhawariyta atinichu. Munaspaykiqa, tukuy imamantam rimarisunman.",
        themeOverrides: [
            "coffee": .init("coffee", "Aqha / Café?", "Something warm, please", "cup.and.saucer", "Everyday", "Meet in an Andean café. Order a warm drink (mate, café) and converse in Quechua Colla.", 0),
            "groceries": .init("groceries", "Qhatupi", "Find something fresh", "basket", "Everyday", "Visit a traditional Andean market (qhatu). Ask for vegetables, fruits and quantities in Quechua Colla.", 2),
            "travel": .init("travel", "Puriy", "A ticket to somewhere", "tram", "Everyday", "Plan a journey through the Andes. Discuss transport, directions and stops in Quechua Colla.", 1),
            "cabin": .init("cabin", "Chiriy punchaw", "A quieter kind of day", "mountain.2", "Local life", "Plan an imagined stay in an Andean village. Talk about nature, food and weaving in Quechua Colla.", 2),
            "traditions": .init("traditions", "Raymikuna", "Small customs, big stories", "flag", "Local life", "Explore Andean customs, Pachamama, harvest festivals and community solidarity in Quechua Colla.", 2)
        ]
    )
}
