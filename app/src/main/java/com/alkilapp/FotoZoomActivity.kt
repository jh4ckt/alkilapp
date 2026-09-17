package com.alkilapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.alkilapp.databinding.ActivityFotoZoomBinding
import com.alkilapp.ui.ZoomImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Visor de fotos a pantalla completa con zoom (pellizco / doble toque / arrastre). */
class FotoZoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFotoZoomBinding
    private val cargadorImagenes = Executors.newSingleThreadExecutor()
    private val handlerUi = Handler(Looper.getMainLooper())

    private var fuentes: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFotoZoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        fuentes = intent.getStringArrayListExtra(EXTRA_FUENTES) ?: emptyList()
        if (fuentes.isEmpty()) {
            finish()
            return
        }

        binding.vpFotos.adapter = AdaptadorFotos()
        binding.vpFotos.offscreenPageLimit = 1
        binding.vpFotos.setCurrentItem(
            intent.getIntExtra(EXTRA_POSICION, 0).coerceIn(0, fuentes.size - 1),
            false
        )
        actualizarIndicador(binding.vpFotos.currentItem)

        binding.vpFotos.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tvZoomIndicador.text = getString(R.string.foto_zoom_indicador, position + 1, fuentes.size)
            }
        })

        binding.btnZoomCerrar.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        cargadorImagenes.shutdownNow()
        super.onDestroy()
    }

    private fun actualizarIndicador(posicion: Int) {
        binding.tvZoomIndicador.text = getString(R.string.foto_zoom_indicador, posicion + 1, fuentes.size)
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
            if (fuente.startsWith(PREFIJO_B64)) {
                val bmp = decodificarBase64Completa(fuente.removePrefix(PREFIJO_B64))
                if (bmp != null) holder.vista.setImageBitmap(bmp)
            } else {
                cargadorImagenes.execute {
                    val bmp = descargarBitmap(fuente)
                    handlerUi.post {
                        if (bmp != null) holder.vista.setImageBitmap(bmp)
                    }
                }
            }
        }

        inner class Holder(val vista: ZoomImageView) : RecyclerView.ViewHolder(vista)
    }

    /** Decodifica la foto en resolución completa (sin reducir) para hacer zoom real. */
    private fun decodificarBase64Completa(b64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
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
        const val EXTRA_FUENTES = "zoom_fuentes"
        const val EXTRA_POSICION = "zoom_posicion"
        private const val PREFIJO_B64 = "b64:"
    }
}