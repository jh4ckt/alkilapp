/*
 * AlkilApp — Seed script para Firestore (base "alkilappdb")
 * =========================================================
 *
 * Carga datos de prueba realistas de Lima (Perú) en:
 *   - users     → colección "usuarios" (rol: propietario / inquilino / ambos)
 *   - listings  → colección "propiedades" (fusiona el esquema nuevo del spec
 *                 con los campos que ya lee la app Android, p.ej. titulo,
 *                 precio, moneda, barrio, ciudad, lat, lng, fotos)
 *   - reviews   → subcolección usuarios/{userId}/reviews
 *   - chats     → colección "chats" + subcolección chats/{chatId}/messages
 *
 * Es IDEMPOTENTE: antes de insertar borra SOLO los docs de prueba (por IDs
 * fijos, prefijo "seed-") y los demo viejos, no toca las publicaciones reales
 * hechas desde la app ("Cuarto", "Mini departamento", etc.).
 *
 * REQUISITOS
 * ----------
 * 1) Node.js 18+ y npm.
 * 2) Instalar dependencias:
 *      cd backend
 *      npm install
 *
 * 3) Credenciales de Google Cloud. El proyecto es:
 *        gen-lang-client-0040505884
 *    y la base de datos Firestore es la NOMBRADA "alkilappdb" (el proyecto NO
 *    tiene base "(default)"; usar el ID de la base es obligatorio).
 *
 *    Opción A (recomendada): service account JSON.
 *      - Ya está generada en  backend/credentials/alkilapp-seed-sa.json
 *        (cuenta alkilapp-seed@gen-lang-client-0040505884, rol datastore.user).
 *      - El script la usa por defecto automáticamente.
 *
 *    Opción B: variable de entorno clásica (cualquier account con permisos).
 *      Windows (PowerShell):
 *          $env:GOOGLE_APPLICATION_CREDENTIALS="C:\ruta\a\clave.json"
 *      Linux/macOS (bash):
 *          export GOOGLE_APPLICATION_CREDENTIALS="/ruta/a/clave.json"
 *
 * EJECUCIÓN
 * ---------
 *   cd backend
 *   npm run seed
 *   (o: node seed.js)
 *
 * VERIFICACIÓN RÁPIDA DESPUÉS DE CORRERLO
 * ---------------------------------------
 *   gcloud auth application-default login   # una sola vez, si hace falta
 *   node -e "const {Firestore}=require('@google-cloud/firestore');const db=new Firestore({projectId:'gen-lang-client-0040505884',databaseId:'alkilappdb'});db.collection('usuarios').limit(10).get().then(s=>console.log('usuarios:',s.size));db.collection('propiedades').limit(10).get().then(s=>console.log('propiedades:',s.size));db.collection('chats').limit(5).get().then(s=>console.log('chats:',s.size));"
 *
 * NOTA DE SEGURIDAD
 * -----------------
 * backend/credentials/ NO debe subirse al repositorio (ya está en .gitignore).
 * La cuenta alkilapp-seed solo tiene "roles/datastore.user" (lectura/escritura
 * de Firestore, sin acceso a nada más del proyecto).
 */

const path = require('path');
const fs = require('fs');
const zlib = require('zlib');
const { Firestore } = require('@google-cloud/firestore');

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';

// ---- Credenciales -----------------------------------------------------------
// A) GOOGLE_APPLICATION_CREDENTIALS en el entorno, o
// B) la key de la service account generada en este repo (backend/credentials/).
function resolveCredentials() {
  const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (env && fs.existsSync(env)) return env;
  const fallback = path.join(__dirname, 'credentials', 'alkilapp-seed-sa.json');
  if (fs.existsSync(fallback)) return fallback;
  throw new Error(
    'No find credentials. Set GOOGLE_APPLICATION_CREDENTIALS or restore ' +
      'backend/credentials/alkilapp-seed-sa.json'
  );
}

const db = new Firestore({
  projectId: PROJECT_ID,
  databaseId: DATABASE_ID,
  keyFilename: resolveCredentials(),
});

// ---- Utilidades -------------------------------------------------------------
function geohash(lat, lon, precision = 9) {
  const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz';
  let latMin = -90, latMax = 90, lonMin = -180, lonMax = 180;
  let hash = '', bit = 0, ch = 0, even = true;
  while (hash.length < precision) {
    const mid = even ? (lonMin + lonMax) / 2 : (latMin + latMax) / 2;
    if (even) {
      if (lon >= mid) { ch |= 1 << (4 - bit); lonMin = mid; } else { lonMax = mid; }
    } else {
      if (lat >= mid) { ch |= 1 << (4 - bit); latMin = mid; } else { latMax = mid; }
    }
    even = !even;
    if (bit < 4) bit++;
    else { hash += BASE32[ch]; bit = 0; ch = 0; }
  }
  return hash;
}

