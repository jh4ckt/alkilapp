## AlkilApp Android v1.44 — Fix feed (Lima mostraba 1 en vez de 4-5) + Gas

**Bug que reportaste**: en el feed de Lima solo te mostraba 1 propiedad pese a tener ~4-5 registradas. 

**Causa raiz**: el adaptador del feed (PropiedadAdapter) caia al "distrito de tu zona" (tu ubicacion actual) como filtro por defecto — si tu distrito no coincide con el de las propiedades, solo aparecia 1. Ahora sin filtro de distrito explicito **muestra TODAS las del departamento efectivo**.

**Cambios**:
1. Fix feed — todas las del departamento cuando no hay filtro de distrito (no solo 1)
2. Selectores de distrito corregidos (usaban ciudades en vez de distritos de Lima): RegistrarPropiedad L201, MiPerfil L162, MainActivity L589 -> ahora usan los ~43 distritos reales
3. **Gas** agregado a comodidades disponibles
4. Etiquetas dinamicas del bottom sheet de filtros: muestran el departamento/distrito elegido, al "Limpiar filtros" vuelven a "Seleccione...". 

**Instalar**: descarga y abre alkilapp_v1.44.apk (permite instalar desde fuentes desconocidas).
