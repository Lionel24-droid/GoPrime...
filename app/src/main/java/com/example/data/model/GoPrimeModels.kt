package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val labelFr: String) {
    CLIENT("Client"),
    DRIVER("Livreur"),
    ADMIN("Administrateur");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: CLIENT
    }
}

enum class FulfillmentMode(val labelFr: String) {
    DELIVERY("Livraison à domicile"),
    PICKUP("Retrait en point de retrait");

    companion object {
        fun fromRaw(raw: String?): FulfillmentMode =
            entries.firstOrNull { it.name == raw } ?: DELIVERY
    }
}

enum class PaymentMethod(val labelFr: String) {
    CASH("Espèces"),
    MOBILE_MONEY("Mobile Money");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

enum class PaymentStatus(val labelFr: String) {
    PENDING("À encaisser"),
    PENDING_VERIFICATION("Vérification Mobile Money"),
    PAID("Payé (vérifié)"),
    FAILED("Échec paiement");

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class OrderStatus(val labelFr: String) {
    PENDING("En attente"),
    ACCEPTED("Acceptée"),
    PREPARING("En préparation"),
    READY_FOR_PICKUP("Prête au retrait"),
    OUT_FOR_DELIVERY("En livraison"),
    COMPLETED("Terminée"),
    REJECTED("Refusée"),
    CANCELLED("Annulée");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == REJECTED || this == CANCELLED

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class DeliveryStatusCategory(val labelFr: String) {
    IN_PROGRESS("En cours"),
    DELIVERED("Livré"),
    CANCELLED("Annulé");

    companion object {
        fun fromOrderStatus(status: OrderStatus): DeliveryStatusCategory = when (status) {
            OrderStatus.PENDING,
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.OUT_FOR_DELIVERY -> IN_PROGRESS
            OrderStatus.COMPLETED -> DELIVERED
            OrderStatus.CANCELLED,
            OrderStatus.REJECTED -> CANCELLED
        }
    }
}

enum class WhatsappDispatchStatus(val labelFr: String) {
    NOT_CONFIGURED("Non configuré"),
    PENDING("En attente serveur"),
    SENT("Envoyé"),
    FAILED("Échec");

    companion object {
        fun fromRaw(raw: String?): WhatsappDispatchStatus =
            entries.firstOrNull { it.name == raw } ?: NOT_CONFIGURED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val defaultDistrictId: String = "",
    val defaultAddress: String = "",
    val whatsappPhone: String = "",
    val role: String = UserRole.CLIENT.name,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: UserRole get() = UserRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val sortOrder: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val priceCdf: Long = 0L,
    val stock: Long = 0L,
    val imageUrl: String = "",
    val storagePath: String = "",
    val featured: Boolean = false,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeliveryZone(
    @DocumentId val id: String = "",
    val name: String = "",
    val commune: String = "",
    val feeCdf: Long = 0L,
    val estimatedMinutes: Long = 30L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CartItem(
    @DocumentId val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val unitPriceCdf: Long = 0L,
    val quantity: Long = 1L,
    val imageUrl: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val lineTotalCdf: Long get() = unitPriceCdf * quantity
}

@Keep
@IgnoreExtraProperties
data class OrderItemLine(
    val productId: String = "",
    val productName: String = "",
    val unitPriceCdf: Long = 0L,
    val quantity: Long = 1L
)

@Keep
@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val fulfillmentMode: String = FulfillmentMode.DELIVERY.name,
    val districtId: String = "",
    val districtName: String = "",
    val deliveryAddress: String = "",
    val items: List<Map<String, Any>> = emptyList(),
    val subtotalCdf: Long = 0L,
    val deliveryFeeCdf: Long = 0L,
    val totalCdf: Long = 0L,
    val paymentMethod: String = PaymentMethod.CASH.name,
    val paymentProvider: String = "",
    val paymentPhone: String = "",
    val paymentReference: String = "",
    val paymentStatus: String = PaymentStatus.PENDING.name,
    val status: String = OrderStatus.PENDING.name,
    val assignedDriverId: String = "",
    val assignedDriverName: String = "",
    val whatsappNotificationStatus: String = WhatsappDispatchStatus.PENDING.name,
    val customerWhatsappStatus: String = WhatsappDispatchStatus.PENDING.name,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedFulfillment: FulfillmentMode get() = FulfillmentMode.fromRaw(fulfillmentMode)
    val parsedPaymentMethod: PaymentMethod get() = PaymentMethod.fromRaw(paymentMethod)
    val parsedPaymentStatus: PaymentStatus get() = PaymentStatus.fromRaw(paymentStatus)
    val parsedStatus: OrderStatus get() = OrderStatus.fromRaw(status)
    val deliveryStatusCategory: DeliveryStatusCategory
        get() = DeliveryStatusCategory.fromOrderStatus(parsedStatus)
    val parsedWhatsappStatus: WhatsappDispatchStatus get() = WhatsappDispatchStatus.fromRaw(whatsappNotificationStatus)
    val parsedCustomerWhatsappStatus: WhatsappDispatchStatus get() = WhatsappDispatchStatus.fromRaw(customerWhatsappStatus)

    fun parsedItems(): List<OrderItemLine> = items.map { raw ->
        OrderItemLine(
            productId = raw["productId"] as? String ?: "",
            productName = raw["productName"] as? String ?: "",
            unitPriceCdf = (raw["unitPriceCdf"] as? Number)?.toLong() ?: 0L,
            quantity = (raw["quantity"] as? Number)?.toLong() ?: 1L
        )
    }
}

const val DEFAULT_ADMIN_WHATSAPP_PHONE = "+243974685125"

@Keep
@IgnoreExtraProperties
data class WhatsappIntegrationSettings(
    @DocumentId val id: String = "whatsapp",
    val enabled: Boolean = true,
    val adminWhatsappPhones: List<String> = listOf(DEFAULT_ADMIN_WHATSAPP_PHONE),
    val notifyCustomerOnOrder: Boolean = true,
    val templateName: String = "goprime_new_order",
    val customerTemplateName: String = "goprime_order_confirmation",
    val languageCode: String = "fr",
    val cloudFunctionEndpoint: String = "",
    val mobileMoneyProviderMode: String = "Verification webhook requise",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationLog(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val channel: String = "",
    val recipientRole: String = "ADMIN",
    val recipient: String = "",
    val status: String = "",
    val detail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderReview(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val userId: String = "",
    val customerName: String = "",
    val productIds: List<String> = emptyList(),
    val productRating: Int = 5,
    val deliveryRating: Int = 5,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

