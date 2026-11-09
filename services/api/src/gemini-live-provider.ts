import { randomUUID } from 'node:crypto';
import WebSocket from 'ws';
import { Diagnostics } from './diagnostics.js';
import { ServiceError } from './errors.js';
import {
  supportsLanguage,
  parseLiveContext,
  type LiveProvider,
  type LiveContext,
  type Sideband,
  type VoiceUsage
} from './live-provider.js';

interface ActiveSession {
  sessionID: string;
  language: string;
  context: LiveContext;
  onUsage?: (event: VoiceUsage) => void;
  onLoss?: () => void;
  clientSocket?: WebSocket;
  upstreamSocket?: WebSocket;
  startedAt?: number;
  meterInterval?: NodeJS.Timeout;
  closed?: boolean;
}

const languages: Record<string, string> = {
  'nb-NO': 'Norwegian Bokmål with an Eastern Norwegian pronunciation',
  'es-ES': 'Spanish from Spain',
  'en': 'English',
  'en-US': 'English',
  'fr-FR': 'French from France',
  'de-DE': 'Standard German as spoken in Germany',
  'it-IT': 'Italian as spoken in Italy',
  'pt-BR': 'Brazilian Portuguese',
  'zh-CN': 'Standard Mandarin with Simplified Chinese writing',
  'qu-PE': 'Southern Quechua (Cusco-Collao)',
  'ko-KR': 'Korean as spoken in South Korea',
  'ja-JP': 'Japanese as spoken in Japan',
  'hi-IN': 'Standard Hindi as spoken in India'
};

const DUMMY_SDP =
  'v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=GeminiLiveBridge\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=setup:passive\r\n';

export class GeminiLiveProvider implements LiveProvider {
  private readonly sessions = new Map<string, ActiveSession>();
  private readonly diagnostics: Diagnostics;
  private readonly wsUrl: string;

  constructor(
    private readonly key: string,
    options: { testOrigin?: string; diagnostics?: Diagnostics } = {}
  ) {
    this.diagnostics = options.diagnostics ?? new Diagnostics();
    if (!key || key.length < 10) throw new ServiceError('live_not_configured', 503);
    this.wsUrl = options.testOrigin
      ? `${options.testOrigin}/ws`
      : 'wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent';
  }

  async create(sdp: string, language: string, input?: LiveContext): Promise<{ sessionID: string; sdp: string }> {
    if (!supportsLanguage(language)) throw new ServiceError('invalid_language');
    const context = parseLiveContext(input);
    const sessionID = `gemini_${randomUUID().replace(/-/g, '')}`;

    this.sessions.set(sessionID, {
      sessionID,
      language,
      context
    });

    this.diagnostics.record('provider_completed', {
      operation: 'voice.create',
      providerStatus: 200,
      durationMilliseconds: 1
    });

    return { sessionID, sdp: DUMMY_SDP };
  }

  async attach(sessionID: string, onUsage: (event: VoiceUsage) => void, onLoss: () => void): Promise<Sideband> {
    const session = this.sessions.get(sessionID);
    if (!session) throw new ServiceError('invalid_provider_session', 502);

    session.onUsage = onUsage;
    session.onLoss = onLoss;

    return {
      closeSession: () => {
        this.closeActiveSession(sessionID);
      },
      disconnect: () => {
        this.disconnectActiveSession(sessionID);
      }
    };
  }

  async hangup(sessionID: string): Promise<void> {
    this.closeActiveSession(sessionID);
  }

