import React from 'react';
import { REAL_LANGUAGES } from '../data/repositoryData';
import { UiTranslation } from '../data/uiTranslations';

interface LanguagesStripProps {
  t: UiTranslation;
}

export const LanguagesStrip: React.FC<LanguagesStripProps> = ({ t }) => {
  return (
    <section className="languages-strip-wrapper" aria-label="Idiomas soportados">
      <div className="container">
        <h2 className="languages-strip-title">{t.strip_title}</h2>
        <div className="languages-strip-grid">
          {REAL_LANGUAGES.map((lang) => (
            <div key={lang.id} className="lang-chip">
              <span className="lang-chip-code">{lang.id}</span>
              <span className="lang-chip-name">{lang.nativeName}</span>
              <span className="lang-chip-greeting">· {lang.greeting}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};
