## AlkilApp v1.40 (vc22)

### ✅ Registro nacional con ciudades por departamento (25 departamentos)
- **strings.xml**: 25 nuevos arrays `ciudades_<departamento>` con capitales y ciudades principales
  - Amazonas: Chachapoyas, Bagua Grande, Bagua, Utcubamba, Luya, Rodríguez de Mendoza
  - Ancash: Huaraz, Chimbote, Casma, Huari, Carhuaz, Yungay, Caraz, Recuay
  - Apurímac: Abancay, Andahuaylas, Chalhuanca, Chincheros, Cotabambas
  - Arequipa: Arequipa, Camaná, Mollendo, Chivay, La Joya, Pedregal, Majes
  - Ayacucho: Ayacucho, Huamanga, Huanta, Coracora, Pauza, Pichari
  - Cajamarca: Cajamarca, Jaén, Celendín, Chota, Cutervo, San Ignacio, San Marcos
  - Callao: Callao, Ventanilla, Bellavista, La Perla, Carmen de la Legua
  - Cusco: Cusco, Urubamba, Calca, Paucartambo, Quillabamba, Sicuani, Espinar
  - Huancavelica: Huancavelica, Castrovirreyna, Acobamba, Angaraes, Huaytará
  - Huánuco: Huánuco, Tingo María, Huacaybamba, Lauricocha, Leoncio Prado, Marañón, Pachitea
  - Ica: Ica, Chincha, Pisco, Nazca, Palpa
  - Junín: Huancayo, Jauja, Tarma, Concepción, Junín, Chanchamayo, Satipo
  - La Libertad: Trujillo, Chepén, Pacasmayo, Guadalupe, Otuzco, Santiago de Chuco, Virú
  - Lambayeque: Chiclayo, Lambayeque, Ferreñafe, Motupe, Zaña
  - Lima: 43 distritos + principales (Lima, Callao, San Juan de Lurigancho, Miraflores, San Isidro, San Borja, La Molina, Barranco, etc.)
  - Loreto: Iquitos, Yurimaguas, Nauta, Requena, Caballococha, San Lorenzo
  - Madre de Dios: Puerto Maldonado, Iberia, Inapari, Las Piedras
  - Moquegua: Moquegua, Ilo, Omacho, Carumas
  - Pasco: Cerro de Pasco, Oxapampa, Yanahuanca, Huariaca
  - Piura: Piura, Sullana, Talara, Paita, Sechura, Morropón, Huancabamba, Ayabaca
  - Puno: Puno, Juliaca, Azángaro, Ayaviri, Moho, Huancané, Carabaya
  - San Martín: Moyobamba, Tarapoto, Juanjuí, Bellavista, Picota, El Dorado, Lamas, Rioja
  - Tacna: Tacna, Locumba, Tarata, Candarave
  - Tumbes: Tumbes, Zorritos, Punta Sal, Contralmirante Villar
  - Ucayali: Pucallpa, Aguaytía, Atalaya, Padre Abad, Contamana

- **RegistrarPropiedadActivity**: spinner Departamento + listener que carga ciudades dinámicamente
  - Lima → 43 distritos
  - Otros departamentos → ciudades principales + "Otro"
- **MainActivity (filtros)**: mismo comportamiento en `abrirDialogoFiltros()`
- **String actualizado**: "Ahora se filtra a nivel nacional (Perú). Selecciona departamento y ciudad."

### 🔧 Fixes
- **Fix chat permission denied**: revertido `.sorted()` en `participants` array (PropiedadDetalleActivity + PerfilPropietarioActivity) para mantener orden original al crear chat
- **Debug logs**: añadidos en `abrirDetallePropiedad` y `PropiedadAdapter` clicks

### Técnico
- Version bump: `versionCode 22`, `versionName 1.40`
- Build: `gradlew assembleDebug` OK
- Commit: `beb9d85`