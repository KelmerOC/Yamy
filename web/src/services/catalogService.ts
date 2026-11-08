import {
  PublicAppCatalog,
  SupportedLanguage,
  ScenarioItem,
  TestModalityItem,
  AppScreenShowcaseItem,
  Language,
  Scenario,
  TestModality,
  ShowcaseScreen
} from '../types/catalog';
import {
  DEFAULT_APP_CATALOG,
  SUPPORTED_LANGUAGES,
  REAL_SCENARIOS,
  TEST_MODALITIES,
  APP_SCREENS,
  RECALL_LEVELS
} from '../data/repositoryData';

export { DEFAULT_APP_CATALOG };

export interface CatalogData {
  languages: Language[];
  scenarios: Scenario[];
  testModalities: TestModality[];
  showcaseScreens: ShowcaseScreen[];
}

export async function fetchAppCatalog(endpointUrl = '/v1/public/catalog'): Promise<PublicAppCatalog> {
  try {
    const response = await fetch(endpointUrl, {
      method: 'GET',
      headers: {
        'Accept': 'application/json'
      }
    });

    if (!response.ok) {
      return DEFAULT_APP_CATALOG;
    }

    const data = await response.json();
    if (!data || typeof data !== 'object') {
      return DEFAULT_APP_CATALOG;
    }

    return {
      appName: data.appName || DEFAULT_APP_CATALOG.appName,
      liveModel: 'models/gemini-3.8-live',
      tutorModel: 'gemini-3.5-flash-lite',
      audioSampleRateHz: 16000,
      tickerIntervalMs: 5500,
      languages: Array.isArray(data.languages) && data.languages.length === 12
        ? data.languages
        : DEFAULT_APP_CATALOG.languages,
      appScreens: Array.isArray(data.appScreens) && data.appScreens.length === 5
        ? data.appScreens
        : DEFAULT_APP_CATALOG.appScreens,
      scenarios: Array.isArray(data.scenarios) && data.scenarios.length === 24
        ? data.scenarios
        : DEFAULT_APP_CATALOG.scenarios,
      testModalities: Array.isArray(data.testModalities) && data.testModalities.length === 5
        ? data.testModalities
        : DEFAULT_APP_CATALOG.testModalities
    };
  } catch {
    return DEFAULT_APP_CATALOG;
  }
}

class CatalogService {
  private catalog: PublicAppCatalog = DEFAULT_APP_CATALOG;

  public async fetchCatalog(url?: string): Promise<PublicAppCatalog> {
    this.catalog = await fetchAppCatalog(url);
    return this.catalog;
  }

  public getCatalog(): PublicAppCatalog {
    return this.catalog;
  }

  public getLanguages(): SupportedLanguage[] {
    return this.catalog.languages || SUPPORTED_LANGUAGES;
  }

  public getLanguageById(id: string): SupportedLanguage | undefined {
    return this.getLanguages().find((lang) => lang.id === id);
  }

  public getScenarios(category?: string): ScenarioItem[] {
    const list = this.catalog.scenarios || REAL_SCENARIOS;
    if (!category || category === 'all') {
      return list;
    }
    return list.filter((s) => s.category === category);
  }

  public getShowcaseScreens(): AppScreenShowcaseItem[] {
    return this.catalog.appScreens || APP_SCREENS;
  }

  public getTestModalities(): TestModalityItem[] {
    return this.catalog.testModalities || TEST_MODALITIES;
  }

  public getRecallLevels() {
    return RECALL_LEVELS;
  }
}

export const catalogService = new CatalogService();
