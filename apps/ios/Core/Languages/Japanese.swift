import Foundation

extension LanguageModule {
    public static let japanese = LanguageModule(
        id: "ja", name: "Japanese", nativeName: "日本語", variety: "Japan", locale: "ja-JP",
        greeting: "こんにちは！", greetingWord: "こんにちは",
        speechGuidance: "Use clear, natural Standard Japanese (Tokyo pitch accent). Use polite style (丁寧語 / です・ます) as the standard friendly baseline, adapting to informal (タメ口) or honorific (敬語) when the role and situation call for it. Maintain natural conversational rhythm and particles.",
        writingGuidance: "Use natural Japanese orthography combining Kanji, Hiragana, and Katakana with standard punctuation (、。). Match the register to the situation.",
        lemmaGuidance: "Give nouns in their plain form without particles (助詞) and verbs/adjectives in their plain dictionary form (辞書形, e.g., 行く, 食べる, 美しい). Explain particles clearly when asked.",
        teachingFocus: [
            "挨拶、自己紹介、基本的な表現（はじめまして、～です、～をください）。",
            "日常的な質問、助詞（は、が、を、に、で）、丁寧語（です・ます）の現在形。",
            "過去形（～でした、～ました）、日常の出来事、接続表現（～て、～から）。",
            "理由や意見（～と思います、～ので）、希望（～たい）、許可や禁止。",
            "敬語（尊敬語・謙譲語）、ニュアンス、オノマトペ、慣用句、文化的文脈。",
            "自然で流暢な日本語による深い対話、ディスカッション、ニュアンスの使い分け。"
        ],
        topicPlaceholder: "アニメ、食べ物、旅行、日本の生活…",
        lookupUnavailableReply: "今は確認できませんでした。よろしければ、このテーマについて一般的なお話をしましょう。",
        themeOverrides: [
            "coffee": .init("coffee", "カフェで一杯？", "Something warm, please", "cup.and.saucer", "Everyday", "街の喫茶店で待ち合わせ。飲み物を注文し、店員さんへの丁寧な挨拶や日常の会話を楽しみます。", 0),
            "groceries": .init("groceries", "商店街でお買い物", "A little of everything", "basket", "Everyday", "活気ある商店街やスーパーで食材を買います。数量、値段の尋ね方、丁寧なお願いを練習します。", 2),
            "travel": .init("travel", "次の目的地へ", "A ticket to somewhere", "tram", "Everyday", "日本国内の電車の旅を計画。ルート、乗り換え、切符の買い方について会話します。", 1),
            "cabin": .init("cabin", "週末のおでかけ", "A change of scene", "mountain.2", "Local life", "温泉街や自然豊かな場所への週末旅行を計画し、のんびりと過ごす予定を話します。", 2),
            "traditions": .init("traditions", "暮らしの歳時記", "Small customs, big stories", "flag", "Local life", "日本の四季折々の習慣や日々の暮らしについて語り合い、文化の違いを楽しみます。", 2)
        ]
    )
}
