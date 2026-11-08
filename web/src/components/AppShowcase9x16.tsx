import React, { useState } from 'react';
import { SHOWCASE_SCREENS } from '../data/repositoryData';
import { IconCheck, IconPhone } from './icons/MuralSvgIcons';
import { UiTranslation } from '../data/uiTranslations';

interface AppShowcase9x16Props {
  t: UiTranslation;
  currentLang?: 'es' | 'en' | 'qu' | 'pt' | 'fr';
}

export const AppShowcase9x16: React.FC<AppShowcase9x16Props> = ({ t, currentLang = 'es' }) => {
  const [selectedScreenId, setSelectedScreenId] = useState<string>('talk');

  const activeIndex = SHOWCASE_SCREENS.findIndex((s) => s.id === selectedScreenId);
  const activeScreen = SHOWCASE_SCREENS[activeIndex >= 0 ? activeIndex : 0];

  const langKey = (currentLang in activeScreen.title ? currentLang : 'es') as
    | 'es'
    | 'en'
    | 'qu'
    | 'pt'
    | 'fr';

  const screenTitle = activeScreen.title[langKey] || activeScreen.title.es;
  const screenDesc = activeScreen.description[langKey] || activeScreen.description.es;
  const screenHighlights = activeScreen.highlights[langKey] || activeScreen.highlights.es;
  const badgeLabel = `Pantalla ${(activeIndex >= 0 ? activeIndex : 0) + 1} / 5`;

  return (
    <section id="showcase-9x16" className="section-wrapper">
      <div className="container">
        {/* Cabecera de la sección */}
        <div className="section-header-block">
          <div className="section-badge-pill">
            <IconPhone size={16} />
            <span>{t.showcase_badge}</span>
          </div>
          <h2 className="section-title">{t.showcase_title}</h2>
          <p className="section-lead">{t.showcase_lead}</p>
        </div>

        {/* 1. Escenario Interactivo Principal */}
        <div className="showcase-stage-layout">
          {/* Mockup Smartphone vertical */}
          <div className="phone-mockup-wrapper">
            <div className="phone-mockup-bezel">
              {/* Pantalla interna con la imagen real */}
              <div className="phone-screen-inner">
                <img
                  src={activeScreen.imagePath}
                  alt={screenTitle}
                  className="phone-screen-img"
                  loading="lazy"
                />
              </div>
            </div>
          </div>

          {/* Selector interactivo de pantallas en píldoras y detalles */}
          <div className="showcase-controls-col">
            <div className="showcase-tabs-pill-row">
              {SHOWCASE_SCREENS.map((screen) => {
                const label = screen.tabLabel[langKey] || screen.tabLabel.es;
                return (
                  <button
                    key={screen.id}
                    type="button"
                    className={`showcase-pill-btn ${
                      screen.id === activeScreen.id ? 'active' : ''
                    }`}
                    onClick={() => setSelectedScreenId(screen.id)}
                  >
                    {label}
                  </button>
                );
              })}
            </div>

            <div>
              <span className="section-badge-pill">{badgeLabel}</span>
              <h3 className="showcase-info-title">{screenTitle}</h3>
            </div>

            <p className="showcase-info-desc">{screenDesc}</p>

            {/* Puntos clave de la pantalla */}
            <div className="showcase-highlights-list">
              {screenHighlights.map((highlight, idx) => (
                <div key={idx} className="showcase-highlight-item">
                  <div className="showcase-check-icon-circle">
                    <IconCheck size={14} />
                  </div>
                  <span>{highlight}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 2. Galería Completa de las 5 Pantallas Reales */}
        <div className="section-header-block">
          <span className="section-badge-pill">{t.showcase_gallery_label}</span>
        </div>

        <div className="gallery-9x16-grid">
          {SHOWCASE_SCREENS.map((screen, idx) => {
            const cardTitle = screen.tabLabel[langKey] || screen.tabLabel.es;
            const cardDesc = screen.description[langKey] || screen.description.es;
            const cardBadge = `Pantalla ${idx + 1} / 5`;
            return (
              <div
                key={screen.id}
                className="gallery-9x16-card"
                onClick={() => setSelectedScreenId(screen.id)}
              >
                {/* Contenedor estricto de pantalla */}
                <div className="gallery-frame-9x16">
                  <img
                    src={screen.imagePath}
                    alt={cardTitle}
                    className="gallery-img-9x16"
                    loading="lazy"
                  />
                </div>
                <div className="gallery-card-body">
                  <span className="gallery-card-badge">{cardBadge}</span>
                  <h4 className="gallery-card-title">{cardTitle}</h4>
                  <p className="gallery-card-desc">{cardDesc}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
