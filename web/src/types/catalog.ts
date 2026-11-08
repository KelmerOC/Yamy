export interface SupportedLanguage {
  id: string;
  name: string;
  nativeName: string;
  variety: string;
  locale: string;
  greeting: string;
  assistantTagline: string;
}

export interface AppScreenShowcaseItem {
  id: 'talk' | 'themes' | 'tests' | 'words' | 'options';
  imagePath: string;
  aspectRatio: '9:16';
  tabLabel: Record<'es' | 'en' | 'qu' | 'pt' | 'fr', string>;
  title: Record<'es' | 'en' | 'qu' | 'pt' | 'fr', string>;
  description: Record<'es' | 'en' | 'qu' | 'pt' | 'fr', string>;
  highlights: Record<'es' | 'en' | 'qu' | 'pt' | 'fr', string[]>;
}

export interface ScenarioItem {
  id: string;
  category: 'everyday' | 'connection' | 'local_life' | 'interests';
  palette: 'peach' | 'lilac' | 'sage' | 'butter';
  mcer: 'A1' | 'A2' | 'B1' | 'B2' | 'C1';
  title: string;
  subtitle: string;
  situation: string;
  sampleGreeting: string;
}

export interface TestModalityItem {
  id: string;
  name: string;
  badge: string;
  palette: 'peach' | 'lilac' | 'sage' | 'butter';
  desc: string;
}

export interface PublicAppCatalog {
  appName: string;
  liveModel: 'models/gemini-3.8-live';
  tutorModel: 'gemini-3.5-flash-lite';
  audioSampleRateHz: 16000;
  tickerIntervalMs: 5500;
  languages: SupportedLanguage[];
  appScreens: AppScreenShowcaseItem[];
  scenarios: ScenarioItem[];
  testModalities: TestModalityItem[];
}

export type ScenarioCategory = 'all' | 'everyday' | 'connection' | 'local_life' | 'interests';
export type ScenarioPalette = 'peach' | 'lilac' | 'sage' | 'butter';
export type Language = SupportedLanguage;
export type Scenario = ScenarioItem;
export type TestModality = TestModalityItem;
export type ShowcaseScreen = AppScreenShowcaseItem;

export interface RecallLevel {
  id: string;
  title: string;
  bars: number;
  badge: string;
  desc: string;
}
