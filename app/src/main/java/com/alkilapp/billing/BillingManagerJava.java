package com.alkilapp.billing;

import android.app.Activity;
import android.util.Log;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BillingManagerJava {
    private static final String TAG = "AlkilAppBilling";

    private final BillingClient billingClient;
    private final Activity activity;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private boolean billingReady = false;
    private PurchaseCallback purchaseCallback;

    public interface PurchaseCallback {
        void onSuccess(String productId, String purchaseToken, String orderId);
        void onError(String message);
    }

    public BillingManagerJava(Activity activity) {
        this.activity = activity;

        // Se habilita explícitamente el soporte de compras pendientes para productos únicos (INAPP)
        PendingPurchasesParams pendingPurchasesParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();

        billingClient = BillingClient.newBuilder(activity)
                .enablePendingPurchases(pendingPurchasesParams)
                .setListener((billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                        for (Purchase purchase : purchases) {
                            handlePurchase(purchase);
                        }
                    }
                })
                .build();
    }

    public void initialize(Runnable onReady) {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    billingReady = true;
                    Log.d(TAG, "BillingClient listo");
                    queryPurchases(null);
                    if (onReady != null) {
                        onReady.run();
                    }
                } else {
                    Log.e(TAG, "Error iniciando Billing: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                billingReady = false;
                Log.w(TAG, "BillingClient desconectado");
            }
        });
    }

    public void launchPurchaseFlow(String productId, PurchaseCallback callback) {
        this.purchaseCallback = callback;
        if (!billingReady) {
            callback.onError("Billing no listo");
            return;
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(List.of(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                ))
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, queryResult) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK 
                    || queryResult == null) {
                callback.onError("Producto no encontrado: " + productId);
                return;
            }

            List<ProductDetails> productDetailsList = queryResult.getProductDetailsList();
            if (productDetailsList == null || productDetailsList.isEmpty()) {
                callback.onError("Producto no encontrado: " + productId);
                return;
            }

            ProductDetails productDetails = productDetailsList.get(0);
            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(List.of(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .build()
                    ))
                    .build();

            BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                callback.onError("Error lanzando compra: " + result.getDebugMessage());
            }
        });
    }

    public void queryPurchases(java.util.function.Consumer<List<Purchase>> callback) {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                if (callback != null) {
                    callback.accept(purchases != null ? purchases : new ArrayList<>());
                }
            }
        });
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged()) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

            billingClient.acknowledgePurchase(params, billingResult -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Compra reconocida: " + purchase.getProducts());
                }
            });
        }
        if (purchaseCallback != null) {
            List<String> products = purchase.getProducts();
            String productId = products != null && !products.isEmpty() ? products.get(0) : "";
            purchaseCallback.onSuccess(
                    productId,
                    purchase.getPurchaseToken(),
                    purchase.getOrderId()
            );
        }
    }

    public static final String SKU_DESTACAR_7D = "destacar_7d";
    public static final String SKU_DESTACAR_15D = "destacar_15d";
    public static final String SKU_DESTACAR_30D = "destacar_30d";
}