import React from 'react';
import { LogoYamy, IconGithub } from './icons/MuralSvgIcons';
import { UiTranslation } from '../data/uiTranslations';

interface MonumentalFooterProps {
  t: UiTranslation;
}

export const MonumentalFooter: React.FC<MonumentalFooterProps> = ({ t }) => {
  return (
    <footer className="monumental-footer">
      <div className="container">
        {/* Grilla superior de navegación y descripción */}
        <div className="footer-top-grid">
          <div className="footer-brand-col">
            <div className="footer-brand-title">
              <LogoYamy size={32} />
              <span>yamy</span>
            </div>
            <p className="footer-brand-desc">{t.footer_copy}</p>
          </div>

          <div className="footer-links-col">
            <h4 className="footer-col-title">Navegación</h4>
            <a href="#inicio" className="footer-link">
              {t.nav_home}
            </a>
            <a href="#showcase-9x16" className="footer-link">
              {t.nav_showcase}
            </a>
            <a href="#escenarios" className="footer-link">
              {t.nav_scenarios}
            </a>
            <a href="#infraestructura" className="footer-link">
              {t.nav_infra}
            </a>
            <a href="#tests-evaluacion" className="footer-link">
              {t.nav_tests}
            </a>
            <a href="#instalacion" className="footer-link">
              {t.nav_install}
            </a>
          </div>

          <div className="footer-links-col">
            <h4 className="footer-col-title">Comunidad & Código</h4>
            <a
              href="https://github.com/KelmerOC/Yamy"
              target="_blank"
              rel="noopener noreferrer"
              className="footer-link"
            >
              {t.footer_repo}
            </a>
            <a
              href="https://github.com/KelmerOC/Yamy/issues"
              target="_blank"
              rel="noopener noreferrer"
              className="footer-link"
            >
              {t.footer_issues}
            </a>
            <a
              href="https://aistudio.google.com"
              target="_blank"
              rel="noopener noreferrer"
              className="footer-link"
            >
              {t.footer_aistudio}
            </a>
          </div>
        </div>

        {/* Wordmark monumental 'yamy' en fondo cósmico */}
        <div className="monumental-wordmark" aria-hidden="true">
          yamy
        </div>

        {/* Fila inferior de derechos con mención discreta de crédito */}
        <div className="footer-bottom-row">
          <div className="footer-bottom-left">
            <span>{t.footer_rights}</span>
            <span className="footer-divider">·</span>
            <a
              href={t.footer_credit_url}
              target="_blank"
              rel="noopener noreferrer"
              className="footer-credit-link"
            >
              {t.footer_credit}
            </a>
          </div>

          <a
            href="https://github.com/KelmerOC/Yamy"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-pill btn-pill-github btn-pill-sm"
          >
            <IconGithub size={16} />
            <span>GitHub</span>
          </a>
        </div>
      </div>
    </footer>
  );
};
