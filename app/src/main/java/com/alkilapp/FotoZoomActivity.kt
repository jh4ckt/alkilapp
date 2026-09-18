package com.alkilapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.alkilapp.databinding.ActivityFotoZoomBinding
import com.alkilapp.ui.ZoomImageView
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max

/** Visor de fotos a pantalla completa con zoom (pellizco / doble toque / arrastre). */
class FotoZoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFotoZoomBinding
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private val cargadorImagenes = Executors.newSingleThreadExecutor()
    private val handlerUi = Handler(Looper.getMainLooper())

    private var fuentes: List<String> = emptyList()
    private var registroPagina: OnPageChangeCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFotoZoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        binding.btnZoomCerrar.setOnClickListener { finish() }

        cargarFuentes()
    }

    /**
     * Las fotos se cargan desde Firestore por ID: enviar los base64 por el
     * intent superaba el limite de Binder (1 MB) y cerraba la app.
     */
    private fun cargarFuentes() {
        val propId = intent.getStringExtra(EXTRA_PROPIEDAD_ID).orEmpty()
        if (propId.isBlank()) {
            finish()
            return
        }
        binding.pbZoom.visibility = View.VISIBLE
        db.collection("propiedades").document(propId).get()
            .addOnSuccessListener { doc ->
                val base64 = (doc.get("fotos") as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()
                val urls = (doc.get("photos") as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()
                fuentes = base64.filter { it.isNotBlank() }.map { "$PREFIJO_B64$it" } +
                    urls.filter { it.isNotBlank() }
                mostrar()
            }
            .addOnFailureListener { finish() }
    }

    private fun mostrar() {
        binding.pbZoom.visibility = View.GONE
        if (fuentes.isEmpty()) {
            finish()
            return
        }
        val posicion = intent.getIntExtra(EXTRA_POSICION, 0).coerceIn(0, fuentes.size - 1)
        binding.vpFotos.adapter = AdaptadorFotos()
        binding.vpFotos.offscreenPageLimit = 1
        binding.vpFotos.setCurrentItem(posicion, false)
        actualizarIndicador(posicion)

        val callback = object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                actualizarIndicador(position)
            }
        }
        registroPagina = callback
        binding.vpFotos.registerOnPageChangeCallback(callback)
    }

    override fun onDestroy() {
        registroPagina?.let { binding.vpFotos.unregisterOnPageChangeCallback(it) }
        cargadorImagenes.shutdownNow()
        super.onDestroy()
    }

    private fun actualizarIndicador(posicion: Int) {
        binding.tvZoomIndicador.text =
            getString(R.string.foto_zoom_indicador, posicion + 1, fuentes.size)
    }

    private inner class AdaptadorFotos : RecyclerView.Adapter<AdaptadorFotos.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val vista = ZoomImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return Holder(vista)
        }

        override fun getItemCount(): Int = fuentes.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val fuente = fuentes[position]
            holder.vista.setImageBitmap(null)
            holder.vista.tag = position
            val esBase64 = fuente.startsWith(PREFIJO_B64)
            cargadorImagenes.execute {
                val bmp = if (esBase64) {
                    decodificarBase64(fuente.removePrefix(PREFIJO_B64))
                } else {
                    descargarBitmap(fuente)
                }
                handlerUi.post {
                    if (bmp != null && holder.vista.tag == position) {
                        holder.vista.setImageBitmap(bmp)
                    }
                }
            }
        }

        inner class Holder(val vista: ZoomImageView) : RecyclerView.ViewHolder(vista)
    }

    /**
     * Decodifica en resolucion alta pero acotada (~2048px de lado mayor) para
     * permitir zoom real sin arriesgar OutOfMemory con fotos muy grandes.
     */
    private fun decodificarBase64(b64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = factorMuestra(bounds.outWidth, bounds.outHeight, LADO_MAX)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun factorMuestra(ancho: Int, alto: Int, objetivo: Int): Int {
        var muestra = 1
        var mayor = max(ancho, alto)
        while (mayor / 2 >= objetivo) {
            mayor /= 2
            muestra *= 2
        }
        return muestra
    }

    private fun descargarBitmap(urlString: String): Bitmap? {
        return try {
            val conexion = URL(urlString).openConnection() as HttpURLConnection
            conexion.connectTimeout = 10000
            conexion.readTimeout = 10000
            val stream = conexion.inputStream
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            conexion.disconnect()
            bmp
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_PROPIEDAD_ID = "zoom_propiedad_id"
        const val EXTRA_POSICION = "zoom_posicion"
        private const val PREFIJO_B64 = "b64:"
        private const val LADO_MAX = 2048
    }
}