const now = () => new Date();

// ---- PNG de color sólido (foto placeholder base64, sin dependencias) --------
// La tarjeta del listado usa `fotos` (base64), no las URLs de `photos`. Para que
// los seeds se visualicen con área de foto, se genera un PNG sólido 4x4 en el
// color indicado (hex "#RRGGBB") usando solo zlib nativo de Node.
const _crcTable = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
const _crc32 = (buf) => {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = _crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
function solidPngBase64(hex) {
  const v = hex.replace('#', '');
  const r = parseInt(v.slice(0, 2), 16);
  const g = parseInt(v.slice(2, 4), 16);
  const b = parseInt(v.slice(4, 6), 16);
  const W = 4, H = 4, stride = 1 + W * 4;
  const raw = Buffer.alloc(stride * H);
  for (let y = 0; y < H; y++) {
    raw[y * stride] = 0;
    raw[y * stride + 1] = r; raw[y * stride + 2] = g;
    raw[y * stride + 3] = b; raw[y * stride + 4] = 255;
    raw[y * stride + 5] = r; raw[y * stride + 6] = g;
    raw[y * stride + 7] = b; raw[y * stride + 8] = 255;
    raw[y * stride + 9] = r; raw[y * stride + 10] = g;
    raw[y * stride + 11] = b; raw[y * stride + 12] = 255;
    raw[y * stride + 13] = r; raw[y * stride + 14] = g;
    raw[y * stride + 15] = b; raw[y * stride + 16] = 255;
  }
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length, 0);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(_crc32(body), 0);
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0);
  ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8; ihdr[9] = 6; // 8-bit, RGBA
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
  return png.toString('base64');
}

async function deleteDoc(ref) {
  try { await ref.delete(); } catch { /* no existía */ }
}

async function deleteCollection(rootRef) {
  const snap = await rootRef.get();
  await Promise.all(snap.docs.map((d) => d.ref.delete()));
}

/** Borra los docs seed por IDs fijos (idempotencia). NUNCA toca docs reales. */
async function cleanSeedData() {
  log('Limpieza de datos de prueba previos...');

  // Usuarios seed + sus subcolecciones de reviews.
  for (const uid of ['u-propietario-1', 'u-inquilino-1', 'u-ambos-1', 'u-propietario-2']) {
    await deleteCollection(db.collection('usuarios').doc(uid).collection('reviews'));
    await deleteDoc(db.collection('usuarios').doc(uid));
  }

  // Listings seed (en la colección que ya lee la app) + demos de la ronda 1.
  const listingIds = [
    'seed-l-hab-miraflores',
    'seed-l-depa-sanmiguel',
    'seed-l-depa-surco',
    'seed-l-casa-surco',
    // Lote extra 2026-09-18 (10 inmuebles, distritos distintos de Lima):
    'seed-l-depa-sanisidro',
    'seed-l-hab-barranco',
    'seed-l-casa-lamolina',
    'seed-l-depa-sanborja',
    'seed-l-depa-magdalena',
    'seed-l-hab-jesusmaria',
    'seed-l-depa-lince',
    'seed-l-depa-pueblolibre',
    'seed-l-depa-losollivos',
    'seed-l-otro-ate',
    // Antiguos demos (reemplazados por la nueva carga):
    'departamento-belgrano-cabildo-1400',
    'departamento-palermo-thames-1500',
    'departamento-puerto-madero-avda',
    'loft-san-telmo-defensa-700',
    'monoambiente-recoleta-callao-1200',
    'casa-palermo-venta-guemes-3000',
  ];
  for (const id of listingIds) {
    await deleteDoc(db.collection('propiedades').doc(id));
  }

  // Chat seed (+ mensajes). Si se agregan chats nuevos, añadirlos también aquí.
  const chatIds = ['chat-demo-01', 'chat-papa-renzo', 'chat-papa-diego'];
  for (const id of chatIds) {
    const chatRef = db.collection('chats').doc(id);
    await deleteCollection(chatRef.collection('messages'));
    await deleteDoc(chatRef);
  }
}