  handleClientStream(sessionID: string, clientSocket: WebSocket): void {
    const session = this.sessions.get(sessionID);
    if (!session || session.closed) {
      clientSocket.close(1008, 'Session not active');
      return;
    }

    session.clientSocket = clientSocket;
    const url = `${this.wsUrl}?key=${this.key}`;
    const upstream = new WebSocket(url);
    session.upstreamSocket = upstream;

    upstream.once('open', () => {
      session.startedAt = Date.now();

      // Send initial setup frame to Gemini
      const instructions = `${session.context.instructions ?? 'You are Yamy, a warm language conversation partner.'}\nSpeak only ${languages[session.language] ?? 'English'}.`;
      const setupMessage = {
        setup: {
          model: 'models/gemini-3.8-live',
          generationConfig: {
            responseModalities: ['AUDIO'],
            speechConfig: {
              voiceConfig: {
                prebuiltVoiceConfig: {
                  voiceName: (session.context as unknown as Record<string, unknown>)?.voiceName as string ?? 'Puck'
                }
              }
            },
            inputAudioTranscription: {},
            outputAudioTranscription: {}
          },
          systemInstruction: {
            parts: [{ text: instructions }]
          },
          tools: [
            {
              functionDeclarations: [
                {
                  name: 'delegate_helper',
                  description: 'Delegate a research or lookup query to the helper.',
                  parameters: {
                    type: 'OBJECT',
                    properties: {
                      query: { type: 'STRING', description: 'Query to look up' }
                    },
                    required: ['query']
                  }
                }
              ]
            }
          ]
        }
      };

      upstream.send(JSON.stringify(setupMessage));

      // Start periodic usage metering (1s ticks)
      session.meterInterval = setInterval(() => {
        if (!session.startedAt || session.closed) return;
        const elapsedSeconds = Math.max(0, (Date.now() - session.startedAt) / 1000);
        session.onUsage?.({
          type: 'session.usage.updated',
          usage: { seconds: elapsedSeconds }
        });
      }, 1000);
      session.meterInterval.unref();
    });

    upstream.on('message', (data, isBinary) => {
      if (clientSocket.readyState === WebSocket.OPEN) {
        clientSocket.send(data, { binary: isBinary });
      }
    });

    clientSocket.on('message', (data, isBinary) => {
      if (!isBinary) {
        try {
          const parsed = JSON.parse(data.toString());
          if (parsed && typeof parsed === 'object' && 'setup' in parsed) {
            // Client sent an initial setup frame; server already sent the authoritative setup
            // frame to upstream with validated language and instructions, so ignore duplicate.
            return;
          }
        } catch {
          // Not valid JSON, forward directly
        }
      }
      if (upstream.readyState === WebSocket.OPEN) {
        upstream.send(data, { binary: isBinary });
      }
    });

    const cleanup = () => {
      if (session.meterInterval) {
        clearInterval(session.meterInterval);
        session.meterInterval = undefined;
      }
      if (session.startedAt && !session.closed) {
        session.closed = true;
        const finalSeconds = Math.max(0, (Date.now() - session.startedAt) / 1000);
        session.onUsage?.({
          type: 'session.closed',
          usage: { seconds: finalSeconds }
        });
      }
      this.sessions.delete(sessionID);
    };

    upstream.on('error', () => {
      session.onLoss?.();
      cleanup();
    });

    upstream.on('close', () => {
      if (clientSocket.readyState === WebSocket.OPEN) clientSocket.close();
      cleanup();
    });

    clientSocket.on('close', () => {
      if (upstream.readyState === WebSocket.OPEN) upstream.close();
      cleanup();
    });

    clientSocket.on('error', () => {
      if (upstream.readyState === WebSocket.OPEN) upstream.close();
      cleanup();
    });
  }

  private closeActiveSession(sessionID: string): void {
    const session = this.sessions.get(sessionID);
    if (!session || session.closed) return;
    session.closed = true;

    if (session.meterInterval) {
      clearInterval(session.meterInterval);
      session.meterInterval = undefined;
    }

    const seconds = session.startedAt ? Math.max(0, (Date.now() - session.startedAt) / 1000) : 0;
    session.onUsage?.({
      type: 'session.closed',
      usage: { seconds }
    });

    if (session.clientSocket?.readyState === WebSocket.OPEN) {
      session.clientSocket.close(1000, 'Session closed');
    }
    if (session.upstreamSocket?.readyState === WebSocket.OPEN) {
      session.upstreamSocket.close(1000, 'Session closed');
    }
    this.sessions.delete(sessionID);
  }

  private disconnectActiveSession(sessionID: string): void {
    const session = this.sessions.get(sessionID);
    if (!session) return;
    if (session.meterInterval) clearInterval(session.meterInterval);
    session.clientSocket?.terminate();
    session.upstreamSocket?.terminate();
    this.sessions.delete(sessionID);
  }
}
