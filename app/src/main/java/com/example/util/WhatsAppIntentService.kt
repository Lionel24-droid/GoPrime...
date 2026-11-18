package com.example.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.data.model.CartItem
import com.example.data.model.DEFAULT_ADMIN_WHATSAPP_PHONE
import com.example.data.model.FulfillmentMode
import com.example.data.model.Order
import com.example.data.model.PaymentMethod
import com.example.ui.components.formatCdf
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Service utilitaire Android pour ouvrir WhatsApp avec un message pré-rempli
 * contenant les détails complets d'une commande GoPrime via Android Intent.
 */
object WhatsAppIntentService {

    private const val WHATSAPP_STANDARD_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun normalizePhoneForWhatsApp(
        rawPhone: String,
        fallbackPhone: String = DEFAULT_ADMIN_WHATSAPP_PHONE
    ): String {
        val cleaned = normalizeDigits(rawPhone)
        if (cleaned.length >= 8) return cleaned
        return normalizeDigits(fallbackPhone)
    }

    private fun normalizeDigits(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return if (digits.startsWith("0") && digits.length in 9..10) {
            "243${digits.drop(1)}"
        } else {
            digits
        }
    }

    fun buildWhatsAppUrl(
        targetPhone: String = DEFAULT_ADMIN_WHATSAPP_PHONE,
        message: String
    ): String {
        val normalizedPhone = normalizePhoneForWhatsApp(targetPhone)
        val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "https://wa.me/$normalizedPhone?text=$encodedMessage"
    }