// ---- Datos seed -------------------------------------------------------------
const users = [
  {
    _id: 'u-propietario-1',
    userId: 'u-propietario-1',
    name: 'Renzo Salazar',
    email: 'renzo.salazar@example.pe',
    phoneNumber: '+51987654321',
    profilePicture: 'https://randomuser.me/api/portraits/men/32.jpg',
    role: 'propietario',
    verification: { identityVerified: true, emailVerified: true, phoneVerified: true },
    trustLevel: 'verified_premium',
    verificationBadge: true,
    rating: 4.8,
    totalRatings: 12,
    createdAt: now(),
  },
  {
    _id: 'u-inquilino-1',
    userId: 'u-inquilino-1',
    name: 'Valeria Quispe',
    email: 'valeria.quispe@example.pe',
    phoneNumber: '+51912345678',
    profilePicture: 'https://randomuser.me/api/portraits/women/44.jpg',
    role: 'inquilino',
    verification: { identityVerified: false, emailVerified: true, phoneVerified: true },
    trustLevel: 'none',
    verificationBadge: false,
    rating: 4.5,
    totalRatings: 3,
    createdAt: now(),
  },
  {
    _id: 'u-ambos-1',
    userId: 'u-ambos-1',
    name: 'Diego Flores',
    email: 'diego.flores@example.pe',
    phoneNumber: '+51955123456',
    profilePicture: 'https://randomuser.me/api/portraits/men/75.jpg',
    role: 'ambos',
    verification: { identityVerified: true, emailVerified: true, phoneVerified: false },
    trustLevel: 'basic',
    verificationBadge: true,
    rating: 4.9,
    totalRatings: 7,
    createdAt: now(),
  },
  {
    _id: 'u-propietario-2',
    userId: 'u-propietario-2',
    name: 'María Gutiérrez',
    email: 'maria.gutierrez@example.pe',
    phoneNumber: '+51911223344',
    profilePicture: 'https://randomuser.me/api/portraits/women/68.jpg',
    role: 'propietario',
    verification: { identityVerified: false, emailVerified: false, phoneVerified: true },
    trustLevel: 'none',
    verificationBadge: false,
    rating: 0,
    totalRatings: 0,
    createdAt: now(),
  },
];

/** Convierte un listing del spec al documento final (campos app + campos nuevos). */
function buildListing({ listingId, titulo, descripcion, propertyType, rentalType, precio,
  direccion, barrio, lat, lng, features, roommatePreferences, ownerId, contacto,
  comodidades, ambientes = features.bedrooms, superficieM2 = features.areaSqm,
  photoSeed, isFeatured = false, featuredUntil = null, fotoColor = null }, photoSeedLegacy) {
  const createdAt = now();
  const _photoSeed = photoSeedLegacy || photoSeed;
  return {
    // Campos del spec (users/listings/chats).
    listingId,
    title: titulo,
    description: descripcion,
    country: 'PE',
    city: 'Lima',
    district: barrio,
    propertyType,
    rentalType,
    price: precio,
    currency: 'PEN',
    features,
    roommatePreferences: roommatePreferences || {},
    location: { latitude: lat, longitude: lng, address: direccion },
    geohash: geohash(lat, lng),
    photos: [
      `https://picsum.photos/seed/${photoSeed}-1/600/400`,
      `https://picsum.photos/seed/${photoSeed}-2/600/400`,
    ],
    ownerId,
    createdAt,
    updatedAt: createdAt,
    status: 'active',
    viewsCount: 100 + Math.floor(Math.random() * 200),
    unreadCount: null,

    // Monetización: anuncio destacado (borde/etiqueta "Destacado" en la app).
    isFeatured,
    featuredUntil: featuredUntil ? new Date(featuredUntil) : null,

    // Campos que ya lee la app Android (fusionados, sin romper nada).
    titulo,
    descripcion,
    tipo: propertyType,
    operacion: 'alquiler',
    precio,
    moneda: 'PEN',
    direccion,
    barrio,
    ciudad: 'Lima',
    lat,
    lng,
    idPropietario: ownerId,
    contacto,
    estado: 'disponible',
    ambientes,
    superficieM2,
    comodidades: comodidades || [],
    fotos: fotoColor ? [solidPngBase64(fotoColor)] : [], // la app muestra base64; los seeds usan urls en `photos`
    esDemo: true,
  };
}

