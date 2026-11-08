import Foundation
import MuralCore

final class NoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

struct APIUsage { var input = 0; var output = 0; var searches = 0 }
struct APIResult { var text: String; var sources: [SourceLink]; var usage: APIUsage }

@MainActor final class APIClient {
    private let session: URLSession
    init() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 45; config.timeoutIntervalForResource = 60
        config.httpCookieStorage = nil; config.urlCache = nil
        session = URLSession(configuration: config, delegate: NoRedirect(), delegateQueue: nil)
    }
    func post(_ path: String, body: [String: Any]) async throws -> [String: Any] {
        guard let key = CredentialStore.read() else { throw APIError.missingKey }
        var request = URLRequest(url: URL(string: "https://api.openai.com/v1/" + path)!)
        request.httpMethod = "POST"; request.setValue("Bearer " + key, forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else { throw ProviderFailure(status: http.statusCode, body: data, reference: http.value(forHTTPHeaderField: "x-request-id")) }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw APIError.invalidResponse }
        return json
    }
    func respond(instructions: String, input: String, schema: [String: Any]? = nil, search: Bool = false, onText: (@MainActor (String) -> Void)? = nil) async throws -> APIResult {
        guard let key = CredentialStore.read() else { throw APIError.missingKey }
        if key.hasPrefix("AIza") || key.hasPrefix("AQ.") {
            if let onText {
                return try await geminiStreamResponse(key: key, instructions: instructions, input: input, onText: onText)
            } else {
                return try await geminiRespond(key: key, instructions: instructions, input: input, schema: schema, search: search)
            }
        }
        var body: [String: Any] = ["model": "gpt-5.6-luna", "store": false, "instructions": instructions,
                                  "input": [["role": "user", "content": input]], "max_output_tokens": schema == nil ? 1400 : 2200,
                                  "reasoning": ["effort": "low"]]
        if let schema { body["text"] = ["format": ["type": "json_schema", "name": "mural_result", "strict": true, "schema": schema]] }
        if search { body["tools"] = [["type": "web_search"]]; body["tool_choice"] = "auto"; body["max_tool_calls"] = 1 }
        let json: [String: Any]
        if let onText {
            body["stream"] = true
            json = try await streamResponse(body: body, onText: onText)
        } else { json = try await post("responses", body: body) }
        guard json["status"] as? String == "completed" else { throw APIError.incomplete }
        var text = "", sources: [SourceLink] = [], usage = APIUsage()
        for item in json["output"] as? [[String: Any]] ?? [] {
            if item["type"] as? String == "web_search_call" { usage.searches += 1 }
            for content in item["content"] as? [[String: Any]] ?? [] {
                if content["type"] as? String == "refusal" { throw APIError.refused }
                if content["type"] as? String == "output_text" { text += content["text"] as? String ?? "" }
                for citation in content["annotations"] as? [[String: Any]] ?? [] {
                    guard citation["type"] as? String == "url_citation", let url = citation["url"] as? String else { continue }
                    let source = SourceLink(title: citation["title"] as? String ?? "Source", url: url)
                    if source.safeURL != nil && !sources.contains(where: { $0.url == url }) { sources.append(source) }
                }
            }
        }
        if let u = json["usage"] as? [String: Any] { usage.input = u["input_tokens"] as? Int ?? 0; usage.output = u["output_tokens"] as? Int ?? 0 }
        guard !text.isEmpty else { throw APIError.incomplete }
        return APIResult(text: text, sources: sources, usage: usage)
    }

    private func geminiRespond(key: String, instructions: String, input: String, schema: [String: Any]?, search: Bool) async throws -> APIResult {
        let body = geminiBody(instructions: instructions, input: input, schema: schema, search: search)
        let candidateModels = ["gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.8-flash"]
        var lastError: Error?

        for model in candidateModels {
            guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(model):generateContent?key=" + key) else { continue }
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)

            do {
                let (data, response) = try await session.data(for: request)
                try Task.checkCancellation()
                guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
                if http.statusCode == 404 || http.statusCode == 429 || http.statusCode == 503 {
                    lastError = ProviderFailure(status: http.statusCode, body: data, reference: http.value(forHTTPHeaderField: "x-request-id"))
                    try? await Task.sleep(nanoseconds: 350_000_000)
                    continue
                }
                guard (200..<300).contains(http.statusCode) else {
                    throw ProviderFailure(status: http.statusCode, body: data, reference: http.value(forHTTPHeaderField: "x-request-id"))
                }
                guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw APIError.invalidResponse }
                return try decodeGeminiResult(json)
            } catch let err as ProviderFailure where err.status == 404 || err.status == 429 || err.status == 503 {
                lastError = err
                try? await Task.sleep(nanoseconds: 350_000_000)
                continue
            }
        }
        throw lastError ?? APIError.invalidResponse
    }

    private func geminiBody(instructions: String, input: String, schema: [String: Any]? = nil, search: Bool = false) -> [String: Any] {
        var body: [String: Any] = [
            "systemInstruction": ["parts": [["text": instructions]]],
            "contents": [["role": "user", "parts": [["text": input]]]],
            "generationConfig": [
                "maxOutputTokens": schema == nil ? 1400 : 2200,
                "thinkingConfig": [
                    "thinkingLevel": "low"
                ]
            ]
        ]
        if let schema {
            var genConfig = body["generationConfig"] as? [String: Any] ?? [:]
            genConfig["responseMimeType"] = "application/json"
            genConfig["responseSchema"] = toGeminiSchema(schema)
            body["generationConfig"] = genConfig
        }
        if search {
            body["tools"] = [["googleSearch": [:]]]
        }
        return body
    }

    private func toGeminiSchema(_ schema: [String: Any]) -> [String: Any] {
        var result: [String: Any] = [:]
        for (k, v) in schema {
            switch k {
            case "type":
                if let typeStr = v as? String {
                    result["type"] = typeStr.uppercased()
                }
            case "properties":
                if let props = v as? [String: Any] {
                    var convertedProps: [String: Any] = [:]
                    for (propName, propVal) in props {
                        if let propSchema = propVal as? [String: Any] {
                            convertedProps[propName] = toGeminiSchema(propSchema)
                        } else {
                            convertedProps[propName] = propVal
                        }
                    }
                    result["properties"] = convertedProps
                }
            case "items":
                if let itemSchema = v as? [String: Any] {
                    result["items"] = toGeminiSchema(itemSchema)
                }
            case "additionalProperties":
                break
            default:
                result[k] = v
            }
        }
        return result
    }

    private func decodeGeminiResult(_ json: [String: Any]) throws -> APIResult {
        guard let candidates = json["candidates"] as? [[String: Any]], let first = candidates.first else {
            throw APIError.incomplete
        }
        if let finishReason = first["finishReason"] as? String,
           ["SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT"].contains(finishReason) {
            throw APIError.refused
        }
        guard let content = first["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]] else {
            throw APIError.incomplete
        }
        var text = ""
        for part in parts {
            text += part["text"] as? String ?? ""
        }
        guard !text.isEmpty else { throw APIError.incomplete }

        var sources: [SourceLink] = []
        var searches = 0
        if let groundingMetadata = first["groundingMetadata"] as? [String: Any] {
            if let queries = groundingMetadata["webSearchQueries"] as? [Any] {
                searches = queries.count
            }
            if let chunks = groundingMetadata["groundingChunks"] as? [[String: Any]] {
                for chunk in chunks {
                    guard let web = chunk["web"] as? [String: Any],
                          let uri = web["uri"] as? String,
                          let source = SourceLink(title: web["title"] as? String ?? "Source", url: uri).safeURL != nil ? SourceLink(title: web["title"] as? String ?? "Source", url: uri) : nil else { continue }
                    if !sources.contains(where: { $0.url == uri }) {
                        sources.append(source)
                    }
                }
            }
        }

        var usage = APIUsage()
        if let usageMetadata = json["usageMetadata"] as? [String: Any] {
            usage.input = usageMetadata["promptTokenCount"] as? Int ?? 0
            usage.output = usageMetadata["candidatesTokenCount"] as? Int ?? 0
            usage.searches = searches
        }
        return APIResult(text: text, sources: sources, usage: usage)
    }

    private func geminiStreamResponse(key: String, instructions: String, input: String, onText: @MainActor (String) -> Void) async throws -> APIResult {
        let body = geminiBody(instructions: instructions, input: input)
        let candidateModels = ["gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.8-flash"]
        var lastError: Error?

        for model in candidateModels {
            guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(model):streamGenerateContent?alt=sse&key=" + key) else { continue }
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)

            do {
                let (bytes, response) = try await session.bytes(for: request)
                guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
                if http.statusCode == 404 || http.statusCode == 429 || http.statusCode == 503 {
                    var body = Data()
                    for try await byte in bytes { body.append(byte); if body.count >= 16_384 { break } }
                    lastError = ProviderFailure(status: http.statusCode, body: body, reference: http.value(forHTTPHeaderField: "x-request-id"))
                    try? await Task.sleep(nanoseconds: 350_000_000)
                    continue
                }
                guard (200..<300).contains(http.statusCode) else {
                    var body = Data()
                    for try await byte in bytes { body.append(byte); if body.count >= 16_384 { break } }
                    throw ProviderFailure(status: http.statusCode, body: body, reference: http.value(forHTTPHeaderField: "x-request-id"))
                }

                var accumulatedText = ""
                var lastJson: [String: Any]?
                var lineBuffer = Data()

                for try await byte in bytes {
                    try Task.checkCancellation()
                    if byte == 10 {
                        if let line = String(data: lineBuffer, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
                           line.hasPrefix("data:") {
                            let jsonString = String(line.dropFirst(5)).trimmingCharacters(in: .whitespaces)
                            if let data = jsonString.data(using: .utf8),
                               let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                                lastJson = json
                                if let candidates = json["candidates"] as? [[String: Any]],
                                   let first = candidates.first {
                                    if let finishReason = first["finishReason"] as? String,
                                       ["SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT"].contains(finishReason) {
                                        throw APIError.refused
                                    }
                                    if let content = first["content"] as? [String: Any],
                                       let parts = content["parts"] as? [[String: Any]] {
                                        for part in parts {
                                            if let chunk = part["text"] as? String, !chunk.isEmpty {
                                                accumulatedText += chunk
                                                onText(accumulatedText)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        lineBuffer.removeAll(keepingCapacity: true)
                    } else if byte != 13 {
                        lineBuffer.append(byte)
                    }
                }

                guard !accumulatedText.isEmpty else { throw APIError.incomplete }
                var usage = APIUsage()
                if let lastJson, let usageMetadata = lastJson["usageMetadata"] as? [String: Any] {
                    usage.input = usageMetadata["promptTokenCount"] as? Int ?? 0
                    usage.output = usageMetadata["candidatesTokenCount"] as? Int ?? 0
                }
                return APIResult(text: accumulatedText, sources: [], usage: usage)
            } catch let err as ProviderFailure where err.status == 404 || err.status == 429 || err.status == 503 {
                lastError = err
                try? await Task.sleep(nanoseconds: 350_000_000)
                continue
            }
        }
        throw lastError ?? APIError.invalidResponse
    }
    private func streamResponse(body: [String: Any], onText: @MainActor (String) -> Void) async throws -> [String: Any] {
        guard let key = CredentialStore.read() else { throw APIError.missingKey }
        var request = URLRequest(url: URL(string: "https://api.openai.com/v1/responses")!)
        request.httpMethod = "POST"
        request.setValue("Bearer " + key, forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            var body = Data()
            for try await byte in bytes { body.append(byte); if body.count >= 16_384 { break } }
            throw ProviderFailure(status: http.statusCode, body: body, reference: http.value(forHTTPHeaderField: "x-request-id"))
        }
        guard http.value(forHTTPHeaderField: "Content-Type")?.lowercased().hasPrefix("text/event-stream") == true else { throw APIError.invalidResponse }
        var decoder = ResponseTextStream()
        do {
            for try await byte in bytes {
                try Task.checkCancellation()
                guard let update = try decoder.consume(byte: byte) else { continue }
                switch update {
                case .text(let value): onText(value)
                case .completed(let result): return result
                }
            }
        } catch ResponseTextStream.Failure.refused { throw APIError.refused }
        catch is ResponseTextStream.Failure { throw APIError.incomplete }
        throw APIError.incomplete
    }
    static func object(_ fields: [String: Any]) -> [String: Any] { ["type": "object", "properties": fields, "required": fields.keys.sorted(), "additionalProperties": false] }
    static let string: [String: Any] = ["type": "string"]
    static func assessmentSchema(language: LanguageModule) -> [String: Any] { object([
        "outcome": ["type": "string", "enum": ["success", "partial", "breakdown", "uncertain"]],
        "suggestedLevel": ["type": "integer", "minimum": 0, "maximum": 5], "nextGoal": string, "capability": string,
        "words": ["type": "array", "maxItems": 12, "items": object([
            "lemma": string, "meaning": string, "form": string, "quote": string, "language": ["type": "string", "enum": Array(Set([language.id, "en", "mixed", "uncertain"])).sorted()],
            "kind": ["type": "string", "enum": ["exposure", "understanding", "assisted", "independent", "lapse"]],
            "confidence": ["type": "number", "minimum": 0, "maximum": 1], "sourceIDs": ["type": "array", "items": string]
        ])]
    ]) }
    enum APIError: LocalizedError {
        case missingKey, invalidResponse, incomplete, refused, http(Int)
        var errorDescription: String? {
            switch self {
            case .missingKey: "Add your OpenAI or Google Gemini key in Settings to begin."
            case .invalidResponse, .incomplete: "The AI service returned an incomplete response. Please try again."
            case .refused: "Mural couldn’t complete that request. Try a different topic."
            case .http(401): "Your API key wasn’t accepted. Check it in Settings."
            case .http(403), .http(404): "This API key may not have access to the requested model. Check your project permissions."
            case .http(429): "The usage or rate limit was reached. Check your project’s billing and limits."
            case .http(let status): "The AI service couldn’t complete the request (HTTP \(status)). Please try again."
            }
        }
    }
}

