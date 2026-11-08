import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApp } from '../src/app.js';
import { connectDatabase } from '../src/db.js';
import {
  getPublicAppCatalog,
  SUPPORTED_LANGUAGES,
  APP_SCREEN_SHOWCASE,
  SCENARIOS,
  TEST_MODALITIES,
  type PublicAppCatalog
} from '../src/public-catalog.js';

test('getPublicAppCatalog returns all invariants and complete datasets', () => {
  const catalog = getPublicAppCatalog();

  assert.equal(catalog.appName, 'Mural');
  assert.equal(catalog.liveModel, 'models/gemini-3.8-live');
  assert.equal(catalog.tutorModel, 'gemini-3.5-flash-lite');
  assert.equal(catalog.audioSampleRateHz, 16000);
  assert.equal(catalog.tickerIntervalMs, 5500);

  // 12 real languages
  assert.equal(catalog.languages.length, 12);
  const expectedLanguages = ['es', 'en', 'nb', 'fr', 'de', 'it', 'pt', 'zh', 'qu', 'ko', 'ja', 'hi'];
  assert.deepEqual(catalog.languages.map(l => l.id), expectedLanguages);

  for (const lang of catalog.languages) {
    assert.ok(lang.id && lang.name && lang.nativeName && lang.variety && lang.locale && lang.greeting && lang.assistantTagline);
  }

  // 5 real app screens
  assert.equal(catalog.appScreens.length, 5);
  const expectedScreens = ['talk', 'themes', 'tests', 'words', 'options'];
  assert.deepEqual(catalog.appScreens.map(s => s.id), expectedScreens);

  const expectedImages = [
    '/imagenes/Inicio.jpg',
    '/imagenes/Temas.jpg',
    '/imagenes/Test.jpg',
    '/imagenes/Palabras.jpg',
    '/imagenes/Opciones.jpg'
  ];
  assert.deepEqual(catalog.appScreens.map(s => s.imagePath), expectedImages);

  for (const screen of catalog.appScreens) {
    assert.equal(screen.aspectRatio, '9:16');
    for (const lang of ['es', 'en', 'qu', 'pt', 'fr'] as const) {
      assert.ok(screen.tabLabel[lang], `Missing tabLabel for ${lang} on ${screen.id}`);
      assert.ok(screen.title[lang], `Missing title for ${lang} on ${screen.id}`);
      assert.ok(screen.description[lang], `Missing description for ${lang} on ${screen.id}`);
      assert.ok(Array.isArray(screen.highlights[lang]) && screen.highlights[lang].length > 0, `Missing highlights for ${lang} on ${screen.id}`);
    }
  }

  // 24 real scenarios
  assert.equal(catalog.scenarios.length, 24);
  const expectedScenarios = [
    'coffee', 'weekend', 'walk', 'dinner', 'introductions', 'groceries',
    'travel', 'home', 'friends', 'work', 'weather', 'cabin',
    'music', 'film', 'books', 'design', 'technology', 'travelstories',
    'restaurant', 'neighbours', 'traditions', 'opinions', 'future', 'today'
  ];
  assert.deepEqual(catalog.scenarios.map(s => s.id), expectedScenarios);

  for (const scenario of catalog.scenarios) {
    assert.ok(['everyday', 'connection', 'local_life', 'interests'].includes(scenario.category));
    assert.ok(['peach', 'lilac', 'sage', 'butter'].includes(scenario.palette));
    assert.ok(['A1', 'A2', 'B1', 'B2', 'C1'].includes(scenario.mcer));
    assert.ok(scenario.title && scenario.subtitle && scenario.situation && scenario.sampleGreeting);
  }

  // 5 test modalities
  assert.equal(catalog.testModalities.length, 5);
  const expectedModalities = ['mcq', 'cloze', 'reading', 'clean_correction', 'speaking'];
  assert.deepEqual(catalog.testModalities.map(m => m.id), expectedModalities);

  for (const mod of catalog.testModalities) {
    assert.ok(mod.id && mod.name && mod.badge && mod.palette && mod.desc);
    assert.ok(['peach', 'lilac', 'sage', 'butter'].includes(mod.palette));
  }

  // Zero emojis rule
  const serialized = JSON.stringify(catalog);
  assert.equal(/[\uD800-\uDFFF\u2600-\u27BF]/.test(serialized), false, 'Catalog contains prohibited Unicode emojis');
});

test('GET /v1/public/catalog exposes catalog with CORS header and no auth required', async () => {
  const db = connectDatabase('postgresql://unused@127.0.0.1:1/unused');
  const app = createApp({ db, auth: {} });
  try {
    const response = await app.inject({ method: 'GET', url: '/v1/public/catalog' });
    assert.equal(response.statusCode, 200);
    assert.equal(response.headers['access-control-allow-origin'], '*');

    const body = response.json() as PublicAppCatalog;
    assert.equal(body.appName, 'Mural');
    assert.equal(body.languages.length, 12);
    assert.equal(body.appScreens.length, 5);
    assert.equal(body.scenarios.length, 24);
    assert.equal(body.testModalities.length, 5);

    // OPTIONS preflight
    const options = await app.inject({ method: 'OPTIONS', url: '/v1/public/catalog' });
    assert.equal(options.statusCode, 204);
    assert.equal(options.headers['access-control-allow-origin'], '*');
    assert.equal(options.headers['access-control-allow-methods'], 'GET, OPTIONS');
  } finally {
    await app.close();
    await db.end();
  }
});
