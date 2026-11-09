import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GeminiHostedResponses } from '../src/gemini-hosted-responses.js';
import { GeminiLiveProvider } from '../src/gemini-live-provider.js';
import { hostedHelperBody, parseHostedHelperInput, HOSTED_HELPER_MODEL } from '../src/hosted-helpers.js';
import { geminiCost, RATE_VERSION_GEMINI } from '../src/pricing.js';
import { randomUUID } from 'node:crypto';

const key = 'AIzaSy-fake-test-key';
const body = () =>
  hostedHelperBody(
    parseHostedHelperInput({
      requestID: randomUUID(),
      purpose: 'meaning',
      instructions: 'Explain the meaning clearly.',
      input: 'Buenos días.'
    })
  );

test('GeminiHostedResponses formats request, sends to Gemini REST endpoint, and parses candidates', async () => {
  let attempts = 0;
  let capturedUrl = '';
  let capturedBody: any = null;

  const fakeResponse = {
    candidates: [
      {
        content: {
          parts: [{ text: 'Good morning in Spanish.' }],
          role: 'model'
        },
        finishReason: 'STOP'
      }
    ],
    usageMetadata: {
      promptTokenCount: 15,
      candidatesTokenCount: 8,
      cachedContentTokenCount: 3
    }
  };

  const transport = new GeminiHostedResponses(
    key,
    (async (url, init) => {
      attempts++;
      capturedUrl = String(url);
      capturedBody = JSON.parse(init?.body as string);
      assert.equal(init?.method, 'POST');
      assert.equal(init?.redirect, 'error');
      return Response.json(fakeResponse);
    }) as typeof fetch,
    undefined,
    'https://fake.gemini.endpoint'
  );

  const result = (await transport.send(body(), new AbortController().signal)) as any;
  assert.ok(capturedUrl.includes('/v1beta/models/gemini-3.5-flash:generateContent'));
  assert.ok(capturedUrl.includes(`key=${key}`));
  assert.equal(capturedBody.generationConfig?.thinkingConfig?.thinkingLevel, 'low');
  assert.equal(capturedBody.contents[0].parts[0].text, 'Buenos días.');
  assert.equal(capturedBody.systemInstruction.parts[0].text, 'Explain the meaning clearly.');

  // Validate format consumed by observedResponse
  assert.equal(result.model, HOSTED_HELPER_MODEL);
  assert.equal(result.status, 'completed');
  assert.equal(result.usage.input_tokens, 15);
  assert.equal(result.usage.output_tokens, 8);
  assert.equal(result.usage.input_tokens_details.cached_tokens, 3);
  assert.equal(result.output[0].type, 'message');
  assert.equal(result.output[0].content[0].text, 'Good morning in Spanish.');
});

test('GeminiHostedResponses handles Google Search Grounding and maps citations', async () => {
  const searchInput = parseHostedHelperInput({
    requestID: randomUUID(),
    purpose: 'delegation',
    instructions: 'Look up facts about Madrid.',
    input: 'Tell me about the Prado Museum.',
    search: true
  });
  const searchBody = hostedHelperBody(searchInput);

  const fakeSearchResponse = {
    candidates: [
      {
        content: {
          parts: [{ text: 'The Prado Museum is in Madrid, Spain.' }],
          role: 'model'
        },
        groundingMetadata: {
          groundingChunks: [
            {
              web: {
                uri: 'https://en.wikipedia.org/wiki/Prado_Museum',
                title: 'Prado Museum - Wikipedia'
              }
            }
          ]
        }
      }
    ],
    usageMetadata: {
      promptTokenCount: 40,
      candidatesTokenCount: 20
    }
  };

  let capturedBody: any = null;
  const transport = new GeminiHostedResponses(
    key,
    (async (_url, init) => {
      capturedBody = JSON.parse(init?.body as string);
      return Response.json(fakeSearchResponse);
    }) as typeof fetch,
    undefined,
    'https://fake.gemini.endpoint'
  );

  const result = (await transport.send(searchBody, new AbortController().signal)) as any;
  assert.ok(capturedBody.tools?.some((t: any) => t.googleSearch));
  assert.equal(result.output[0].type, 'web_search_call');
  assert.equal(result.output[1].type, 'message');
  assert.equal(result.output[1].content[0].annotations[0].type, 'url_citation');
  assert.equal(result.output[1].content[0].annotations[0].url, 'https://en.wikipedia.org/wiki/Prado_Museum');
  assert.equal(result.output[1].content[0].annotations[0].title, 'Prado Museum - Wikipedia');
});

