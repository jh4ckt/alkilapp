## AlkilApp v1.39 (vc21)

### Registro nacional (25 departamentos)
- RegistrarPropiedadActivity: nuevo spinner Departamento con 25 departamentos de Peru
- Distrito dinamico: al seleccionar Lima -> 43 distritos; otros -> "Sin distrito" + "Otro"
- MainActivity (filtros): spinner Departamento con listener que actualiza distritos en vivo
- Strings actualizado: "Ahora se filtra a nivel nacional (Peru). Selecciona departamento y distrito."

### Boton compartir en ficha de propiedad
- Nuevo boton en cabecera de PropiedadDetalleActivity (icono compartir nativo)
- Genera link: https://alkilapp.com/propiedad/{propId}
- Abre share sheet del sistema (WhatsApp, email, etc.)

### Fix: Permission denied al abrir chat
- Array participants ordenado consistentemente (.sorted()) en PropiedadDetalleActivity y PerfilPropietarioActivity
- Evita PERMISSION_DENIED en regla Firestore cuando chat ya existe

### Tecnico
- Version bump: versionCode 21, versionName 1.39
- Build: gradlew assembleDebug
- APK instalado en Xiaomi (serial HGR4RJCK)