    fun buildOrderDetailsMessage(order: Order): String {
        val lines = order.parsedItems()
        return buildString {
            appendLine("🛍️ *Commande GoPrime ${order.orderNumber.ifBlank { "#${order.id.take(6)}" }}*")
            appendLine("Statut : ${order.parsedStatus.labelFr}")
            if (order.customerName.isNotBlank() || order.customerPhone.isNotBlank()) {
                appendLine("Client : ${order.customerName.ifBlank { "Client" }} (${order.customerPhone})")
            }
            appendLine("Mode : ${order.parsedFulfillment.labelFr} — ${order.districtName}")
            if (order.deliveryAddress.isNotBlank()) {
                appendLine("Adresse : ${order.deliveryAddress}")
            }
            appendLine()
            appendLine("*Articles :*")
            if (lines.isEmpty()) {
                appendLine("- Articles GoPrime")
            } else {
                lines.forEach { line ->
                    appendLine("- ${line.quantity}× ${line.productName} : ${formatCdf(line.unitPriceCdf * line.quantity)}")
                }
            }
            appendLine()
            appendLine("Sous-total : ${formatCdf(order.subtotalCdf)}")
            appendLine("Frais livraison : ${formatCdf(order.deliveryFeeCdf)}")
            appendLine("*Total : ${formatCdf(order.totalCdf)}*")
            val paymentInfo = buildString {
                append(order.parsedPaymentMethod.labelFr)
                if (order.parsedPaymentMethod == PaymentMethod.MOBILE_MONEY && order.paymentProvider.isNotBlank()) {
                    append(" (${order.paymentProvider})")
                }
                append(" • ${order.parsedPaymentStatus.labelFr}")
            }
            appendLine("Paiement : $paymentInfo")
            if (order.paymentReference.isNotBlank()) {
                appendLine("Réf paiement : ${order.paymentReference}")
            }
            if (order.notes.isNotBlank()) {
                appendLine("Note : ${order.notes}")
            }
        }.trim()
    }

    fun buildCheckoutDetailsMessage(
        customerName: String,
        customerPhone: String,
        fulfillmentMode: FulfillmentMode,
        districtName: String,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        paymentProvider: String,
        paymentPhone: String,
        paymentReference: String,
        notes: String,
        cartItems: List<CartItem>,
        subtotalCdf: Long,
        deliveryFeeCdf: Long,
        totalCdf: Long,
        orderNumber: String? = null
    ): String = buildString {
        if (!orderNumber.isNullOrBlank()) {
            appendLine("🛍️ *Commande GoPrime $orderNumber*")
        } else {
            appendLine("🛍️ *Nouvelle commande GoPrime*")
        }
        appendLine("Client : ${customerName.ifBlank { "Client GoPrime" }} (${customerPhone.ifBlank { "Non renseigné" }})")
        appendLine("Mode : ${fulfillmentMode.labelFr}${if (districtName.isNotBlank()) " — $districtName" else ""}")
        if (fulfillmentMode == FulfillmentMode.DELIVERY && deliveryAddress.isNotBlank()) {
            appendLine("Adresse : $deliveryAddress")
        }
        appendLine()
        appendLine("*Articles :*")
        cartItems.forEach { item ->
            appendLine("- ${item.quantity}× ${item.productName} : ${formatCdf(item.lineTotalCdf)}")
        }
        appendLine()
        appendLine("Sous-total : ${formatCdf(subtotalCdf)}")
        appendLine("Frais livraison : ${formatCdf(deliveryFeeCdf)}")
        appendLine("*Total à payer : ${formatCdf(totalCdf)}*")
        val paymentDetail = if (paymentMethod == PaymentMethod.MOBILE_MONEY) {
            "Mobile Money (${paymentProvider.ifBlank { "Opérateur" }} • ${paymentPhone.ifBlank { "-" }})"
        } else {
            "Espèces"
        }
        appendLine("Paiement : $paymentDetail")
        if (paymentReference.isNotBlank()) {
            appendLine("Réf : $paymentReference")
        }
        if (notes.isNotBlank()) {
            appendLine("Note : $notes")
        }
    }.trim()

    fun openWhatsAppWithMessage(
        context: Context,
        message: String,
        targetPhone: String = DEFAULT_ADMIN_WHATSAPP_PHONE
    ): Result<Unit> {
        val uri = Uri.parse(buildWhatsAppUrl(targetPhone = targetPhone, message = message))
        val installedPackage = findInstalledWhatsAppPackage(context)

        fun buildIntent(packageName: String?): Intent =
            Intent(Intent.ACTION_VIEW, uri).apply {
                if (packageName != null) {
                    setPackage(packageName)
                }
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

        if (installedPackage != null) {
            try {
                context.startActivity(buildIntent(installedPackage))
                return Result.success(Unit)
            } catch (_: ActivityNotFoundException) {
                // Fallback to default browser / system handler below
            }
        }

        return try {
            context.startActivity(buildIntent(null))
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(
                IllegalStateException(
                    "WhatsApp ou aucun navigateur compatible n'est installé sur cet appareil.",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Impossible d'ouvrir WhatsApp.",
                    e
                )
            )
        }
    }

    fun sendOrderDetailsToAppWhatsApp(
        context: Context,
        order: Order,
        appWhatsappPhone: String = DEFAULT_ADMIN_WHATSAPP_PHONE
    ): Result<Unit> = openWhatsAppWithMessage(
        context = context,
        message = buildOrderDetailsMessage(order),
        targetPhone = appWhatsappPhone
    )

    fun sendOrderDetailsToCustomerWhatsApp(
        context: Context,
        order: Order
    ): Result<Unit> = openWhatsAppWithMessage(
        context = context,
        message = buildOrderDetailsMessage(order),
        targetPhone = order.customerPhone.ifBlank { DEFAULT_ADMIN_WHATSAPP_PHONE }
    )

    fun sendCheckoutSummaryToAppWhatsApp(
        context: Context,
        customerName: String,
        customerPhone: String,
        fulfillmentMode: FulfillmentMode,
        districtName: String,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        paymentProvider: String,
        paymentPhone: String,
        paymentReference: String,
        notes: String,
        cartItems: List<CartItem>,
        subtotalCdf: Long,
        deliveryFeeCdf: Long,
        totalCdf: Long,
        orderNumber: String? = null,
        appWhatsappPhone: String = DEFAULT_ADMIN_WHATSAPP_PHONE
    ): Result<Unit> = openWhatsAppWithMessage(
        context = context,
        message = buildCheckoutDetailsMessage(
            customerName = customerName,
            customerPhone = customerPhone,
            fulfillmentMode = fulfillmentMode,
            districtName = districtName,
            deliveryAddress = deliveryAddress,
            paymentMethod = paymentMethod,
            paymentProvider = paymentProvider,
            paymentPhone = paymentPhone,
            paymentReference = paymentReference,
            notes = notes,
            cartItems = cartItems,
            subtotalCdf = subtotalCdf,
            deliveryFeeCdf = deliveryFeeCdf,
            totalCdf = totalCdf,
            orderNumber = orderNumber
        ),
        targetPhone = appWhatsappPhone
    )

    private fun findInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        val candidates = listOf(WHATSAPP_STANDARD_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)
        return candidates.firstOrNull { pkg -> isPackageInstalled(pm, pkg) }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
