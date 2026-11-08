import { randomUUID } from 'node:crypto';
import { Diagnostics } from './diagnostics.js';
import { boundedJSON } from './live-provider.js';
import { HOSTED_HELPER_MODEL, type HostedResponsesRequest, type HostedResponsesTransport } from './hosted-helpers.js';
import { ServiceError } from './errors.js';

export class GeminiHostedResponses implements HostedResponsesTransport {
  #key: string;
  readonly #diagnostics: Diagnostics;
  readonly #endpointBase: string;

  constructor(
    key: string,
    private readonly request: typeof fetch = fetch,
    diagnostics = new Diagnostics(),
    endpointBase = 'https://generativelanguage.googleapis.com'
  ) {
    if (!key || key.length < 10) throw new ServiceError('hosted_helpers_configuration_invalid', 503);
    this.#key = key;
    this.#diagnostics = diagnostics;
    this.#endpointBase = endpointBase;
  }

  async send(body: HostedResponsesRequest, signal: AbortSignal, onText?: (text: string) => void): Promise<unknown> {
    const started = performance.now();
    let status: number | undefined;
    let requestID: string | undefined;

    try {
      const geminiBody: Record<string, unknown> = {
        contents: [
          {
            role: 'user',
            parts: [{ text: body.input[0]?.content ?? '' }]
          }
        ],
        systemInstruction: {
          parts: [{ text: body.instructions }]
        }
      };

      const generationConfig: Record<string, unknown> = {
        maxOutputTokens: body.max_output_tokens,
        thinkingConfig: {
          thinkingLevel: 'low'
        }
      };

      if (body.text?.format?.type === 'json_schema' && body.text.format.schema) {
        generationConfig.responseMimeType = 'application/json';
        generationConfig.responseSchema = body.text.format.schema;
      }
      geminiBody.generationConfig = generationConfig;

      const hasSearch = body.tools?.some(t => t.type === 'web_search') ?? false;
      if (hasSearch) {
        geminiBody.tools = [{ googleSearch: {} }];
      }

      const isStream = Boolean(body.stream && onText);
      const url = isStream
        ? `${this.#endpointBase}/v1beta/models/gemini-3.5-flash:streamGenerateContent?alt=sse&key=${this.#key}`
        : `${this.#endpointBase}/v1beta/models/gemini-3.5-flash:generateContent?key=${this.#key}`;

      const response = await this.request(url, {
        method: 'POST',
        redirect: 'error',
        signal,
        headers: {
          'Content-Type': 'application/json',
          Accept: isStream ? 'text/event-stream' : 'application/json'
        },
        body: JSON.stringify(geminiBody)
      });

      status = response.status;
      requestID = response.headers.get('x-request-id') ?? undefined;

      if (!response.ok) {
        await response.body?.cancel().catch(() => {});
        throw new Error(`Gemini request failed with status ${response.status}`);
      }

      let fullText = '';
      let inputTokens = 0;
      let outputTokens = 0;
      let cachedTokens = 0;
      const citations: Array<{ url: string; title: string }> = [];
      let hadSearch = false;

      if (isStream) {
        const reader = response.body?.getReader();
        if (!reader) throw new Error('Empty response body');
        const decoder = new TextDecoder();
        let buffer = '';

        try {
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? '';

            for (const line of lines) {
              const trimmed = line.trim();
              if (!trimmed.startsWith('data:')) continue;
              const jsonStr = trimmed.slice(5).trim();
              if (!jsonStr || jsonStr === '[DONE]') continue;

              try {
                const chunk = JSON.parse(jsonStr);
                const candidates = chunk.candidates as Array<Record<string, any>> | undefined;
                if (candidates && candidates.length > 0) {
                  const candidate = candidates[0];
                  const parts = candidate?.content?.parts as Array<Record<string, any>> | undefined;
                  if (parts) {
                    for (const part of parts) {
                      if (typeof part.text === 'string' && part.text.length > 0) {
                        fullText += part.text;
                        onText?.(fullText);
                      }
                    }
                  }

                  const grounding = candidate?.groundingMetadata;
                  if (grounding?.groundingChunks) {
                    hadSearch = true;
                    for (const gChunk of grounding.groundingChunks) {
                      if (gChunk.web?.uri) {
                        citations.push({
                          url: gChunk.web.uri,
                          title: gChunk.web.title ?? 'Source'
                        });
                      }
                    }
                  }
                }

                if (chunk.usageMetadata) {
                  inputTokens = chunk.usageMetadata.promptTokenCount ?? inputTokens;
                  outputTokens = chunk.usageMetadata.candidatesTokenCount ?? outputTokens;
                  cachedTokens = chunk.usageMetadata.cachedContentTokenCount ?? cachedTokens;
                }
              } catch {
                // Ignore parse errors on individual SSE lines
              }
            }
          }
        } finally {
          await reader.cancel().catch(() => {});
        }
      } else {
        const rawJson = await boundedJSON(response, 1_048_576);
        const candidates = rawJson.candidates as Array<Record<string, any>> | undefined;
        if (candidates && candidates.length > 0) {
          const candidate = candidates[0];
          const parts = candidate?.content?.parts as Array<Record<string, any>> | undefined;
          if (parts) {
            for (const part of parts) {
              if (typeof part.text === 'string') {
                fullText += part.text;
              }
            }
          }

          const grounding = candidate?.groundingMetadata;
          if (grounding?.groundingChunks) {
            hadSearch = true;
            for (const gChunk of grounding.groundingChunks) {
              if (gChunk.web?.uri) {
                citations.push({
                  url: gChunk.web.uri,
                  title: gChunk.web.title ?? 'Source'
                });
              }
            }
          }
        }

        if (rawJson.usageMetadata) {
          inputTokens = rawJson.usageMetadata.promptTokenCount ?? 0;
          outputTokens = rawJson.usageMetadata.candidatesTokenCount ?? 0;
          cachedTokens = rawJson.usageMetadata.cachedContentTokenCount ?? 0;
        }
      }

      // Conservative estimate if token metadata missing
      if (inputTokens === 0) inputTokens = Math.ceil(Buffer.byteLength(body.input[0]?.content ?? '') / 4);
      if (outputTokens === 0) outputTokens = Math.max(1, Math.ceil(Buffer.byteLength(fullText) / 4));

      const outputItems: Array<Record<string, unknown>> = [];
      if (hadSearch || hasSearch) {
        outputItems.push({ type: 'web_search_call' });
      }

      outputItems.push({
        type: 'message',
        content: [
          {
            type: 'output_text',
            text: fullText,
            annotations: citations.map(c => ({
              type: 'url_citation',
              url: c.url,
              title: c.title
            }))
          }
        ]
      });

      const syntheticResponse = {
        id: `resp_${randomUUID().replace(/-/g, '')}`,
        model: HOSTED_HELPER_MODEL,
        status: 'completed',
        service_tier: 'default',
        usage: {
          input_tokens: inputTokens,
          input_tokens_details: {
            cached_tokens: cachedTokens,
            cache_write_tokens: 0
          },
          output_tokens: outputTokens
        },
        output: outputItems
      };

      this.#diagnostics.record('provider_completed', {
        operation: 'helper.respond',
        providerStatus: status,
        providerRequestID: requestID,
        durationMilliseconds: performance.now() - started
      });

      return syntheticResponse;
    } catch (error) {
      const serviceError = new ServiceError('hosted_helper_provider_unavailable', 502);
      this.#diagnostics.record(
        'provider_failed',
        {
          operation: 'helper.respond',
          providerStatus: status,
          providerRequestID: requestID,
          durationMilliseconds: performance.now() - started
        },
        serviceError
      );
      throw serviceError;
    }
  }
}