const listings = [
  buildListing({
    listingId: 'seed-l-hab-miraflores',
    titulo: 'Habitación en departamento compartido (Co-living Miraflores)',
    descripcion:
      'Habitación amoblada en depto compartido a 3 cuadras del Óvalo de Miraflores. ' +
      'Wifi, agua, luz y limpieza de áreas comunes incluidos. Convivencia tranquila con 2 ' +
      'roomies profesionales. Disponible desde el 1 del próximo mes.',
    propertyType: 'habitacion',
    rentalType: 'co-living',
    precio: 950,
    direccion: 'Av. José Larco 1234, Miraflores',
    barrio: 'Miraflores',
    lat: -12.1182,
    lng: -77.028,
    features: {
      bedrooms: 1, bathrooms: 1, areaSqm: 12, floor: 3,
      hasElevator: true, allowsPets: false, furnished: true, utilitiesIncluded: true,
    },
    roommatePreferences: { preferredGender: 'Indistinto', requiresStudentStatus: false },
    ownerId: 'u-propietario-1',
    contacto: 'renzo.salazar@example.pe',
    comodidades: ['Wifi', 'Agua', 'Luz'],
    isFeatured: true,
    featuredUntil: Date.now() + 7 * 24 * 60 * 60 * 1000,
  }, 'alk-hab-01'),

  buildListing({
    listingId: 'seed-l-depa-sanmiguel',
    titulo: 'Departamento amoblado en San Miguel',
    descripcion:
      'Depto amoblado frente al Real Plaza San Miguel. 2 dormitorios, 1 baño, cocina equipada, ' +
      'lavandería en el piso y dos spots de estacionamiento de visita. Zona súper conectada ' +
      '(Metropolitano y ciclovía de la Av. La Marina).',
    propertyType: 'departamento',
    rentalType: 'largo_plazo',
    precio: 1800,
    direccion: 'Av. La Marina 2555, San Miguel',
    barrio: 'San Miguel',
    lat: -12.0766,
    lng: -77.0872,
    features: {
      bedrooms: 2, bathrooms: 1, areaSqm: 55, floor: 5,
      hasElevator: true, allowsPets: true, furnished: true, utilitiesIncluded: false,
    },
    ownerId: 'u-propietario-1',
    contacto: 'renzo.salazar@example.pe',
    comodidades: ['Ascensor', 'Estacionamiento de visita'],
  }, 'alk-dep-01'),

  buildListing({
    listingId: 'seed-l-depa-surco',
    titulo: 'Departamento sin amoblar en Santiago de Surco',
    descripcion:
      'Depto para estrenar sin amoblar, ideal para armar a tu gusto. 2 dormitorios con closet, ' +
      '2 baños, cocina con barra, balcón y 1 estacionamiento. A metros de Caminos del Inca y ' +
      'de colegios de la zona.',
    propertyType: 'departamento',
    rentalType: 'largo_plazo',
    precio: 1400,
    direccion: 'Av. Caminos del Inca 1520, Santiago de Surco',
    barrio: 'Surco',
    lat: -12.1105,
    lng: -76.9788,
    features: {
      bedrooms: 2, bathrooms: 2, areaSqm: 68, floor: 2,
      hasElevator: false, allowsPets: true, furnished: false, utilitiesIncluded: false,
    },
    ownerId: 'u-ambos-1',
    contacto: 'diego.flores@example.pe',
    comodidades: ['Balcón', 'Cocina con barra'],
  }, 'alk-dep-02'),

  buildListing({
    listingId: 'seed-l-casa-surco',
    titulo: 'Casa de 2 pisos en Monterrico (Surco)',
    descripcion:
      'Casa de 2 pisos en condominio cerrado de Monterrico. 3 dormitorios, 2 baños completos, ' +
      'patio con jardín, cuarto de servicio y garaje para 2 autos. Incluye muebles de sala y comedor.',
    propertyType: 'casa',
    rentalType: 'largo_plazo',
    precio: 3200,
    direccion: 'Av. El Derby 780, Santiago de Surco',
    barrio: 'Surco',
    lat: -12.1387,
    lng: -76.967,
    features: {
      bedrooms: 3, bathrooms: 2, areaSqm: 140, floor: 2,
      hasElevator: false, allowsPets: true, furnished: true, utilitiesIncluded: false,
    },
    ownerId: 'u-ambos-1',
    contacto: 'diego.flores@example.pe',
    comodidades: ['Jardín', 'Garaje para 2 autos', 'Cuarto de servicio'],
    isFeatured: true,
    featuredUntil: Date.now() + 3 * 24 * 60 * 60 * 1000,
  }, 'alk-casa-01'),

  // ---- Lote extra (2026-09-18): 10 inmuebles con direcciones ficticias en
  //      distintos distritos de Lima, para visualizar el listado y el mapa. ----
  buildListing({
    listingId: 'seed-l-depa-sanisidro',
    titulo: 'Departamento de 1 dormitorio amoblado en San Isidro',
    descripcion:
      'Depto amoblado a pasos del parque El Olivar. 1 dormitorio, 1 baño, cocina americana, ' +
      'wifi incluido y gimnasio en el edificio. Zona exclusiva y muy segura.',
    propertyType: 'departamento',
    rentalType: 'largo_plazo',
    precio: 1600,
    direccion: 'Av. Petit Thouars 2950, San Isidro',
    barrio: 'San Isidro',
    lat: -12.099,
    lng: -77.0489,
    features: {
      bedrooms: 1, bathrooms: 1, areaSqm: 42, floor: 4,
      hasElevator: true, allowsPets: false, furnished: true, utilitiesIncluded: true,
    },
    ownerId: 'u-propietario-1',
    contacto: 'renzo.salazar@example.pe',
    comodidades: ['Wifi', 'Ascensor', 'Gimnasio'],
    fotoColor: '#C8D6E8',
  }, 'alk-dep-sanisidro'),

  buildListing({
    listingId: 'seed-l-hab-barranco',
    titulo: 'Habitación con balcón al malecón en Barranco',
    descripcion:
      'Habitación con balcón y vista al mar en casona restaurada de Barranco. Cocina y baño ' +
      'compartidos, ambiente artístico y tranquilo. A 5 min del Museo Pedro de Osma.',
    propertyType: 'habitacion',
    rentalType: 'co-living',
    precio: 800,
    direccion: 'Jr. Unión 240, Barranco',
    barrio: 'Barranco',
    lat: -12.1447,
    lng: -77.0224,
    features: {
      bedrooms: 1, bathrooms: 1, areaSqm: 10, floor: 2,
      hasElevator: false, allowsPets: true, furnished: true, utilitiesIncluded: true,
    },
    ownerId: 'u-propietario-2',
    contacto: 'maria.gutierrez@example.pe',
    comodidades: ['Wifi', 'Agua', 'Limpieza de área común'],
    fotoColor: '#E8D3C8',
  }, 'alk-hab-barranco'),

  buildListing({
    listingId: 'seed-l-casa-lamolina',
    titulo: 'Casa de 4 dormitorios con piscina en La Molina',
    descripcion:
      'Casa independiente de 3 pisos en condominio con piscina y áreas verdes. 4 dormitorios, ' +
      '3 baños, sala doble, estudio, cuarto de servicio y 2 estacionamientos. Cerca de la Av. ' +
      'La Molina y del club El Polo.',
    propertyType: 'casa',
    rentalType: 'largo_plazo',
    precio: 4200,
    direccion: 'Calle Los Girasoles 180, La Molina',
    barrio: 'La Molina',
    lat: -12.0778,
    lng: -76.9355,
    features: {
      bedrooms: 4, bathrooms: 3, areaSqm: 180, floor: 3,
      hasElevator: false, allowsPets: true, furnished: false, utilitiesIncluded: false,
    },
    ownerId: 'u-propietario-1',
    contacto: 'renzo.salazar@example.pe',
    comodidades: ['Piscina compartida', 'Jardín', 'Garaje para 2 autos', 'Cuarto de servicio'],
    isFeatured: true,
    featuredUntil: Date.now() + 7 * 24 * 60 * 60 * 1000,
    fotoColor: '#D4C8E8',
  }, 'alk-casa-lamolina'),

  buildListing({
    listingId: 'seed-l-depa-sanborja',
    titulo: 'Departamento de 3 dormitorios frente al parque en San Borja',
    descripcion:
      'Depto con vista al parque de San Borja y cerca del Centro Comercial San Borja. 3 dormitorios ' +
      'concloset, 2 baños, cocina equipada, terraza y estacionamiento techado.',
    propertyType: 'departamento',
    rentalType: 'largo_plazo',
    precio: 2600,
    direccion: 'Av. San Borja Norte 760, San Borja',
    barrio: 'San Borja',
    lat: -12.1068,
    lng: -76.9956,
    features: {
      bedrooms: 3, bathrooms: 2, areaSqm: 95, floor: 7,
      hasElevator: true, allowsPets: true, furnished: true, utilitiesIncluded: false,
    },
    ownerId: 'u-propietario-1',
    contacto: 'renzo.salazar@example.pe',
    comodidades: ['Ascensor', 'Estacionamiento techado', 'Terraza'],
    fotoColor: '#C8E8D4',
  }, 'alk-dep-sanborja'),

  buildListing({
    listingId: 'seed-l-depa-magdalena',
    titulo: 'Departamento dúplex amoblado en Magdalena del Mar',
    descripcion:
      'Dúplex amoblado de 2 dormitorios a media cuadra de la Av. Brasil. 1 baño y medio, lavandería ' +
      'propia y azotea con vista. A minutos de la UNMSM y del militae.',
    propertyType: 'departamento',
    rentalType: 'largo_plazo',
    precio: 2200,
    direccion: 'Av. Brasil 1450, Magdalena del Mar',
    barrio: 'Magdalena del Mar',
    lat: -12.098,
    lng: -77.078,
    features: {
      bedrooms: 2, bathrooms: 2, areaSqm: 80, floor: 1,
      hasElevator: false, allowsPets: true, furnished: true, utilitiesIncluded: false,
    },
    ownerId: 'u-ambos-1',
    contacto: 'diego.flores@example.pe',
    comodidades: ['Azotea privada', 'Lavandería propia'],
    fotoColor: '#E8E4C8',
  }, 'alk-dep-magdalena'),

  buildListing({
    listingId: 'seed-l-hab-jesusmaria',
    titulo: 'Habitación amplia cerca del Hospital Edgardo Rebagliati',
    descripcion:
      'Habitación amplia con baño privado en Jesús María. Ideal para profesionales de salud o ' +
      'internos. Incluye desayuno de cortesía los fines de semana.',
    propertyType: 'habitacion',
    rentalType: 'largo_plazo',
    precio: 750,
    direccion: 'Av. Cuba 890, Jesús María',
    barrio: 'Jesús María',
    lat: -12.0847,
    lng: -77.0456,
    features: {
      bedrooms: 1, bathrooms: 1, areaSqm: 14, floor: 3,
      hasElevator: true, allowsPets: false, furnished: true, utilitiesIncluded: true,
    },
    ownerId: 'u-propietario-2',
    contacto: 'maria.gutierrez@example.pe',
    comodidades: ['Wifi', 'Baño privado', 'Limpieza semanal'],
    fotoColor: '#D9C7B8',
  }, 'alk-hab-jesusmaria'),

  buildListing({
    listingId: 'seed-l-depa-lince',
    titulo: 'Departamento de 2 dormitorios en Lince (cerca de la Av. Arenales)',
    descripcion:
      'Depto de 2 dormitorios con 1 baño y cocina independiente. A 3 cuadras del Mercado de Lince ' +
      'y con fácil acceso al Metropolitano. Perfecto para parejas o roomies.',
    propertyType: 'departamento',
    rentalType: 'largo_plazo',
    precio: 1500,
    direccion: 'Av. Arenales 2100, Lince',
    barrio: 'Lince',
    lat: -12.0886,
    lng: -77.0412,
    features: {
      bedrooms: 2, bathrooms: 1, areaSqm: 60, floor: 2,
      hasElevator: false, allowsPets: true, furnished: false, utilitiesIncluded: false,
    },
    ownerId: 'u-propietario-2',
    contacto: 'maria.gutierrez@example.pe',
    comodidades: ['Cocina independiente'],
    fotoColor: '#B8C4CE',
  }, 'alk-dep-lince'),

  buildListing({
    listingId: 'seed-l-depa-pueblolibre',
    titulo: 'Departamento de 1 dormitorio amoblado en Pueblo Libre',
    descripcion:
      'Depto amoblado cerca del Museo Nacional de Arqueología. 1 dormitorio con closet, cocina ' +
      'americana y balcón. Ideal para una persona. Agua y luz incluidos.',
    propertyType: 'departamento',
    rentalType: 'largo_plazo',
    precio: 1350,
    direccion: 'Av. Bolívar 1150, Pueblo Libre',
    barrio: 'Pueblo Libre',
    lat: -12.0768,
    lng: -77.0705,
    features: {
      bedrooms: 1, bathrooms: 1, areaSqm: 38, floor: 3,
      hasElevator: true, allowsPets: false, furnished: true, utilitiesIncluded: true,
    },
    ownerId: 'u-ambos-1',
    contacto: 'diego.flores@example.pe',
    comodidades: ['Wifi', 'Agua', 'Luz', 'Balcón'],
    fotoColor: '#C2C9D6',
  }, 'alk-dep-pueblolibre'),

  buildListing({
    listingId: 'seed-l-depa-losollivos',
    titulo: 'Departamento económico de 3 dormitorios en Los Olivos',
    descripcion:
      'Depto de 3 dormitorios y 2 baños en condominio con juegos infantiles. A pasos del CC Mega ' +
      'Plaza y de la universidad. Opción perfecta para familias o estudiantes.',
    propertyType: 'departamento',
    rentalType: 'largo_plazo',
    precio: 1100,
    direccion: 'Av. Alfredo Mendiola 4900, Los Olivos',
    barrio: 'Los Olivos',
    lat: -11.9696,
    lng: -77.0739,
    features: {
      bedrooms: 3, bathrooms: 2, areaSqm: 85, floor: 4,
      hasElevator: true, allowsPets: true, furnished: false, utilitiesIncluded: false,
    },
    ownerId: 'u-propietario-1',
    contacto: 'renzo.salazar@example.pe',
    comodidades: ['Ascensor', 'Juegos infantiles'],
    isFeatured: true,
    featuredUntil: Date.now() + 15 * 24 * 60 * 60 * 1000,
    fotoColor: '#E8CFC8',
  }, 'alk-dep-losollivos'),

  buildListing({
    listingId: 'seed-l-otro-ate',
    titulo: 'Estudio tipo loft independiente en Ate',
    descripcion:
      'Loft independiente con todo integrado en zona industrial residencial de Ate. Cuenta con ' +
      'baño, mini cocina y escritorio. A 10 min de la Av. Javier Prado Este, ideal para teletrabajo.',
    propertyType: 'otro',
    rentalType: 'largo_plazo',
    precio: 900,
    direccion: 'Av. Separadora Industrial 1250, Ate',
    barrio: 'Ate',
    lat: -12.0305,
    lng: -76.921,
    features: {
      bedrooms: 1, bathrooms: 1, areaSqm: 28, floor: 1,
      hasElevator: false, allowsPets: false, furnished: true, utilitiesIncluded: false,
    },
    ownerId: 'u-ambos-1',
    contacto: 'diego.flores@example.pe',
    comodidades: ['Escritorio', 'Mini cocina', 'Baño privado'],
    fotoColor: '#DCD6C9',
  }, 'alk-otro-ate'),
];

