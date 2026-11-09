import Foundation

/// A target language's content and teaching policy. IDs are stable storage keys.
public struct LanguageModule: Identifiable, Sendable {
    public let id: String
    public let name: String
    public let nativeName: String
    public let variety: String
    public let locale: String
    public let greeting: String
    public let greetingWord: String
    public let speechGuidance: String
    public let writingGuidance: String
    public let lemmaGuidance: String
    public let teachingFocus: [String]
    public let topicPlaceholder: String
    public let lookupUnavailableReply: String
    public let themeOverrides: [String: ConversationTheme]

    public var themes: [ConversationTheme] {
        ConversationTheme.shared.map { themeOverrides[$0.id] ?? $0 }
    }
    public var displayName: String { id == "qu" ? "Quechua Colla" : name }
    public var defaultTitle: String { "A little \(displayName)" }
    public var talkTitle: String { "A little everyday \(displayName)" }
    public var settingsTitle: String { "\(displayName) · \(variety)" }
}

public enum LanguageRegistry {
    public static let defaultID = "nb"
    public static let all: [LanguageModule] = [.norwegian, .spanish, .english, .french, .german, .italian, .portuguese, .mandarin]
    public static let originarias: [LanguageModule] = [.quechua]
    public static let additionalLanguages: [LanguageModule] = [.korean, .japanese, .hindi]
    public static let supported: [LanguageModule] = all + originarias + additionalLanguages
    public static func module(for id: String) -> LanguageModule? { supported.first { $0.id == id } }
}

public enum MeaningLanguages {
    public static let all = ["English", "French", "German", "Spanish", "Norwegian", "Portuguese", "Italian", "Chinese (Simplified)", "Polish", "Arabic", "Ukrainian", "Korean", "Japanese", "Hindi", "Quechua Colla"]
    public static func greeting(in language: String) -> String {
        ["English": "Hi!", "French": "Salut !", "German": "Hallo!", "Spanish": "¡Hola!", "Norwegian": "Hei!", "Portuguese": "Olá!", "Italian": "Ciao!", "Chinese (Simplified)": "你好！", "Chinese": "你好！", "Polish": "Cześć!", "Arabic": "مرحبًا!", "Ukrainian": "Привіт!", "Korean": "안녕하세요!", "Japanese": "こんにちは！", "Hindi": "नमस्ते!", "Quechua Colla": "¡Allillanchu!", "Quechua": "¡Allillanchu!"][language] ?? "Hi!"
    }
}

