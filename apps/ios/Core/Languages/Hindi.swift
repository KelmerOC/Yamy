import Foundation

extension LanguageModule {
    public static let hindi = LanguageModule(
        id: "hi", name: "Hindi", nativeName: "हिन्दी", variety: "India", locale: "hi-IN",
        greeting: "नमस्ते!", greetingWord: "नमस्ते",
        speechGuidance: "Use clear, natural Modern Standard Hindi pronunciation. Use polite and respectful address (आप) as the friendly default, adjusting to तुम when the role or situation suggests familiarity. Model natural aspiration, retroflex consonants, and sentence intonation.",
        writingGuidance: "Use standard Devanagari script (देवनागरी) with appropriate matras and punctuation (।, ?, !). Use natural contemporary vocabulary while accepting common Hindustani loanwords.",
        lemmaGuidance: "Give nouns in their direct singular form with grammatical gender indicated, and verbs in the infinitive ending in -ना (e.g., जाना, खाना, बोलना). Explain postpositions (ने, को, से, में, पर) clearly.",
        teachingFocus: [
            "अभिवादन, परिचय और सरल रोज़मर्रा के वाक्य (नमस्ते, मेरा नाम..., मुझे चाहिए)।",
            "रोज़मर्रा के सवाल, लिंग-वचन सहमति, वर्तमान काल (होना, ता/ती/ते हैं) और बुनियादी कारक।",
            "भूतकाल (था/थी/थे, सामान्य भूतकाल), कहानियां, ने-नियम और जानी-पहचानी परिस्थितियां।",
            "कारण और राय (क्योंकि, मुझे लगता है कि), भविष्य काल (गा/गी/गे) और संयोजक।",
            "सभ्य/आदरणीय भाषा (आप vs तुम), मुहावरे, लोकोक्तियां और सांस्कृतिक संदर्भ।",
            "सहज, धाराप्रवाह और समृद्ध शब्दावली के साथ उन्नत हिंदी संवाद और चर्चा।"
        ],
        topicPlaceholder: "खाना, यात्रा, संगीत, भारत का जीवन…",
        lookupUnavailableReply: "मैं अभी इसकी पुष्टि नहीं कर पाया। यदि आप चाहें, तो हम इस विषय पर सामान्य रूप से बातचीत कर सकते हैं।",
        themeOverrides: [
            "coffee": .init("coffee", "एक चाय या कॉफ़ी?", "Something warm, please", "cup.and.saucer", "Everyday", "एक कैफे में मिलें। गर्म चाय या कॉफ़ी ऑर्डर करें और अपनी पसंद और दिनचर्या के बारे में बात करें।", 0),
            "groceries": .init("groceries", "सब्ज़ी मंडी में", "A little of everything", "basket", "Everyday", "स्थानीय बाज़ार में खरीदारी करें। मात्रा, भाव-तोल और विनम्र अनुरोध का अभ्यास करें।", 2),
            "travel": .init("travel", "अगला पड़ाव", "A ticket to somewhere", "tram", "Everyday", "यात्रा की योजना बनाएं। ट्रेन, बस, टिकट और रास्तों के बारे में हिंदी में बात करें।", 1),
            "cabin": .init("cabin", "सप्ताहांत की सैर", "A change of scene", "mountain.2", "Local life", "पहाड़ों या शांत जगह पर सप्ताहांत बिताने की योजना बनाएं। भोजन और विश्राम की चर्चा करें।", 2),
            "traditions": .init("traditions", "रीति-रिवाज़ और पर्व", "Small customs, big stories", "flag", "Local life", "त्योहारों, खान-पान और दैनिक रीति-रिवाज़ों के बारे में बात करें और अनुभव साझा करें।", 2)
        ]
    )
}
