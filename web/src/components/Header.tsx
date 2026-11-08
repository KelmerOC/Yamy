import React, { useState, useEffect } from 'react';
import { LogoYamy, IconGithub, IconGlobe, IconMenu, IconClose } from './icons/MuralSvgIcons';
import { UiTranslation } from '../data/uiTranslations';

interface HeaderProps {
  t: UiTranslation;
  currentLang: string;
  onToggleLang: () => void;
}

export const Header: React.FC<HeaderProps> = ({ t, currentLang, onToggleLang }) => {
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  const openDrawer = () => setIsDrawerOpen(true);
  const closeDrawer = () => setIsDrawerOpen(false);

  // Bloqueo de scroll al abrir el drawer mediante clase CSS
  useEffect(() => {
    if (isDrawerOpen) {
      document.body.classList.add('nav-open');
    } else {
      document.body.classList.remove('nav-open');
    }
    return () => {
      document.body.classList.remove('nav-open');
    };
  }, [isDrawerOpen]);

  // Cierre con tecla Escape
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isDrawerOpen) {
        closeDrawer();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isDrawerOpen]);

  const navLinks = [
    { href: '#inicio', label: t.nav_home },
    { href: '#showcase-9x16', label: t.nav_showcase },
    { href: '#escenarios', label: t.nav_scenarios },
    { href: '#infraestructura', label: t.nav_infra },
    { href: '#tests-evaluacion', label: t.nav_tests },
    { href: '#instalacion', label: t.nav_install }
  ];

  return (
    <>
      <header className="site-header">
        <div className="container header-inner">
          <a href="#inicio" className="header-brand" aria-label="Yamy - Volver al inicio">
            <LogoYamy size={36} />
            <span className="brand-text">yamy</span>
          </a>

          {/* Navegación Desktop */}
          <nav className="header-nav" aria-label="Navegación principal">
            {navLinks.map((link) => (
              <a key={link.href} href={link.href} className="nav-link">
                {link.label}
              </a>
            ))}
          </nav>

          {/* Acciones Desktop */}
          <div className="header-actions header-actions-desktop">
            <button
              type="button"
              className="btn-pill btn-pill-secondary btn-pill-sm"
              onClick={onToggleLang}
              aria-label="Cambiar idioma de interfaz"
            >
              <IconGlobe size={16} />
              <span>{currentLang.toUpperCase()}</span>
            </button>

            <a
              href="https://github.com/KelmerOC/Yamy"
              target="_blank"
              rel="noopener noreferrer"
              className="btn-pill btn-pill-github btn-pill-sm"
              aria-label="Repositorio GitHub oficial"
            >
              <IconGithub size={16} />
              <span>{t.github_btn}</span>
            </a>
          </div>

          {/* Botón Hamburguesa Móvil */}
          <button
            type="button"
            className="mobile-menu-btn"
            onClick={openDrawer}
            aria-expanded={isDrawerOpen}
            aria-controls="mobile-nav-drawer"
            aria-label={t.nav_menu_open}
          >
            <IconMenu size={24} />
          </button>
        </div>
      </header>

      {/* Drawer Móvil Lateral */}
      <div
        className={`drawer-overlay ${isDrawerOpen ? 'drawer-open' : ''}`}
        onClick={closeDrawer}
        aria-hidden={!isDrawerOpen}
      />

      <aside
        id="mobile-nav-drawer"
        className={`drawer-panel ${isDrawerOpen ? 'drawer-open' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={t.nav_menu_open}
      >
        <div className="drawer-header">
          <div className="header-brand">
            <LogoYamy size={32} />
            <span className="brand-text">yamy</span>
          </div>
          <button
            type="button"
            className="drawer-close-btn"
            onClick={closeDrawer}
            aria-label={t.nav_menu_close}
          >
            <IconClose size={22} />
          </button>
        </div>

        <nav className="drawer-nav" aria-label="Navegación móvil">
          {navLinks.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="drawer-nav-link"
              onClick={closeDrawer}
            >
              {link.label}
            </a>
          ))}
        </nav>

        <div className="drawer-footer-actions">
          <button
            type="button"
            className="btn-pill btn-pill-secondary btn-pill-sm drawer-action-btn"
            onClick={onToggleLang}
            aria-label="Cambiar idioma de interfaz"
          >
            <IconGlobe size={16} />
            <span>{currentLang.toUpperCase()}</span>
          </button>

          <a
            href="https://github.com/KelmerOC/Yamy"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-pill btn-pill-github btn-pill-sm drawer-action-btn"
            aria-label="Repositorio GitHub oficial"
            onClick={closeDrawer}
          >
            <IconGithub size={16} />
            <span>{t.github_btn}</span>
          </a>
        </div>
      </aside>
    </>
  );
};
