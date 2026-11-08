import React from 'react';
import { NebulaOrbCanvas } from './NebulaOrbCanvas';
import { MultilingualTicker } from './MultilingualTicker';
import { IconPhone, IconTerminal, IconGithub } from './icons/MuralSvgIcons';
import { UiTranslation } from '../data/uiTranslations';

interface HeroFullBleedProps {
  t: UiTranslation;
}

export const HeroFullBleed: React.FC<HeroFullBleedProps> = ({ t }) => {
  return (
    <section id="inicio" className="hero-full-bleed">
      {/* Escenario central exacto: Orbe cósmico + Ticker multilingüe cada 5.5s */}
      <div className="hero-center-stage">
        {/* Esfera Nebulosa interactiva a 60 FPS con anillo Helix a -22° */}
        <NebulaOrbCanvas />

        {/* Carrusel multilingüe rotando cada 5.5 segundos */}
        <MultilingualTicker />

        {/* Descripción de la aplicación */}
        <p className="section-lead hero-desc-text">
          {t.hero_desc}
        </p>

        {/* Botones píldora de acción principal */}
        <div className="hero-actions-row">
          <a href="#showcase-9x16" className="btn-pill btn-pill-primary">
            <IconPhone size={20} />
            <span>{t.btn_showcase}</span>
          </a>

          <a href="#instalacion" className="btn-pill btn-pill-secondary">
            <IconTerminal size={18} />
            <span>{t.btn_install}</span>
          </a>

          <a
            href="https://github.com/KelmerOC/Yamy"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-pill btn-pill-github"
          >
            <IconGithub size={18} />
            <span>{t.btn_github}</span>
          </a>
        </div>
      </div>
    </section>
  );
};