test('GeminiHostedResponses streaming emits deltas and computes terminal usage', async () => {
  const seenDeltas: string[] = [];
  const ssePayload =
    `data: ${JSON.stringify({ candidates: [{ content: { parts: [{ text: 'Hola ' }] } }] })}\n\n` +
    `data: ${JSON.stringify({ candidates: [{ content: { parts: [{ text: 'mundo!' }] } }], usageMetadata: { promptTokenCount: 5, candidatesTokenCount: 4 } })}\n\n` +
    `data: [DONE]\n\n`;

  const transport = new GeminiHostedResponses(
    key,
    (async () => {
      return new Response(ssePayload, { headers: { 'content-type': 'text/event-stream' } });
    }) as typeof fetch,
    undefined,
    'https://fake.gemini.endpoint'
  );

  const result = (await transport.send({ ...body(), stream: true }, new AbortController().signal, text =>
    seenDeltas.push(text)
  )) as any;

  assert.deepEqual(seenDeltas, ['Hola ', 'Hola mundo!']);
  assert.equal(result.usage.input_tokens, 5);
  assert.equal(result.usage.output_tokens, 4);
  assert.equal(result.output[0].content[0].text, 'Hola mundo!');
});

test('GeminiHostedResponses rejects failed responses without leaking API key', async () => {
  const transport = new GeminiHostedResponses(
    key,
    (async () => {
      return new Response('Sensitive error message with key ' + key, { status: 403 });
    }) as typeof fetch,
    undefined,
    'https://fake.gemini.endpoint'
  );

  await assert.rejects(transport.send(body(), new AbortController().signal), (error: any) => {
    assert.equal(error.code, 'hosted_helper_provider_unavailable');
    assert.equal(String(error).includes(key), false);
    return true;
  });
});

test('GeminiLiveProvider creates session with synthetic SDP and attaches sideband', async () => {
  const provider = new GeminiLiveProvider(key);
  const created = await provider.create('v=0\r\no=- ...', 'es-ES');

  assert.ok(created.sessionID.startsWith('gemini_'));
  assert.ok(created.sdp.startsWith('v=0'));

  let usageReceived: any = null;
  const sideband = await provider.attach(
    created.sessionID,
    event => {
      usageReceived = event;
    },
    () => {}
  );

  assert.ok(sideband);
  sideband.closeSession();
  assert.equal(usageReceived?.type, 'session.closed');
  assert.equal(typeof usageReceived?.usage?.seconds, 'number');
});

test('geminiCost calculates accurate nanoUSD pricing for Gemini 2.0 Flash', () => {
  const usage = {
    milliseconds: 60_000, // 1 minute
    inputTokens: 10_000,
    cachedInputTokens: 2_000,
    outputTokens: 2_000,
    searchCalls: 2
  };

  const calculated = geminiCost(usage);
  assert.equal(calculated.rateVersion, RATE_VERSION_GEMINI);

  // 1 minute of voice = 35_000_000 nanoUSD
  assert.equal(calculated.voice, 35_000_000n);

  // Text: (10000 - 2000) * 100/1000 = 800n
  // Cached: 2000 * 25/1000 = 50n
  // Output: 2000 * 400/1000 = 800n
  // Text total = 800 + 50 + 800 = 1650n
  assert.equal(calculated.text, 1650n);

  // Search: 2 * 35_000n = 70_000n
  assert.equal(calculated.search, 70_000n);

  assert.equal(calculated.total, 35_000_000n + 1650n + 70_000n);
});

