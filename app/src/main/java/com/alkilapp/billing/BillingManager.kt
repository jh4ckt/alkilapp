package com.alkilapp.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.*

/**
 * Gestor de Google Play Billing para compras de "Destacar publicacion".
 * NOTA: Requiere configurar productos en Play Console (SKUs: destacar_7d, destacar_15d, destacar_30d)
 * y tener la app publicada en track interno/cerrado/abierto para probar compras reales.
 * Wrapper sobre BillingManagerJava para evitar problemas de SAM conversion en Billing 8.0.0.
 */
class BillingManager(private val activity: Activity) {

    private val billingManager = BillingManagerJava(activity)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var billingReady = false
    private var queryPurchasesCallback: ((List<Purchase>) -> Unit)? = null

    interface PurchaseCallback {
        fun onSuccess(productId: String, purchaseToken: String, orderId: String?)
        fun onError(message: String)
    }

    fun initialize(onReady: () -> Unit) {
        billingManager.initialize {
            billingReady = true
            Log.d("AlkilAppBilling", "BillingClient listo")
            queryPurchases()
            onReady()
        }
    }

    /** Lanza el flujo de compra para un SKU dado. */
    fun launchPurchaseFlow(productId: String, callback: PurchaseCallback) {
        if (!billingReady) {
            callback.onError("Billing no listo")
            return
        }
        billingManager.launchPurchaseFlow(productId, object : BillingManagerJava.PurchaseCallback {
            override fun onSuccess(productId: String, purchaseToken: String, orderId: String?) {
                callback.onSuccess(productId, purchaseToken, orderId)
            }
            override fun onError(message: String) {
                callback.onError(message)
            }
        })
    }

    /** Consulta compras activas para validar si ya tiene destacado vigente. */
    fun queryPurchases(callback: ((List<Purchase>) -> Unit)? = null) {
        queryPurchasesCallback = callback
        billingManager.queryPurchases { purchases ->
            callback?.invoke(purchases)
        }
    }

    /** Product IDs en Play Console (ajustar a los reales). */
    companion object {
        const val SKU_DESTACAR_7D = "destacar_7d"
        const val SKU_DESTACAR_15D = "destacar_15d"
        const val SKU_DESTACAR_30D = "destacar_30d"
    }
}