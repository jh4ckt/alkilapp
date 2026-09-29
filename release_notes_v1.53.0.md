# AlkilApp Android v1.53 - Agendar visita de visita

## En el chat ya no se propone un monto: se agenda una visita

El boton de la parte inferior del chat paso de "proponer un monto al dueno" a
**"Agendar visita"**:

1. El interesado abre el dialogo y elige **fecha** (date picker) y **hora**
   (time picker 24 h), con un mensaje opcional para el propietario.
2. La solicitud llega como una **tarjeta de cita** en el chat con la fecha y la
   hora, en estado *Pendiente de confirmacion*.
3. El **dueno** del inmueble responde con **Aceptar** o **Rechazar**.
4. El chat **sigue abierto** en ambos casos para seguir coordinando (direccion,
   referencia, etc.). Si se rechaza, se puede volver a agendar otra visita.

En el panel/listado del chat la cita queda resumida como
`Visita: 15/09/2026 10:30` o `Visita confirmada: ...` / `Visita rechazada: ...`.

## "Eliminar chat" ya no borra nada (auditoria)

Se pedia que **nada se borrara de la base** para poder auditar los chats. Ahora:

- `Eliminar chat` **solo oculta el chat en tu lista** (campo `deletedForUsers`).
- El historial **se conserva completo** y sigue siendo legible por el
  servicio (soporte/moderacion).
- Las **reglas de Firestore prohíben el borrado** desde el cliente: nadie puede
  eliminar un chat ni un mensaje desde la app, ni borrar el `deletedForUsers` de
  otro usuario.

## Reglas de seguridad de las citas (Firestore v13)

- Solo el **interesado** puede agendar una cita (nunca el dueno).
- La cita nace siempre `pendiente`: no se puede crear ya "aceptada".
- Solo el **dueno** (o el otro participante si el chat no tiene inmueble)
  acepta/rechaza, y **nunca el solicitante** ni un tercero.
- Una cita ya respondida **no se puede volver a cambiar**.
- No se puede tocar la fecha, la hora ni el texto de una cita.
- No se puede agendar en un chat cerrado ni en un inmueble ya alquilado.

Las reglas se validan contra el emulador de Firestore con datos sembrados:
**71/71 casos OK** (se añadieron 29 casos para citas y soft delete).

## Otros

- Correccion de compilacion del dialogo de cita: una sola vista inflada y los
  listeners de los selectores registrados antes de mostrarlos.
- Textos de la cita en `strings.xml` (se eliminaron textos sueltos en el codigo).

## Instalacion

`app-release.apk` (versionCode 35 / versionName 1.53.0). Requiere Android 8.0+.

> La clave de firma de subida sigue pendiente de que Play Console acepte la
> clave actual (`F9:53:6F:...`); el APK de este release es firmada con ella y
> solo se puede actualizar en el dispositivo si coincide con la instalada.