test('GeminiLiveProvider handleClientStream bridges frames with upstream and tracks usage', async () => {
  const { createServer } = await import('node:http');
  const { WebSocketServer, WebSocket } = await import('ws');

  const httpServer = createServer();
  const upstreamWss = new WebSocketServer({ noServer: true });

  const upstreamReceived: string[] = [];
  let upstreamSocketRef: any = null;

  upstreamWss.on('connection', ws => {
    upstreamSocketRef = ws;
    ws.on('message', data => {
      upstreamReceived.push(data.toString());
    });
  });

  httpServer.on('upgrade', (req, socket, head) => {
    upstreamWss.handleUpgrade(req, socket, head, ws => {
      upstreamWss.emit('connection', ws, req);
    });
  });

  await new Promise<void>(resolve => httpServer.listen(0, '127.0.0.1', () => resolve()));
  const address = httpServer.address() as any;
  const testOrigin = `ws://127.0.0.1:${address.port}`;

  const provider = new GeminiLiveProvider(key, { testOrigin });
  const session = await provider.create('v=0\r\n...', 'es-ES');

  let usageEvent: any = null;
  await provider.attach(session.sessionID, e => { usageEvent = e; }, () => {});

  // Create a local client WebSocket pair
  const clientPairWss = new WebSocketServer({ noServer: true });
  let providerSideWs: any = null;
  const connectPromise = new Promise<void>(resolve => {
    clientPairWss.on('connection', ws => {
      providerSideWs = ws;
      provider.handleClientStream(session.sessionID, ws);
      resolve();
    });
  });

  const clientPairHttp = createServer();
  clientPairHttp.on('upgrade', (req, socket, head) => {
    clientPairWss.handleUpgrade(req, socket, head, ws => {
      clientPairWss.emit('connection', ws, req);
    });
  });
  await new Promise<void>(resolve => clientPairHttp.listen(0, '127.0.0.1', () => resolve()));
  const clientPairAddr = clientPairHttp.address() as any;

  const clientWs = new WebSocket(`ws://127.0.0.1:${clientPairAddr.port}`);
  const clientReceived: string[] = [];
  clientWs.on('message', data => { clientReceived.push(data.toString()); });

  await connectPromise;
  await new Promise(r => setTimeout(r, 50));

  // Upstream must have received setup frame from provider
  assert.ok(upstreamReceived.length >= 1);
  const setupParsed = JSON.parse(upstreamReceived[0]!);
  assert.ok(setupParsed.setup);
  assert.equal(setupParsed.setup.model, 'models/gemini-3.8-live');

  // Upstream sends setupComplete back
  upstreamSocketRef.send(JSON.stringify({ setupComplete: {} }));
  await new Promise(r => setTimeout(r, 50));

  // Client receives setupComplete
  assert.ok(clientReceived.some(msg => msg.includes('setupComplete')));

  // Client sends message to upstream
  clientWs.send(JSON.stringify({ clientContent: { turns: [{ role: 'user', parts: [{ text: 'Hola' }] }] } }));
  await new Promise(r => setTimeout(r, 50));
  assert.ok(upstreamReceived.some(msg => msg.includes('Hola')));

  // Upstream sends audio chunk to client
  upstreamSocketRef.send(JSON.stringify({ serverContent: { modelTurn: { parts: [{ text: 'Respuesta' }] } } }));
  await new Promise(r => setTimeout(r, 50));
  assert.ok(clientReceived.some(msg => msg.includes('Respuesta')));

  // Close client
  clientWs.close();
  await new Promise(r => setTimeout(r, 50));

  assert.equal(usageEvent?.type, 'session.closed');

  // Teardown test servers
  upstreamWss.close();
  clientPairWss.close();
  httpServer.close();
  clientPairHttp.close();
});

