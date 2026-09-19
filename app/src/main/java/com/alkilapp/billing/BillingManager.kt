package com.alkilapp.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*

/**
 * Gestor de Google Play Billing para compras de "Destacar publicacion".
 * NOTA: Requiere configurar productos en Play Console (SKUs: destacar_7d, destacar_15d, destacar_30d)
 * y tener la app publicada en track interno/cerrado/abierto para probar compras reales.
 */
class BillingManager(private val activity: Activity) {

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .enablePendingPurchases()
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
        .build()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var billingReady = false
    private var queryPurchasesCallback: ((List<Purchase>) -> Unit)? = null

    interface PurchaseCallback {
        fun onSuccess(productId: String, purchaseToken: String, orderId: String?)
        fun onError(message: String)
    }

    fun initialize(onReady: () -> Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingReady = true
                    Log.d("AlkilAppBilling", "BillingClient listo")
                    queryPurchases()
                    onReady()
                } else {
                    Log.e("AlkilAppBilling", "Error iniciando Billing: ${billingResult.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                billingReady = false
                Log.w("AlkilAppBilling", "BillingClient desconectado")
            }
        })
    }

    /** Lanza el flujo de compra para un SKU dado. */
    fun launchPurchaseFlow(productId: String, callback: PurchaseCallback) {
        if (!billingReady) {
            callback.onError("Billing no listo")
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList == null || productDetailsList.isEmpty()) {
                callback.onError("Producto no encontrado: $productId")
                return@queryProductDetailsAsync
            }
            val productDetails = productDetailsList[0]
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                )
                .build()
            val result = billingClient.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                callback.onError("Error lanzando compra: ${result.debugMessage}")
            }
        }
    }

    /** Consulta compras activas para validar si ya tiene destacado vigente. */
    fun queryPurchases(callback: ((List<Purchase>) -> Unit)? = null) {
        queryPurchasesCallback = callback
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                callback?.invoke(purchases ?: emptyList())
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("AlkilAppBilling", "Compra reconocida: ${purchase.products}")
                }
            }
        }
        queryPurchasesCallback?.invoke(listOf(purchase))
    }

    /** Product IDs en Play Console (ajustar a los reales). */
    companion object {
        const val SKU_DESTACAR_7D = "destacar_7d"
        const val SKU_DESTACAR_15D = "destacar_15d"
        const val SKU_DESTACAR_30D = "destacar_30d"
    }
}