import React, { useEffect, useRef } from 'react';

export const NebulaOrbCanvas: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    let phase = 0;
    let energy = 0;
    let targetEnergy = 0;
    let isRunning = false;
    let width = 800;
    let height = 380;
    const states = ['idle', 'listening', 'speaking'] as const;
    let stateIndex = 0;

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const resize = () => {
      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.getBoundingClientRect();
      const w = rect.width || 800;
      const h = rect.height || 400;

      canvas.width = Math.floor(w * dpr);
      canvas.height = Math.floor(h * dpr);
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.scale(dpr, dpr);
      width = w;
      height = h;
    };

    resize();
    window.addEventListener('resize', resize);

    const handleClick = () => {
      stateIndex = (stateIndex + 1) % states.length;
      const curState = states[stateIndex];
      if (curState === 'idle') {
        targetEnergy = 0;
      } else if (curState === 'listening') {
        targetEnergy = 0.55;
      } else if (curState === 'speaking') {
        targetEnergy = 1.0;
      }
    };

    canvas.addEventListener('click', handleClick);

    const drawAcousticStippleField = (cx: number, cy: number) => {
      const linesCount = 20;
      const stepY = height / (linesCount + 4);

      for (let i = 0; i < linesCount; i++) {
        const baseY = stepY * (i + 2.5);
        const distFromCenter = Math.abs(baseY - cy) / (height * 0.5);
        const alpha = Math.max(0.04, (1 - distFromCenter * 0.7) * 0.28);

        ctx.fillStyle =
          i % 3 === 0
            ? `rgba(234, 179, 40, ${alpha * 0.85})`
            : `rgba(36, 132, 224, ${alpha})`;

        const points = 72;
        for (let j = 0; j <= points; j++) {
          const x = (width / points) * j;
          const dx = (x - cx) / (width * 0.45);
          const domeFalloff = Math.exp(-dx * dx);

          const wave =
            Math.sin(j * 0.22 + phase * 1.5 + i * 0.35) * (8 + energy * 12) * domeFalloff;
          const wave2 = Math.cos(j * 0.15 - phase + i * 0.2) * 4;
          const y = baseY + wave + wave2;

          ctx.beginPath();
          const dotRadius = i % 2 === 0 ? 1.2 : 0.9;
          ctx.arc(x, y, dotRadius, 0, Math.PI * 2);
          ctx.fill();
        }
      }
    };

    const drawTechnicalCrosses = (cx: number, cy: number) => {
      ctx.strokeStyle = 'rgba(234, 179, 40, 0.45)';
      ctx.lineWidth = 1.2;

      const coords = [
        { x: cx - 180, y: cy - 70 },
        { x: cx + 190, y: cy - 60 },
        { x: cx - 210, y: cy + 65 },
        { x: cx + 220, y: cy + 75 }
      ];

      coords.forEach((pt) => {
        ctx.beginPath();
        ctx.moveTo(pt.x - 4, pt.y);
        ctx.lineTo(pt.x + 4, pt.y);
        ctx.moveTo(pt.x, pt.y - 4);
        ctx.lineTo(pt.x, pt.y + 4);
        ctx.stroke();
      });
    };

    const drawHelixOrb = (cx: number, cy: number) => {
      const baseRadius = 78;
      const pulse = Math.sin(phase * 2) * (2 + energy * 6);
      const radius = baseRadius + pulse;

      // Halo exterior
      const haloGrad = ctx.createRadialGradient(cx, cy, radius * 0.5, cx, cy, radius * 1.85);
      haloGrad.addColorStop(0, 'rgba(77, 163, 255, 0.35)');
      haloGrad.addColorStop(0.5, 'rgba(234, 179, 40, 0.15)');
      haloGrad.addColorStop(1, 'rgba(77, 163, 255, 0)');
      ctx.fillStyle = haloGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 1.85, 0, Math.PI * 2);
      ctx.fill();

      // Cuerpo esférico nebuloso (120 puntos orgánicos fluidos)
      const bodyGrad = ctx.createRadialGradient(
        cx - radius * 0.3,
        cy - radius * 0.3,
        radius * 0.1,
        cx,
        cy,
        radius
      );
      bodyGrad.addColorStop(0, '#68B7FF');
      bodyGrad.addColorStop(0.4, '#2484E0');
      bodyGrad.addColorStop(0.8, '#13468B');
      bodyGrad.addColorStop(1, '#091838');

      ctx.save();
      ctx.beginPath();
      const pointsCount = 120;
      for (let k = 0; k < pointsCount; k++) {
        const angle = (k / pointsCount) * Math.PI * 2;
        const wobble = Math.sin(phase * 3 + angle * 4) * (1.6 + energy * 3.2);
        const r = radius + wobble;
        const px = cx + Math.cos(angle) * r;
        const py = cy + Math.sin(angle) * r;

        if (k === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      }
      ctx.closePath();
      ctx.fillStyle = bodyGrad;
      ctx.shadowColor = 'rgba(36, 132, 224, 0.45)';
      ctx.shadowBlur = 24;
      ctx.fill();
      ctx.restore();

      // 28 Filamentos Radiales de Frecuencia
      ctx.save();
      const filaments = 28;
      for (let f = 0; f < filaments; f++) {
        const theta = (f / filaments) * Math.PI * 2 + phase * 0.4;
        const fLength = radius * (0.8 + Math.sin(phase * 4 + f * 2) * 0.25);
        const fx1 = cx + Math.cos(theta) * (radius * 0.4);
        const fy1 = cy + Math.sin(theta) * (radius * 0.4);
        const fx2 = cx + Math.cos(theta) * fLength;
        const fy2 = cy + Math.sin(theta) * fLength;

        ctx.strokeStyle = f % 2 === 0 ? 'rgba(246, 208, 79, 0.45)' : 'rgba(115, 195, 252, 0.35)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(fx1, fy1);
        ctx.lineTo(fx2, fy2);
        ctx.stroke();
      }
      ctx.restore();

      // Anillo Helix Inclinado a -22 grados (anillo áureo/solar distintivo de Yamy)
      ctx.save();
      ctx.translate(cx, cy);
      ctx.rotate(-22 * (Math.PI / 180));

      // 1. Halo áureo suave exterior
      ctx.strokeStyle = 'rgba(246, 208, 79, 0.22)';
      ctx.lineWidth = 16;
      ctx.beginPath();
      ctx.ellipse(0, 0, radius * 0.88, radius * 0.54, 0, 0, Math.PI * 2);
      ctx.stroke();

      // 2. Trazo áureo / solar translúcido principal (lineWidth = 10)
      const helixAuraGrad = ctx.createLinearGradient(
        -radius * 0.9,
        -radius * 0.6,
        radius * 0.9,
        radius * 0.6
      );
      helixAuraGrad.addColorStop(0, 'rgba(246, 208, 79, 0.6)');
      helixAuraGrad.addColorStop(0.5, 'rgba(234, 179, 40, 0.45)');
      helixAuraGrad.addColorStop(1, 'rgba(246, 208, 79, 0.6)');

      ctx.strokeStyle = helixAuraGrad;
      ctx.lineWidth = 10;
      ctx.beginPath();
      ctx.ellipse(0, 0, radius * 0.86, radius * 0.53, 0, 0, Math.PI * 2);
      ctx.stroke();

      // 3. Línea interior fina y brillante
      const helixGrad = ctx.createLinearGradient(
        -radius * 0.9,
        -radius * 0.6,
        radius * 0.9,
        radius * 0.6
      );
      helixGrad.addColorStop(0, '#F6D04F');
      helixGrad.addColorStop(0.5, '#FFFFFF');
      helixGrad.addColorStop(1, '#EAB328');

      ctx.strokeStyle = helixGrad;
      ctx.lineWidth = 2.5;
      ctx.beginPath();
      ctx.ellipse(0, 0, radius * 0.85, radius * 0.52, 0, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      // Núcleo cósmico profundo y destello estelar central
      ctx.fillStyle = '#091838';
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 0.28, 0, Math.PI * 2);
      ctx.fill();

      // Destello blanco y cruz
      ctx.fillStyle = '#FFFFFF';
      ctx.beginPath();
      ctx.arc(cx, cy, 2.5, 0, Math.PI * 2);
      ctx.fill();

      ctx.strokeStyle = 'rgba(255, 255, 255, 0.85)';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.moveTo(cx - 7, cy);
      ctx.lineTo(cx + 7, cy);
      ctx.moveTo(cx, cy - 7);
      ctx.lineTo(cx, cy + 7);
      ctx.stroke();
    };

    const loop = () => {
      if (!isRunning) return;

      energy += (targetEnergy - energy) * 0.08;
      phase += prefersReducedMotion ? 0.005 : 0.016 + energy * 0.024;

      ctx.clearRect(0, 0, width, height);

      const cx = width * 0.5;
      const cy = height * 0.48;

      drawAcousticStippleField(cx, cy);
      drawTechnicalCrosses(cx, cy);
      drawHelixOrb(cx, cy);

      animationFrameId = requestAnimationFrame(loop);
    };

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            if (!isRunning) {
              isRunning = true;
              loop();
            }
          } else {
            isRunning = false;
          }
        });
      },
      { threshold: 0.1 }
    );

    observer.observe(canvas);

    return () => {
      isRunning = false;
      window.removeEventListener('resize', resize);
      canvas.removeEventListener('click', handleClick);
      observer.disconnect();
      cancelAnimationFrame(animationFrameId);
    };
  }, []);

  return (
    <div className="hero-canvas-wrapper">
      <canvas ref={canvasRef} className="nebula-canvas" />
    </div>
  );
};
