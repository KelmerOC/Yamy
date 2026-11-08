import React, { useState } from 'react';
import { IconTerminal, IconSparkles } from './icons/MuralSvgIcons';
import { UiTranslation } from '../data/uiTranslations';

interface InstallationGuideProps {
  t: UiTranslation;
}

export const InstallationGuide: React.FC<InstallationGuideProps> = ({ t }) => {
  const [activePlatform, setActivePlatform] = useState<'android' | 'ios'>('android');

  return (
    <section id="instalacion" className="section-wrapper">
      <div className="container">
        {/* Cabecera */}
        <div className="section-header-block">
          <div className="section-badge-pill">
            <IconTerminal size={16} />
            <span>{t.install_badge}</span>
          </div>
          <h2 className="section-title">{t.install_title}</h2>
          <p className="section-lead">{t.install_lead}</p>
        </div>

        {/* Conmutador en píldoras: Android vs iOS */}
        <div className="install-platform-toggle">
          <button
            type="button"
            className={`install-platform-btn ${activePlatform === 'android' ? 'active' : ''}`}
            onClick={() => setActivePlatform('android')}
          >
            {t.install_tab_android}
          </button>
          <button
            type="button"
            className={`install-platform-btn ${activePlatform === 'ios' ? 'active' : ''}`}
            onClick={() => setActivePlatform('ios')}
          >
            {t.install_tab_ios}
          </button>
        </div>

        {/* Bloque de Código de Compilación */}
        {activePlatform === 'android' ? (
          <div className="install-code-box">
            <h3 className="install-code-title">
              <IconTerminal size={20} />
              <span>Compilación Android (SDK 36 · Kotlin & Jetpack Compose)</span>
            </h3>
            <pre className="install-code-pre">
{`# 1. Clonar el repositorio
git clone https://github.com/KelmerOC/Yamy.git
cd Yamy/apps/android

# 2. Ejecutar suite de pruebas unitarias
./gradlew testDebugUnitTest

# 3. Compilar APK depuración
./gradlew assembleDebug

# El archivo se genera en:
# apps/android/app/build/outputs/apk/debug/app-debug.apk`}
            </pre>
          </div>
        ) : (
          <div className="install-code-box">
            <h3 className="install-code-title">
              <IconTerminal size={20} />
              <span>Compilación iOS (Xcode 26 · Swift & SwiftUI)</span>
            </h3>
            <pre className="install-code-pre">
{`# 1. Clonar el repositorio
git clone https://github.com/KelmerOC/Yamy.git
cd Yamy/apps/ios

# 2. Abrir proyecto nativo en Xcode
open Mural.xcodeproj

# 3. Seleccionar simulador o dispositivo físico (iOS 18+) y presionar Cmd + R`}
            </pre>
          </div>
        )}

        {/* Tarjeta de Configuración de Clave Google AI Studio */}
        <div className="install-key-card">
          <div className="install-key-icon">
            <IconSparkles size={24} />
          </div>
          <div>
            <h4 className="install-key-title">{t.install_key_title}</h4>
            <p className="install-key-text">{t.install_key_desc}</p>
          </div>
        </div>
      </div>
    </section>
  );
};
