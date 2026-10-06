# Contrato de eventos de analítica (para la app Android)

El panel de admin ya tiene la infraestructura para medir el embudo
**publicación → vista → contacto → alquiler**, pero **la app todavía no envía
eventos**, así que el embudo sale vacío a propósito. Esto es lo que hay que
escribir en el lado Kotlin.

## Dónde se escriben

Colección `eventos`, un documento por evento. Nada más: no hay que tocar nada
más de la base.

```
eventos/{idAutomatico}
```

Las reglas de Firestore ya están desplegadas y son **estrictas**. Si un campo no
cumple lo de abajo, el write falla con `PERMISSION_DENIED` en silencio. Esto no
es negociable: el panel no debe poder leer el comportamiento de cada usuario, y
un evento tiene que ser inmutable.

## Campos (y solo estos)

| Campo | Tipo | Obligatorio | Nota |
|---|---|---|---|
| `tipo` | string | sí | Lista cerrada, abajo |
| `authorId` | string | sí | Debe ser el `uid` del usuario autenticado |
| `sessionId` | string | sí | 1–64 chars. Un id por sesión de la app |
| `ts` | `serverTimestamp()` | sí | **Usar `FieldValue.serverTimestamp()`**, nunca la hora local |
| `listingId` | string | si aplica | Solo si el evento va sobre un inmueble |
| `meta` | map | no | Sin datos personales (ver abajo) |

Cualquier campo extra hará que el write sea rechazado: las reglas usan `hasOnly`.

## Tipos de evento permitidos

```
listing_created        publica un inmueble nuevo o lo edita
listing_viewed         abre la ficha de un inmueble
listing_favorited      marca o desmarca favorito
listing_shared         comparte el anuncio
listing_finalized      el dueño marca "ya lo alquilé"
search_performed       ejecuta una búsqueda
filter_applied         aplica un filtro (departamento, tipo, precio…)
map_marker_tapped      toca un pin del mapa
chat_opened            abre un chat
chat_message_sent      envía un mensaje
verification_submitted  envía la verificación de identidad
support_opened         abre un ticket de soporte
```

Si hace falta un tipo nuevo, hay que añadirlo a la lista en
`backend/firestore.rules` (función `tipoValido()`) y volver a desplegar. No se
puede inventar un tipo en el cliente: el embudo contaría fases que no existen.

## `meta`: qué se puede y qué no

Se puede: números y textos cortos que expliquen el evento.

```kotlin
// OK
meta = mapOf("resultados" to 12, "departamento" to "Lima", "operacion" to "alquiler")
meta = mapOf("lat" to -12.05, "lng" to -77.04, "distanciaKm" to 2.4)
```

**Prohibido** por las reglas (el write falla): `email`, `telefono`, `password`,
`token`, `nombre`, `foto`. La analítica no necesita datos personales y no debe
poder guardarlos.

## Cómo escribirlo

```kotlin
package com.alkilapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object Eventos {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Un id por sesión de la app. Se genera una vez al abrir y se reutiliza.
    private val sessionId: String = java.util.UUID.randomUUID().toString()

    /**
     * Registra un evento de analítica.
     *
     * NUNCA debe romper la acción del usuario: si el write falla (sin red, sin
     * permiso, regla incumplida) se ignora en silencio. Instrumentar la app no
     * puede hacer que un anuncio no se publique o un mensaje no se envíe.
     */
    fun registrar(tipo: String, listingId: String? = null, meta: Map<String, Any>? = null) {
        val uid = auth.currentUser?.uid ?: return      // anónimo: no se registra
        val datos = mutableMapOf<String, Any>(
            "tipo" to tipo,
            "authorId" to uid,
            "sessionId" to sessionId,
            "ts" to FieldValue.serverTimestamp(),
        )
        if (!listingId.isNullOrBlank()) datos["listingId"] = listingId
        if (!meta.isNullOrEmpty()) datos["meta"] = meta

        db.collection("eventos").add(datos)
            .addOnFailureListener { /* se ignora a proposito */ }
    }

    // Atajos de los eventos mas usados
    fun fichaVista(listingId: String) = registrar("listing_viewed", listingId)
    fun chatAbierto(listingId: String, chatId: String) =
        registrar("chat_opened", listingId, mapOf("chatId" to chatId))
    fun busqueda(resultados: Int, filtros: Map<String, Any>) =
        registrar("search_performed", meta = filtros + ("resultados" to resultados))
}
```

## Dónde llamarlo

| Evento | Dónde |
|---|---|
| `listing_created` | `RegistrarPropiedadActivity`, tras guardar con éxito |
| `listing_finalized` | donde se pone `estado = "finalizado"` |
| `listing_viewed` | `PropiedadDetalleActivity.onCreate` (una vez, no en cada `onResume`) |
| `listing_favorited` | al alternar favorito |
| `listing_shared` | al compartir |
| `search_performed` | tras aplicar filtros y calcular resultados |
| `filter_applied` | al cambiar un filtro, con el nombre y valor en `meta` |
| `map_marker_tapped` | al tocar un pin |
| `chat_opened` | `ChatDetailActivity.onCreate` |
| `chat_message_sent` | al enviar mensaje |
| `verification_submitted` | `VerificacionActivity`, tras subir las 2 fotos |
| `support_opened` | al abrir el formulario de ticket |

## Dos cosas que importan

**No dupliques `listing_viewed` en cada `onResume`.** Si se dispara al volver al
foreground, una persona que abre y cierra la ficha 20 veces genera 20 vistas y el
embudo miente. Va una vez por entrada a la ficha.

**No mandes datos personales en `meta`.** Además de lo técnico (id de chat,
número de resultados, coordenadas), nada. Las reglas lo bloquean, y está bien
que lo bloqueen.

## Verificar que funciona

Después de mandar eventos, el panel debería dejar de decir "la app todavía no
envía eventos". Se puede comprobar en caliente:

```
https://alkilapp-admin-288429280561.us-central1.run.app
```

Y para confirmar que los eventos están llegando bien formados:

```powershell
# desde backend/, con las credenciales del service account
node -e "const {Firestore}=require('@google-cloud/firestore');const db=new Firestore({projectId:'gen-lang-client-0040505884',databaseId:'alkilappdb',keyFilename:'credentials/alkilapp-seed-sa.json'});db.collection('eventos').limit(10).get().then(s=>s.docs.forEach(d=>console.log(JSON.stringify({...d.data(),ts:d.data().ts&&d.data().ts.toDate&&d.data().ts.toDate().toISOString()})))).catch(e=>console.error(e.message))"
```