const reviews = [
  {
    _path: ['usuarios', 'u-propietario-1', 'reviews', 'rev-renzo-01'],
    reviewId: 'rev-renzo-01',
    authorId: 'u-inquilino-1',
    authorName: 'Valeria Quispe',
    rating: 5,
    comment:
      'Renzo es un excelente arrendador: responde rápido, todo transparente y el ' +
      'departamento estaba impecable al llegar. 100% recomendado.',
    createdAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
  },
  {
    _path: ['usuarios', 'u-propietario-1', 'reviews', 'rev-renzo-02'],
    reviewId: 'rev-renzo-02',
    authorId: 'u-ambos-1',
    authorName: 'Diego Flores',
    rating: 4,
    comment: 'Buen trato y contrato claro. Le puse 4 estrellas porque tardó un poco el contrato.',
    createdAt: new Date(Date.now() - 12 * 24 * 60 * 60 * 1000),
  },
];

// Real (Google Sign-In) del papá. Los chats seed lo incluyen como participante
// para que le aparezcan en su bandeja ("Mensajes").
const REAL_UID_PAPA = 'smpJfxQT0vUMaCMalJWUej1qPnw1';

// Cada chat: doc + sus mensajes (id determinista para que el cleanup funcione).
const chats = [
  {
    _id: 'chat-demo-01',
    chatId: 'chat-demo-01',
    listingId: 'seed-l-hab-miraflores',
    listingTitle: 'Habitación en departamento compartido (Co-living Miraflores)',
    participants: ['u-inquilino-1', 'u-propietario-1'],
    lastMessage: 'Perfecto, el sábado confirmo por aquí.',
    lastMessageAt: new Date(Date.now() - 2 * 60 * 60 * 1000),
    unreadCount: { 'u-inquilino-1': 0, 'u-propietario-1': 1 },
    _messages: [
      {
        _id: 'msg-01', messageId: 'msg-01', senderId: 'u-inquilino-1',
        text: 'Hola Renzo, ¿la habitación en Larco sigue disponible?',
        sentAt: new Date(Date.now() - 3 * 60 * 60 * 1000),
      },
      {
        _id: 'msg-02', messageId: 'msg-02', senderId: 'u-propietario-1',
        text: 'Hola Valeria, sí, está disponible. ¿Te interesa visitarla este sábado?',
        sentAt: new Date(Date.now() - 2 * 60 * 60 * 1000),
      },
      {
        _id: 'msg-03', messageId: 'msg-03', senderId: 'u-inquilino-1',
        text: 'Perfecto, el sábado confirmo por aquí.',
        sentAt: new Date(Date.now() - 2 * 60 * 60 * 1000),
      },
    ],
  },
  {
    _id: 'chat-papa-renzo',
    chatId: 'chat-papa-renzo',
    listingId: 'seed-l-hab-miraflores',
    listingTitle: 'Habitación en departamento compartido (Co-living Miraflores)',
    participants: [REAL_UID_PAPA, 'u-propietario-1'],
    lastMessage: '¡Claro! Tengo cupos disponibles. ¿Te parece el miércoles después de las 5pm?',
    lastMessageAt: new Date(Date.now() - 45 * 60 * 1000),
    unreadCount: { [REAL_UID_PAPA]: 1, 'u-propietario-1': 0 },
    _messages: [
      {
        _id: 'msg-papa-renzo-01', messageId: 'msg-papa-renzo-01', senderId: REAL_UID_PAPA,
        text: 'Hola Renzo, vi el co-living de Miraflores en la app. ¿Podría visitarlo esta semana?',
        sentAt: new Date(Date.now() - 60 * 60 * 1000),
      },
      {
        _id: 'msg-papa-renzo-02', messageId: 'msg-papa-renzo-02', senderId: 'u-propietario-1',
        text: '¡Claro! Tengo cupos disponibles. ¿Te parece el miércoles después de las 5pm?',
        sentAt: new Date(Date.now() - 45 * 60 * 1000),
      },
    ],
  },
  {
    _id: 'chat-papa-diego',
    chatId: 'chat-papa-diego',
    listingId: 'seed-l-depa-surco',
    listingTitle: 'Departamento sin amoblar en Santiago de Surco',
    participants: [REAL_UID_PAPA, 'u-ambos-1'],
    lastMessage: 'Sí, tiene patio y la zona es pet friendly. ¿Querés agendar una visita?',
    lastMessageAt: new Date(Date.now() - 3 * 60 * 60 * 1000),
    unreadCount: { [REAL_UID_PAPA]: 1, 'u-ambos-1': 0 },
    _messages: [
      {
        _id: 'msg-papa-diego-01', messageId: 'msg-papa-diego-01', senderId: REAL_UID_PAPA,
        text: 'Buenas Diego, ¿el depto de Caminos del Inca acepta mascotas?',
        sentAt: new Date(Date.now() - 3.5 * 60 * 60 * 1000),
      },
      {
        _id: 'msg-papa-diego-02', messageId: 'msg-papa-diego-02', senderId: 'u-ambos-1',
        text: 'Sí, tiene patio y la zona es pet friendly. ¿Querés agendar una visita?',
        sentAt: new Date(Date.now() - 3 * 60 * 60 * 1000),
      },
    ],
  },
];

