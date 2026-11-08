import React from 'react';
import { IconMic, IconBrain, IconSparkles, IconLayers } from './icons/MuralSvgIcons';
import { UiTranslation } from '../data/uiTranslations';

interface InfrastructureFlowProps {
  t: UiTranslation;
}

export const InfrastructureFlow: React.FC<InfrastructureFlowProps> = ({ t }) => {
  return (
    <section id="infraestructura" className="section-wrapper">
      <div className="container">
        {/* Cabecera */}
        <div className="section-header-block">
          <div className="section-badge-pill">
            <IconLayers size={16} />
            <span>{t.infra_badge}</span>
          </div>
          <h2 className="section-title">{t.infra_title}</h2>
          <p className="section-lead">{t.infra_lead}</p>
        </div>

        {/* 3 Tarjetas Conectadas */}
        <div className="infra-connected-cards">
          <div className="infra-card">
            <div className="infra-icon-circle">
              <IconMic size={24} />
            </div>
            <h3 className="infra-card-title">{t.card1_title}</h3>
            <p className="infra-card-desc">{t.card1_desc}</p>
          </div>

          <div className="infra-card">
            <div className="infra-icon-circle">
              <IconBrain size={24} />
            </div>
            <h3 className="infra-card-title">{t.card2_title}</h3>
            <p className="infra-card-desc">{t.card2_desc}</p>
          </div>

          <div className="infra-card">
            <div className="infra-icon-circle">
              <IconSparkles size={24} />
            </div>
            <h3 className="infra-card-title">{t.card3_title}</h3>
            <p className="infra-card-desc">{t.card3_desc}</p>
          </div>
        </div>

        {/* Fila de 4 celdas numeradas [1] [2] [3] [4] (sin elemento estilo árbol) */}
        <div className="steps-row-grid">
          <div className="step-cell">
            <div className="step-number-pill">1</div>
            <h4 className="step-cell-title">{t.step1_title}</h4>
            <p className="step-cell-desc">{t.step1_desc}</p>
          </div>

          <div className="step-cell">
            <div className="step-number-pill">2</div>
            <h4 className="step-cell-title">{t.step2_title}</h4>
            <p className="step-cell-desc">{t.step2_desc}</p>
          </div>

          <div className="step-cell">
            <div className="step-number-pill">3</div>
            <h4 className="step-cell-title">{t.step3_title}</h4>
            <p className="step-cell-desc">{t.step3_desc}</p>
          </div>

          <div className="step-cell">
            <div className="step-number-pill">4</div>
            <h4 className="step-cell-title">{t.step4_title}</h4>
            <p className="step-cell-desc">{t.step4_desc}</p>
          </div>
        </div>
      </div>
    </section>
  );
};
