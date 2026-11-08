import React, { useState, useEffect } from 'react';
import { REAL_LANGUAGES } from '../data/repositoryData';

interface MultilingualTickerProps {
  onLanguageSelect?: (languageId: string) => void;
}

export const MultilingualTicker: React.FC<MultilingualTickerProps> = ({ onLanguageSelect }) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isTransitioning, setIsTransitioning] = useState(false);

  useEffect(() => {
    // Intervalo exacto de 5.5 segundos (5500 ms)
    const interval = setInterval(() => {
      setIsTransitioning(true);
      setTimeout(() => {
        setCurrentIndex((prev) => (prev + 1) % REAL_LANGUAGES.length);
        setIsTransitioning(false);
      }, 250);
    }, 5500);

    return () => clearInterval(interval);
  }, []);

  const currentLang = REAL_LANGUAGES[currentIndex];

  const handleSelectLanguage = (index: number) => {
    setIsTransitioning(true);
    setTimeout(() => {
      setCurrentIndex(index);
      setIsTransitioning(false);
      if (onLanguageSelect) {
        onLanguageSelect(REAL_LANGUAGES[index].id);
      }
    }, 150);
  };

  return (
    <div className="multilingual-ticker-container">
      {/* Píldora de saludo e idioma activo */}
      <div className="ticker-active-pill">
        <span className="ticker-lang-badge">{currentLang.id}</span>
        <span className="ticker-greeting-text">{currentLang.greeting}</span>
        <span className="ticker-lang-label">
          {currentLang.nativeName} ({currentLang.name})
        </span>
      </div>

      {/* Frase animada: "Tu asistente de aprendizaje de idiomas" traducida */}
      <h1
        className={`ticker-headline-display ${isTransitioning ? 'ticker-transitioning' : ''}`}
      >
        {currentLang.assistantTagline}
      </h1>

      {/* Indicadores interactivos para los 12 idiomas reales */}
      <div className="ticker-dots-row">
        {REAL_LANGUAGES.map((lang, idx) => (
          <button
            key={lang.id}
            type="button"
            className={`ticker-dot-chip ${idx === currentIndex ? 'active' : ''}`}
            onClick={() => handleSelectLanguage(idx)}
            aria-label={`Seleccionar idioma ${lang.name}`}
          >
            {lang.nativeName}
          </button>
        ))}
      </div>
    </div>
  );
};
