import React from 'react';
import { TEST_MODALITIES, RECALL_LEVELS } from '../data/repositoryData';
import { IconBook } from './icons/MuralSvgIcons';
import { UiTranslation } from '../data/uiTranslations';

interface AssessmentRecallSectionProps {
  t: UiTranslation;
}

export const AssessmentRecallSection: React.FC<AssessmentRecallSectionProps> = ({ t }) => {
  return (
    <section id="tests-evaluacion" className="section-wrapper-alt">
      <div className="container">
        {/* Cabecera */}
        <div className="section-header-block">
          <div className="section-badge-pill">
            <IconBook size={16} />
            <span>{t.tests_badge}</span>
          </div>
          <h2 className="section-title">{t.tests_title}</h2>
          <p className="section-lead">{t.tests_lead}</p>
        </div>

        {/* Banda Acuarela con las 5 Modalidades Reales de Tests */}
        <div className="watercolor-band">
          <h3 className="section-title">5 Modalidades de Examen en Tiempo Real</h3>
          <p className="section-lead">
            Generadas a partir del contexto oral de tus conversaciones con Gemini Flash Lite y retroalimentación pedagógica instantánea.
          </p>

          <div className="test-modalities-grid">
            {TEST_MODALITIES.map((modality) => {
              const paletteClass = `scenario-card-${modality.palette}`;
              return (
                <div key={modality.id} className={`test-modality-card ${paletteClass}`}>
                  <div>
                    <span className="test-modality-badge">{modality.badge}</span>
                    <h4 className="test-modality-name">{modality.name}</h4>
                  </div>
                  <p className="test-modality-desc">{modality.desc}</p>
                </div>
              );
            })}
          </div>
        </div>

        {/* Visualizador de las 3 Barras RecallBars */}
        <div className="recall-bars-container">
          <div className="section-header-block">
            <h3 className="section-title">{t.recall_title}</h3>
            <p className="section-lead">{t.recall_desc}</p>
          </div>

          <div className="recall-bars-grid">
            {RECALL_LEVELS.slice(0, 3).map((level) => (
              <div key={level.id} className="recall-level-card">
                <div className="recall-level-header">
                  <h4 className="recall-level-title">{level.title}</h4>
                  {/* Visualización de las 3 barras */}
                  <div className="bars-indicator-row">
                    <span
                      className={`bar-segment ${level.bars >= 1 ? 'filled' : ''}`}
                    />
                    <span
                      className={`bar-segment ${level.bars >= 2 ? 'filled' : ''}`}
                    />
                    <span
                      className={`bar-segment ${
                        level.bars >= 3 ? 'filled-strong' : ''
                      }`}
                    />
                  </div>
                </div>

                <span className="section-badge-pill">{level.badge}</span>
                <p className="recall-level-desc">{level.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};
