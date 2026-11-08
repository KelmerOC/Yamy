import React, { useState } from 'react';
import './styles/tokens.css';
import './styles/layout.css';
import './styles/components.css';
import './styles/animations.css';

import { UI_TRANSLATIONS } from './data/uiTranslations';
import { Header } from './components/Header';
import { HeroFullBleed } from './components/HeroFullBleed';
import { LanguagesStrip } from './components/LanguagesStrip';
import { AppShowcase9x16 } from './components/AppShowcase9x16';
import { ScenariosGrid } from './components/ScenariosGrid';
import { InfrastructureFlow } from './components/InfrastructureFlow';
import { AssessmentRecallSection } from './components/AssessmentRecallSection';
import { InstallationGuide } from './components/InstallationGuide';
import { MonumentalFooter } from './components/MonumentalFooter';

export const App: React.FC = () => {
  const [currentLang, setCurrentLang] = useState<'es' | 'en'>('es');

  const toggleLanguage = () => {
    setCurrentLang((prev) => (prev === 'es' ? 'en' : 'es'));
  };

  const t = UI_TRANSLATIONS[currentLang] || UI_TRANSLATIONS.es;

  return (
    <div className="app-wrapper">
      <Header t={t} currentLang={currentLang} onToggleLang={toggleLanguage} />
      <main className="main-content">
        <HeroFullBleed t={t} />
        <LanguagesStrip t={t} />
        <AppShowcase9x16 t={t} currentLang={currentLang} />
        <ScenariosGrid t={t} />
        <InfrastructureFlow t={t} />
        <AssessmentRecallSection t={t} />
        <InstallationGuide t={t} />
      </main>
      <MonumentalFooter t={t} />
    </div>
  );
};

export default App;
