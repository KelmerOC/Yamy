import React, { useState } from 'react';
import { REAL_SCENARIOS } from '../data/repositoryData';
import { Scenario, ScenarioCategory } from '../types/catalog';
import { IconSparkles, IconClose } from './icons/MuralSvgIcons';
import { UiTranslation } from '../data/uiTranslations';

interface ScenariosGridProps {
  t: UiTranslation;
}

export const ScenariosGrid: React.FC<ScenariosGridProps> = ({ t }) => {
  const [activeCategory, setActiveCategory] = useState<ScenarioCategory>('all');
  const [selectedScenario, setSelectedScenario] = useState<Scenario | null>(null);
  const [isExpanded, setIsExpanded] = useState<boolean>(false);

  const categories: { id: ScenarioCategory; label: string }[] = [
    { id: 'all', label: t.cat_all },
    { id: 'everyday', label: t.cat_everyday },
    { id: 'connection', label: t.cat_connection },
    { id: 'local_life', label: t.cat_local },
    { id: 'interests', label: t.cat_interests }
  ];

  const handleCategoryChange = (category: ScenarioCategory) => {
    setActiveCategory(category);
    setIsExpanded(false);
  };

  const filteredScenarios =
    activeCategory === 'all'
      ? REAL_SCENARIOS
      : REAL_SCENARIOS.filter((s) => s.category === activeCategory);

  const displayedScenarios = isExpanded ? filteredScenarios : filteredScenarios.slice(0, 8);
  const remainingCount = filteredScenarios.length - 8;

  return (
    <section id="escenarios" className="section-wrapper-alt">
      <div className="container">
        {/* Cabecera */}
        <div className="section-header-block">
          <div className="section-badge-pill">
            <IconSparkles size={16} />
            <span>{t.scenarios_badge}</span>
          </div>
          <h2 className="section-title">{t.scenarios_title}</h2>
          <p className="section-lead">{t.scenarios_lead}</p>
        </div>

        {/* Filtros por Categoría */}
        <div className="scenarios-category-filter">
          {categories.map((cat) => (
            <button
              key={cat.id}
              type="button"
              className={`category-filter-btn ${activeCategory === cat.id ? 'active' : ''}`}
              onClick={() => handleCategoryChange(cat.id)}
            >
              {cat.label}
            </button>
          ))}
        </div>

        {/* Cuadrícula de escenarios */}
        <div id="scenarios-grid-list" className="scenarios-grid">
          {displayedScenarios.map((scenario) => {
            const paletteClass = `scenario-card-${scenario.palette}`;
            return (
              <div
                key={scenario.id}
                className={`scenario-card ${paletteClass}`}
                onClick={() => setSelectedScenario(scenario)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') setSelectedScenario(scenario);
                }}
              >
                <div>
                  <div className="scenario-header-row">
                    <span className="scenario-mcer-badge">MCER {scenario.mcer}</span>
                  </div>
                  <h3 className="scenario-title">{scenario.title}</h3>
                  <div className="scenario-subtitle">{scenario.subtitle}</div>
                  <p className="scenario-situation">{scenario.situation}</p>
                </div>

                <div className="scenario-greeting-preview">
                  "{scenario.sampleGreeting}"
                </div>
              </div>
            );
          })}
        </div>

        {/* Botón píldora Mostrar más / Mostrar menos */}
        {filteredScenarios.length > 8 && (
          <div className="scenarios-show-more-row">
            <button
              type="button"
              className="btn-pill btn-pill-secondary"
              onClick={() => setIsExpanded((prev) => !prev)}
              aria-expanded={isExpanded}
              aria-controls="scenarios-grid-list"
            >
              <span>
                {isExpanded
                  ? t.scenarios_show_less
                  : `${t.scenarios_show_more} (${remainingCount})`}
              </span>
            </button>
          </div>
        )}

        {/* Modal de Detalle de Escenario */}
        {selectedScenario && (
          <div
            className="scenario-modal-overlay"
            onClick={() => setSelectedScenario(null)}
          >
            <div
              className={`scenario-modal-box scenario-card-${selectedScenario.palette}`}
              onClick={(e) => e.stopPropagation()}
            >
              <button
                type="button"
                className="scenario-modal-close-btn"
                onClick={() => setSelectedScenario(null)}
                aria-label="Cerrar ventana modal"
              >
                <IconClose size={18} />
              </button>

              <div className="scenario-header-row">
                <span className="scenario-mcer-badge">Nivel MCER {selectedScenario.mcer}</span>
              </div>

              <div>
                <h3 className="scenario-title">{selectedScenario.title}</h3>
                <div className="scenario-subtitle">{selectedScenario.subtitle}</div>
              </div>

              <div>
                <strong>{t.scenario_situation}</strong>
                <p className="scenario-situation">{selectedScenario.situation}</p>
              </div>

              <div className="scenario-greeting-preview">
                <strong>{t.scenario_sample_greeting}</strong>
                <p>"{selectedScenario.sampleGreeting}"</p>
              </div>

              <button
                type="button"
                className="btn-pill btn-pill-primary"
                onClick={() => setSelectedScenario(null)}
              >
                Cerrar vista previa
              </button>
            </div>
          </div>
        )}
      </div>
    </section>
  );
};