// ---- Carga ------------------------------------------------------------------
function log(msg) { console.log(`[seed] ${msg}`); }

async function seedAll() {
  log(`Proyecto: ${PROJECT_ID}  |  Base: ${DATABASE_ID}`);
  await cleanSeedData();

  log(`Creando ${users.length} usuarios...`);
  for (const u of users) {
    const { _id, ...data } = u;
    await db.collection('usuarios').doc(_id).set(data);
  }

  log(`Creando ${listings.length} listings en "propiedades"...`);
  for (const l of listings) {
    await db.collection('propiedades').doc(l.listingId).set(l);
  }

  log(`Creando ${reviews.length} reviews (subcolección usuarios/{id}/reviews)...`);
  for (const r of reviews) {
    const [col0, doc0, subcol, docId] = r._path;
    const { _path, ...data } = r;
    await db.collection(col0).doc(doc0).collection(subcol).doc(docId).set(data);
  }

  log(`Creando ${chats.length} chats + mensajes...`);
  for (const c of chats) {
    const { _id, _messages, ...chatData } = c;
    await db.collection('chats').doc(_id).set(chatData);
    for (const m of _messages) {
      const { _id: msgDocId, ...mData } = m;
      await db.collection('chats').doc(_id).collection('messages').doc(msgDocId).set(mData);
    }
  }

  log('¡Seed completado! ✅');
}

seedAll()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('[seed] ERROR:', e.message);
    process.exit(1);
  });