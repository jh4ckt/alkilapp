const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const OUTPUT_DIR = path.join(__dirname, 'play-store');

// Create feature graphic 1024x500
const width = 1024;
const height = 500;

// SVG for the feature graphic
const svg = `
<svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" xmlns="http://www.w3.org/2000/svg">
  <!-- Background gradient -->
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#0B2447;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#1E3A5F;stop-opacity:1" />
    </linearGradient>
    <linearGradient id="accent" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" style="stop-color:#FF6B5E;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#E85D4D;stop-opacity:1" />
    </linearGradient>
  </defs>
  <rect width="${width}" height="${height}" fill="url(#bg)"/>
  
  <!-- Decorative geometric shapes -->
  <circle cx="880" cy="-80" r="200" fill="#FF6B5E" opacity="0.1"/>
  <circle cx="-80" cy="580" r="180" fill="#FF6B5E" opacity="0.08"/>
  
  <!-- Left side: App name + tagline -->
  <g transform="translate(80, 140)">
    <text x="0" y="0" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" 
          font-size="56" font-weight="800" fill="white" letter-spacing="-1">AlkilApp</text>
    <text x="0" y="70" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" 
          font-size="24" fill="rgba(255,255,255,0.9)" font-weight="400">Alquileres en Perú</text>
    
    <!-- Accent line -->
    <rect x="0" y="110" width="120" height="5" fill="url(#accent)" rx="2.5"/>
    
    <!-- Features tags -->
    <g transform="translate(0, 140)">
      <g style="font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; font-size:16; fill:rgba(255,255,255,0.85);">
        <text x="0" y="0">🔍 Búsqueda por zona y mapa</text>
        <text x="0" y="35">💬 Chat directo con propietarios</text>
        <text x="0" y="70">🛡️ Verificación DNI + badge confianza</text>
        <text x="0" y="105">⭐ Reseñas y reputación</text>
        <text x="0" y="140">📱 Gestiona tus publicaciones</text>
      </g>
    </g>
  </g>
  
  <!-- Right side: Phone mockup with app preview -->
  <g transform="translate(580, 40)">
    <!-- Phone frame -->
    <rect x="0" y="0" width="380" height="420" rx="28" fill="#1a1a2e" stroke="#333" stroke-width="2"/>
    <rect x="12" y="12" width="356" height="396" rx="20" fill="#0B2447"/>
    
    <!-- Status bar -->
    <rect x="12" y="12" width="356" height="24" fill="#0B2447"/>
    <text x="30" y="30" font-family="sans-serif" font-size="12" fill="white" font-weight="600">AlkilApp</text>
    <text x="300" y="30" font-family="sans-serif" font-size="12" fill="white">9:41</text>
    
    <!-- Search bar -->
    <rect x="24" y="48" width="332" height="44" rx="22" fill="rgba(255,255,255,0.1)" stroke="rgba(255,255,255,0.2)" stroke-width="1"/>
    <text x="50" y="78" font-family="sans-serif" font-size="14" fill="rgba(255,255,255,0.6)">🔍 Buscar inmueble, distrito...</text>
    
    <!-- Map area -->
    <rect x="24" y="106" width="356" height="160" rx="12" fill="#1E3A5F"/>
    <!-- Map pins -->
    <circle cx="140" cy="180" r="8" fill="#FF6B5E"/>
    <circle cx="220" cy="200" r="8" fill="#FFD700"/>
    <circle cx="300" cy="170" r="8" fill="#FF6B5E"/>
    <circle cx="180" cy="240" r="8" fill="#FFD700"/>
    
    <!-- Bottom sheet handle -->
    <rect x="176" y="278" width="48" height="4" rx="2" fill="rgba(255,255,255,0.3)"/>
    
    <!-- Bottom sheet content -->
    <rect x="24" y="288" width="356" height="110" rx="12" fill="rgba(255,255,255,0.05)"/>
    <text x="40" y="310" font-family="sans-serif" font-size="14" fill="white" font-weight="600">Inmuebles Cercanos</text>
    <text x="40" y="335" font-family="sans-serif" font-size="12" fill="rgba(255,255,255,0.7)">Habitación Miraflores • S/ 950</text>
    <text x="40" y="355" font-family="sans-serif" font-size="12" fill="rgba(255,255,255,0.7)">Depto San Miguel • S/ 1,800</text>
    
    <!-- FAB -->
    <circle cx="340" cy="380" r="24" fill="url(#accent)"/>
    <text x="340" y="385" font-family="sans-serif" font-size="14" fill="white" text-anchor="middle" font-weight="bold">📍</text>
  </g>
  
  <!-- Bottom badge -->
  <g transform="translate(80, 450)">
    <text x="0" y="0" font-family="sans-serif" font-size="14" fill="rgba(255,255,255,0.6)">Disponible en Google Play</text>
    <rect x="180" y="-18" width="140" height="36" rx="8" fill="url(#accent)"/>
    <text x="250" y="8" font-family="sans-serif" font-size="14" fill="white" text-anchor="middle" font-weight="600">Descargar</text>
  </g>
</svg>
`;

async function generate() {
  const svgBuffer = Buffer.from(svg);
  await sharp(svgBuffer)
    .resize(1024, 500)
    .png({ quality: 95 })
    .toFile(path.join(OUTPUT_DIR, 'feature-graphic_1024x500.png'));
  
  console.log('✅ Feature graphic generado: feature-graphic_1024x500.png');
}

generate().catch(e => console.error(e));