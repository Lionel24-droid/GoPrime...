package com.example.data.firestore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.DEFAULT_ADMIN_WHATSAPP_PHONE
import com.example.data.model.DeliveryZone
import com.example.data.model.FulfillmentMode
import com.example.data.model.NotificationLog
import com.example.data.model.Order
import com.example.data.model.OrderReview
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WhatsappDispatchStatus
import com.example.data.model.WhatsappIntegrationSettings
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val BOOTSTRAPPED_ADMIN_EMAIL = "alherilionel338@gmail.com"

data class DeliveryQuote(
    val subtotalCdf: Long,
    val deliveryFeeCdf: Long,
    val totalCdf: Long,
    val districtName: String,
    val estimatedMinutes: Long,
    val isFromCloudFunction: Boolean
)

class GoPrimeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("L'utilisateur doit être connecté avec Google.")

    private fun isBootstrappedAdminUser(user: FirebaseUser?): Boolean =
        user != null && user.isEmailVerified && user.email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)

    private fun <T> Flow<T>.withFirestoreErrorHandling(
        op: OperationType,
        path: String,
        expectedUid: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    suspend fun ensureUserProfile(): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        val uid = currentUser.uid
        val userPath = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()
            val isBootstrapped = isBootstrappedAdminUser(currentUser)

            if (!snap.exists()) {
                val displayName = currentUser.displayName?.takeIf { it.isNotBlank() }
                    ?: currentUser.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Client GoPrime"
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", displayName.take(80))
                    put("email", (currentUser.email ?: "").take(120))
                    put("role", if (isBootstrapped) UserRole.ADMIN.name else UserRole.CLIENT.name)
                    put("active", true)
                    if (isBootstrapped) {
                        put("whatsappPhone", DEFAULT_ADMIN_WHATSAPP_PHONE)
                    }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.set(payload).await()
            }

            if (isBootstrapped) {
                val adminRef = db.collection("admins").document(uid)
                val adminSnap = adminRef.get().await()
                if (!adminSnap.exists()) {
                    adminRef.set(
                        mapOf(
                            "userId" to uid,
                            "email" to (currentUser.email ?: BOOTSTRAPPED_ADMIN_EMAIL),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                if (snap.exists()) {
                    val userUpdates = mutableMapOf<String, Any>()
                    if (snap.getString("role") != UserRole.ADMIN.name) {
                        userUpdates["role"] = UserRole.ADMIN.name
                    }
                    if (snap.getString("whatsappPhone").isNullOrBlank()) {
                        userUpdates["whatsappPhone"] = DEFAULT_ADMIN_WHATSAPP_PHONE
                    }
                    if (userUpdates.isNotEmpty()) {
                        userUpdates["updatedAt"] = FieldValue.serverTimestamp()
                        userRef.update(userUpdates).await()
                    }
                }
                val whatsappRef = db.collection("settings").document("whatsapp")
                val whatsappSnap = whatsappRef.get().await()
                val existingPhones = (whatsappSnap.get("adminWhatsappPhones") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                if (!whatsappSnap.exists()) {
                    whatsappRef.set(
                        mapOf(
                            "enabled" to true,
                            "adminWhatsappPhones" to listOf(DEFAULT_ADMIN_WHATSAPP_PHONE),
                            "notifyCustomerOnOrder" to true,
                            "templateName" to "goprime_new_order",
                            "customerTemplateName" to "goprime_order_confirmation",
                            "languageCode" to "fr",
                            "mobileMoneyProviderMode" to "Vérification Webhook / Cloud Functions requise",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else if (existingPhones.isEmpty()) {
                    whatsappRef.update(
                        mapOf(
                            "enabled" to true,
                            "adminWhatsappPhones" to listOf(DEFAULT_ADMIN_WHATSAPP_PHONE),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .withFirestoreErrorHandling(OperationType.GET, path, uid)
        )
    }

    suspend fun updateUserProfile(
        displayName: String,
        phone: String,
        defaultDistrictId: String,
        defaultAddress: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        val path = "users/$uid"
        return try {
            require(displayName.trim().length in 1..80) { "Nom invalide (1 à 80 caractères)." }
            require(phone.trim().length <= 24) { "Téléphone trop long." }
            require(defaultAddress.trim().length <= 240) { "Adresse trop longue." }
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to displayName.trim(),
                    "phone" to phone.trim(),
                    "defaultDistrictId" to defaultDistrictId.trim(),
                    "defaultAddress" to defaultAddress.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- CATALOG & ZONES (CLIENT ACTIVE QUERIES) ---

    fun observeActiveCategories(uid: String): Flow<List<Category>> = flow {
        emitAll(
            db.collection("categories")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<Category> { it.sortOrder }
                                .thenBy { it.name.lowercase() }
                        )
                }
                .withFirestoreErrorHandling(OperationType.LIST, "categories", uid)
        )
    }

    fun observeActiveProducts(uid: String): Flow<List<Product>> = flow {
        emitAll(
            db.collection("products")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<Product> { it.featured }
                                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .withFirestoreErrorHandling(OperationType.LIST, "products", uid)
        )
    }

    fun observeActiveZones(uid: String): Flow<List<DeliveryZone>> = flow {
        emitAll(
            db.collection("deliveryZones")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DeliveryZone::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "deliveryZones", uid)
        )
    }

    // --- SHOPPING CART ---

    fun observeCart(uid: String): Flow<List<CartItem>> = flow {
        val path = "users/$uid/cart"
        emitAll(
            db.collection("users").document(uid).collection("cart")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CartItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.productName }
                }
                .withFirestoreErrorHandling(OperationType.LIST, path, uid)
        )
    }

    suspend fun upsertCartItem(product: Product, quantity: Long): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        val path = "users/$uid/cart/${product.id}"
        return try {
            require(product.id.isNotBlank()) { "Produit invalide" }
            val docRef = db.collection("users").document(uid).collection("cart").document(product.id)
            if (quantity <= 0L) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val clampedQty = quantity.coerceIn(1L, minOf(99L, maxOf(1L, product.stock)))
            val payload = mapOf(
                "productId" to product.id,
                "productName" to product.name.take(120),
                "unitPriceCdf" to product.priceCdf,
                "quantity" to clampedQty,
                "imageUrl" to product.imageUrl.takeIf { it.isNotBlank() }?.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun removeCartItem(productId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        val path = "users/$uid/cart/$productId"
        return try {
            require(productId.isNotBlank())
            db.collection("users").document(uid).collection("cart").document(productId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- CLIENT ORDERS & CHECKOUT ---

    fun observeCustomerOrders(uid: String): Flow<List<Order>> = flow {
        emitAll(
            db.collection("orders")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "orders", uid)
        )
    }

    suspend fun calculateOrderQuote(
        fulfillmentMode: FulfillmentMode,
        selectedZone: DeliveryZone?,
        cartItems: List<CartItem>
    ): DeliveryQuote {
        val localSubtotal = cartItems.sumOf { it.lineTotalCdf }
        val localFee = if (fulfillmentMode == FulfillmentMode.DELIVERY) selectedZone?.feeCdf ?: 0L else 0L
        val localDistrict = if (fulfillmentMode == FulfillmentMode.PICKUP) {
            "Point de retrait GoPrime"
        } else {
            selectedZone?.name.orEmpty()
        }
        val localMinutes = if (fulfillmentMode == FulfillmentMode.DELIVERY) {
            selectedZone?.estimatedMinutes ?: 30L
        } else {
            0L
        }

        if (auth.currentUser == null || cartItems.isEmpty() ||
            (fulfillmentMode == FulfillmentMode.DELIVERY && selectedZone == null)
        ) {
            return DeliveryQuote(
                subtotalCdf = localSubtotal,
                deliveryFeeCdf = localFee,
                totalCdf = localSubtotal + localFee,
                districtName = localDistrict,
                estimatedMinutes = localMinutes,
                isFromCloudFunction = false
            )
        }

        return try {
            val requestPayload = hashMapOf<String, Any>(
                "fulfillmentMode" to fulfillmentMode.name,
                "items" to cartItems.map { item ->
                    hashMapOf(
                        "productId" to item.productId,
                        "quantity" to item.quantity
                    )
                }
            )
            if (fulfillmentMode == FulfillmentMode.DELIVERY && selectedZone != null) {
                requestPayload["districtId"] = selectedZone.id
            }

            val result = Firebase.functions
                .getHttpsCallable("calculateOrderQuote")
                .call(requestPayload)
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any?>
                ?: throw IllegalStateException("Réponse devis vide")
            val subtotalCdf = (data["subtotalCdf"] as? Number)?.toLong() ?: localSubtotal
            val deliveryFeeCdf = (data["deliveryFeeCdf"] as? Number)?.toLong() ?: localFee
            val totalCdf = (data["totalCdf"] as? Number)?.toLong() ?: (subtotalCdf + deliveryFeeCdf)
            val districtName = (data["districtName"] as? String) ?: localDistrict
            val estimatedMinutes = (data["estimatedMinutes"] as? Number)?.toLong() ?: localMinutes

            DeliveryQuote(
                subtotalCdf = subtotalCdf,
                deliveryFeeCdf = deliveryFeeCdf,
                totalCdf = totalCdf,
                districtName = districtName,
                estimatedMinutes = estimatedMinutes,
                isFromCloudFunction = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DeliveryQuote(
                subtotalCdf = localSubtotal,
                deliveryFeeCdf = localFee,
                totalCdf = localSubtotal + localFee,
                districtName = localDistrict,
                estimatedMinutes = localMinutes,
                isFromCloudFunction = false
            )
        }
    }

    suspend fun placeOrder(
        customerName: String,
        customerPhone: String,
        fulfillmentMode: FulfillmentMode,
        selectedZone: DeliveryZone?,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        paymentProvider: String,
        paymentPhone: String,
        paymentReference: String,
        notes: String,
        cartItems: List<CartItem>
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        val orderRef = db.collection("orders").document()
        return try {
            require(cartItems.isNotEmpty() && cartItems.size <= 50) { "Panier vide ou trop volumineux." }
            require(customerName.trim().length in 1..80) { "Nom du client requis." }
            require(customerPhone.trim().length in 4..24) { "Numéro de téléphone requis." }
            if (fulfillmentMode == FulfillmentMode.DELIVERY) {
                require(selectedZone != null && selectedZone.id.isNotBlank()) { "Veuillez sélectionner un quartier de livraison." }
                require(deliveryAddress.trim().length in 5..240) { "Adresse de livraison précise requise (min. 5 caractères)." }
            }
            if (paymentMethod == PaymentMethod.MOBILE_MONEY) {
                require(paymentProvider.isNotBlank()) { "Sélectionnez un opérateur Mobile Money." }
                require(paymentPhone.trim().length in 6..24) { "Numéro Mobile Money requis." }
            }

            // Try server-authoritative Cloud Function `placeOrder` first
            val cloudPayload = hashMapOf<String, Any>(
                "customerName" to customerName.trim().take(80),
                "customerPhone" to customerPhone.trim().take(24),
                "fulfillmentMode" to fulfillmentMode.name,
                "deliveryAddress" to (if (fulfillmentMode == FulfillmentMode.PICKUP) "Retrait au point GoPrime" else deliveryAddress.trim().take(240)),
                "paymentMethod" to paymentMethod.name,
                "items" to cartItems.map { item ->
                    hashMapOf("productId" to item.productId, "quantity" to item.quantity)
                }
            )
            if (fulfillmentMode == FulfillmentMode.DELIVERY && selectedZone != null) {
                cloudPayload["districtId"] = selectedZone.id
            }
            if (paymentMethod == PaymentMethod.MOBILE_MONEY) {
                if (paymentProvider.isNotBlank()) cloudPayload["paymentProvider"] = paymentProvider.take(40)
                if (paymentPhone.isNotBlank()) cloudPayload["paymentPhone"] = paymentPhone.trim().take(24)
                if (paymentReference.isNotBlank()) cloudPayload["paymentReference"] = paymentReference.trim().take(80)
            }
            if (notes.isNotBlank()) cloudPayload["notes"] = notes.trim().take(300)

            var shouldFallbackToFirestore = false
            try {
                val callableResult = Firebase.functions
                    .getHttpsCallable("placeOrder")
                    .call(cloudPayload)
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = callableResult.getData() as? Map<String, Any?>
                val orderNumber = (data?.get("orderNumber") as? String).orEmpty()
                if (orderNumber.isNotBlank()) {
                    val batch = db.batch()
                    for (item in cartItems) {
                        batch.delete(db.collection("users").document(uid).collection("cart").document(item.productId))
                    }
                    batch.commit().await()
                    return Result.success(orderNumber)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFunctionsException) {
                if (e.code == FirebaseFunctionsException.Code.NOT_FOUND ||
                    e.code == FirebaseFunctionsException.Code.UNAVAILABLE
                ) {
                    shouldFallbackToFirestore = true
                } else {
                    return Result.failure(e)
                }
            } catch (_: Exception) {
                shouldFallbackToFirestore = true
            }

            if (!shouldFallbackToFirestore) {
                throw IllegalStateException("Réponse inattendue du serveur de commande.")
            }

            // Verify authoritative prices & delivery fee directly from Firestore documents
            var verifiedSubtotalCdf = 0L
            val verifiedLines = mutableListOf<Map<String, Any>>()
            for (cartItem in cartItems) {
                val prodSnap = db.collection("products").document(cartItem.productId).get().await()
                val prod = prodSnap.toObject(Product::class.java)
                    ?: throw IllegalStateException("Produit introuvable : ${cartItem.productName}")
                require(prod.active) { "Produit indisponible : ${prod.name}" }
                require(prod.stock >= cartItem.quantity) { "Stock insuffisant pour ${prod.name} (${prod.stock} disponible(s))." }
                verifiedSubtotalCdf += prod.priceCdf * cartItem.quantity
                verifiedLines.add(
                    mapOf(
                        "productId" to prod.id,
                        "productName" to prod.name.take(120),
                        "unitPriceCdf" to prod.priceCdf,
                        "quantity" to cartItem.quantity
                    )
                )
            }

            val verifiedFeeCdf: Long
            val districtName: String
            val districtId: String?
            if (fulfillmentMode == FulfillmentMode.PICKUP) {
                verifiedFeeCdf = 0L
                districtName = "Point de retrait GoPrime"
                districtId = null
            } else {
                val zoneSnap = db.collection("deliveryZones").document(selectedZone!!.id).get().await()
                val freshZone = zoneSnap.toObject(DeliveryZone::class.java)
                    ?: throw IllegalStateException("Zone de livraison introuvable.")
                require(freshZone.active) { "Cette zone de livraison n'est plus active." }
                verifiedFeeCdf = freshZone.feeCdf
                districtName = freshZone.name.take(80)
                districtId = freshZone.id
            }

            val totalCdf = verifiedSubtotalCdf + verifiedFeeCdf
            val orderNumber = "GP-${(100000..999999).random()}"
            val initialPaymentStatus = if (paymentMethod == PaymentMethod.MOBILE_MONEY) {
                PaymentStatus.PENDING_VERIFICATION.name
            } else {
                PaymentStatus.PENDING.name
            }

            val payload = mapOf(
                "orderNumber" to orderNumber,
                "userId" to uid,
                "customerName" to customerName.trim().take(80),
                "customerPhone" to customerPhone.trim().take(24),
                "fulfillmentMode" to fulfillmentMode.name,
                "districtId" to districtId,
                "districtName" to districtName,
                "deliveryAddress" to (if (fulfillmentMode == FulfillmentMode.PICKUP) "Retrait au point GoPrime" else deliveryAddress.trim().take(240)),
                "items" to verifiedLines,
                "subtotalCdf" to verifiedSubtotalCdf,
                "deliveryFeeCdf" to verifiedFeeCdf,
                "totalCdf" to totalCdf,
                "paymentMethod" to paymentMethod.name,
                "paymentProvider" to paymentProvider.takeIf { paymentMethod == PaymentMethod.MOBILE_MONEY && it.isNotBlank() }?.take(40),
                "paymentPhone" to paymentPhone.trim().takeIf { paymentMethod == PaymentMethod.MOBILE_MONEY && it.isNotBlank() }?.take(24),
                "paymentReference" to paymentReference.trim().takeIf { paymentMethod == PaymentMethod.MOBILE_MONEY && it.isNotBlank() }?.take(80),
                "paymentStatus" to initialPaymentStatus,
                "status" to OrderStatus.PENDING.name,
                "whatsappNotificationStatus" to WhatsappDispatchStatus.PENDING.name,
                "customerWhatsappStatus" to WhatsappDispatchStatus.PENDING.name,
                "notes" to notes.trim().takeIf { it.isNotBlank() }?.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            orderRef.set(payload).await()

            // Clear user's cart after order creation
            val batch = db.batch()
            for (item in cartItems) {
                batch.delete(db.collection("users").document(uid).collection("cart").document(item.productId))
            }
            batch.commit().await()

            Result.success(orderNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, orderRef.path)
            Result.failure(e)
        }
    }

    suspend fun cancelPendingOrder(orderId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        val path = "orders/$orderId"
        return try {
            require(orderId.isNotBlank())
            db.collection("orders").document(orderId).update(
                mapOf(
                    "status" to OrderStatus.CANCELLED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- CUSTOMER RATINGS & REVIEWS ---

    fun observeAllReviews(uid: String): Flow<List<OrderReview>> = flow {
        emitAll(
            db.collection("reviews")
                .snapshots()
                .map { snap ->
                    snap.toObjects(OrderReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "reviews", uid)
        )
    }

    suspend fun submitOrderReview(
        order: Order,
        customerDisplayName: String,
        productRating: Int,
        deliveryRating: Int,
        comment: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        val path = "reviews/${order.id}"
        return try {
            require(order.id.isNotBlank()) { "Commande invalide." }
            require(order.parsedStatus == OrderStatus.COMPLETED) {
                "Seules les commandes terminées peuvent être évaluées."
            }
            require(productRating in 1..5 && deliveryRating in 1..5) {
                "Les notes doivent être comprises entre 1 et 5 étoiles."
            }
            val cleanComment = comment.trim()
            require(cleanComment.length <= 500) { "Le commentaire ne peut pas dépasser 500 caractères." }

            val productIds = order.parsedItems()
                .map { it.productId }
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf("order_${order.id}") }
            val safeName = customerDisplayName.trim().ifBlank { order.customerName }.ifBlank { "Client GoPrime" }.take(80)

            val docRef = db.collection("reviews").document(order.id)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "productRating" to productRating,
                        "deliveryRating" to deliveryRating,
                        "comment" to cleanComment,
                        "customerName" to safeName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "orderId" to order.id,
                        "orderNumber" to order.orderNumber.ifBlank { "GP-${order.id.take(6)}" }.take(32),
                        "userId" to uid,
                        "customerName" to safeName,
                        "productIds" to productIds,
                        "productRating" to productRating,
                        "deliveryRating" to deliveryRating,
                        "comment" to cleanComment,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- DRIVER MISSIONS ---

    fun observeDriverOrders(uid: String): Flow<List<Order>> = flow {
        emitAll(
            db.collection("orders")
                .whereEqualTo("assignedDriverId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "orders", uid)
        )
    }

    suspend fun driverStartDelivery(orderId: String): Result<Unit> {
        requireUserId()
        val path = "orders/$orderId"
        return try {
            db.collection("orders").document(orderId).update(
                mapOf(
                    "status" to OrderStatus.OUT_FOR_DELIVERY.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun driverCompleteDelivery(order: Order): Result<Unit> {
        requireUserId()
        val path = "orders/${order.id}"
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to OrderStatus.COMPLETED.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (order.parsedPaymentMethod == PaymentMethod.CASH) {
                updates["paymentStatus"] = PaymentStatus.PAID.name
            }
            db.collection("orders").document(order.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- ADMIN STREAMS & CRUD ---

    fun observeAllCategories(uid: String): Flow<List<Category>> = flow {
        emitAll(
            db.collection("categories")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.sortOrder }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "categories", uid)
        )
    }

    fun observeAllProducts(uid: String): Flow<List<Product>> = flow {
        emitAll(
            db.collection("products")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "products", uid)
        )
    }

    fun observeAllZones(uid: String): Flow<List<DeliveryZone>> = flow {
        emitAll(
            db.collection("deliveryZones")
                .snapshots()
                .map { snap ->
                    snap.toObjects(DeliveryZone::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "deliveryZones", uid)
        )
    }

    fun observeAllOrders(uid: String): Flow<List<Order>> = flow {
        emitAll(
            db.collection("orders")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "orders", uid)
        )
    }

    fun observeAllUsers(uid: String): Flow<List<UserProfile>> = flow {
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "users", uid)
        )
    }

    fun observeWhatsappSettings(uid: String): Flow<WhatsappIntegrationSettings?> = flow {
        emitAll(
            db.collection("settings").document("whatsapp")
                .snapshots()
                .map { it.toObject(WhatsappIntegrationSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .withFirestoreErrorHandling(OperationType.GET, "settings/whatsapp", uid)
        )
    }

    fun observeNotificationLogs(uid: String): Flow<List<NotificationLog>> = flow {
        emitAll(
            db.collection("notificationLogs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(NotificationLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorHandling(OperationType.LIST, "notificationLogs", uid)
        )
    }

    suspend fun saveCategory(
        categoryId: String?,
        name: String,
        description: String,
        sortOrder: Long,
        active: Boolean
    ): Result<Unit> {
        requireUserId()
        val docRef = if (categoryId.isNullOrBlank()) {
            db.collection("categories").document()
        } else {
            db.collection("categories").document(categoryId)
        }
        return try {
            require(name.trim().length in 1..60) { "Nom de catégorie requis (1-60 car.)." }
            require(description.trim().length <= 200) { "Description trop longue." }
            if (categoryId.isNullOrBlank()) {
                val payload = mapOf(
                    "name" to name.trim(),
                    "description" to description.trim().takeIf { it.isNotEmpty() },
                    "sortOrder" to sortOrder.coerceIn(0L, 10000L),
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to name.trim(),
                        "description" to description.trim(),
                        "sortOrder" to sortOrder.coerceIn(0L, 10000L),
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        requireUserId()
        val path = "categories/$categoryId"
        return try {
            db.collection("categories").document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun allocateProductId(): String = db.collection("products").document().id

    suspend fun uploadProductImage(
        context: Context,
        productId: String,
        imageUri: Uri
    ): Result<Pair<String, String>> {
        requireUserId()
        return try {
            val jpegBytes = withContext(Dispatchers.IO) {
                compressImageToJpeg(context, imageUri, maxDimension = 1080)
            }
            val storagePath = "products/$productId/${UUID.randomUUID()}.jpg"
            val ref = Firebase.storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()
            ref.putBytes(jpegBytes, metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl to storagePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Échec du téléversement Firebase Storage : ${e.localizedMessage ?: "vérifiez l'activation du bucket Storage et storage.rules"}",
                    e
                )
            )
        }
    }

    private fun compressImageToJpeg(context: Context, uri: Uri, maxDimension: Int): ByteArray {
        val resolver = context.contentResolver
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: throw IllegalStateException("Impossible de lire l'image sélectionnée.")

        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDimension * 2 ||
            boundsOptions.outHeight / sampleSize > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Format d'image non pris en charge.")

        val scaled = if (decoded.width > maxDimension || decoded.height > maxDimension) {
            val ratio = minOf(
                maxDimension.toFloat() / decoded.width.toFloat(),
                maxDimension.toFloat() / decoded.height.toFloat()
            )
            val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it != decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        scaled.recycle()
        return out.toByteArray()
    }

    suspend fun saveProduct(
        productId: String?,
        name: String,
        description: String,
        category: Category,
        priceCdf: Long,
        stock: Long,
        imageUrl: String,
        storagePath: String,
        featured: Boolean,
        active: Boolean,
        isNewProductWithPreallocatedId: Boolean = false
    ): Result<Unit> {
        requireUserId()
        val isCreate = productId.isNullOrBlank() || isNewProductWithPreallocatedId
        val docRef = if (productId.isNullOrBlank()) {
            db.collection("products").document()
        } else {
            db.collection("products").document(productId)
        }
        return try {
            require(name.trim().length in 1..120) { "Nom du produit requis." }
            require(description.trim().length <= 1000) { "Description trop longue." }
            require(category.id.isNotBlank()) { "Catégorie requise." }
            require(priceCdf > 0L) { "Le prix en CDF doit être supérieur à 0." }
            require(stock >= 0L) { "Le stock ne peut pas être négatif." }

            if (isCreate) {
                val payload = mapOf(
                    "name" to name.trim(),
                    "description" to description.trim(),
                    "categoryId" to category.id,
                    "categoryName" to category.name.take(60),
                    "priceCdf" to priceCdf,
                    "stock" to stock,
                    "imageUrl" to imageUrl.trim().takeIf { it.isNotEmpty() }?.take(500),
                    "storagePath" to storagePath.trim().takeIf { it.isNotEmpty() }?.take(300),
                    "featured" to featured,
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "name" to name.trim(),
                    "description" to description.trim(),
                    "categoryId" to category.id,
                    "categoryName" to category.name.take(60),
                    "priceCdf" to priceCdf,
                    "stock" to stock,
                    "featured" to featured,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (imageUrl.trim().isNotEmpty()) updates["imageUrl"] = imageUrl.trim().take(500)
                if (storagePath.trim().isNotEmpty()) updates["storagePath"] = storagePath.trim().take(300)
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        requireUserId()
        val path = "products/$productId"
        return try {
            db.collection("products").document(productId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveDeliveryZone(
        zoneId: String?,
        name: String,
        commune: String,
        feeCdf: Long,
        estimatedMinutes: Long,
        active: Boolean
    ): Result<Unit> {
        requireUserId()
        val docRef = if (zoneId.isNullOrBlank()) {
            db.collection("deliveryZones").document()
        } else {
            db.collection("deliveryZones").document(zoneId)
        }
        return try {
            require(name.trim().length in 1..80) { "Nom du quartier requis." }
            require(commune.trim().length in 1..80) { "Commune requise." }
            require(feeCdf >= 0L) { "Frais invalides." }
            require(estimatedMinutes in 5L..1440L) { "Délai estimé invalide (5 à 1440 min)." }

            if (zoneId.isNullOrBlank()) {
                val payload = mapOf(
                    "name" to name.trim(),
                    "commune" to commune.trim(),
                    "feeCdf" to feeCdf,
                    "estimatedMinutes" to estimatedMinutes,
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to name.trim(),
                        "commune" to commune.trim(),
                        "feeCdf" to feeCdf,
                        "estimatedMinutes" to estimatedMinutes,
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteDeliveryZone(zoneId: String): Result<Unit> {
        requireUserId()
        val path = "deliveryZones/$zoneId"
        return try {
            db.collection("deliveryZones").document(zoneId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateOrder(
        orderId: String,
        newStatus: OrderStatus,
        driver: UserProfile?,
        paymentStatus: PaymentStatus,
        notes: String = ""
    ): Result<Unit> {
        requireUserId()
        val path = "orders/$orderId"
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "paymentStatus" to paymentStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (driver != null) {
                updates["assignedDriverId"] = driver.userId
                updates["assignedDriverName"] = driver.displayName.take(80)
            } else {
                updates["assignedDriverId"] = FieldValue.delete()
                updates["assignedDriverName"] = FieldValue.delete()
            }
            val cleanNotes = notes.trim().take(300)
            if (cleanNotes.isNotEmpty()) {
                updates["notes"] = cleanNotes
            } else {
                updates["notes"] = FieldValue.delete()
            }
            db.collection("orders").document(orderId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminSetUserActive(targetUserId: String, active: Boolean): Result<Unit> {
        requireUserId()
        val path = "users/$targetUserId"
        return try {
            db.collection("users").document(targetUserId).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateUserRole(targetUser: UserProfile, newRole: UserRole): Result<Unit> {
        requireUserId()
        val path = "users/${targetUser.userId}"
        return try {
            db.collection("users").document(targetUser.userId).update(
                mapOf(
                    "role" to newRole.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val adminMarkerRef = db.collection("admins").document(targetUser.userId)
            if (newRole == UserRole.ADMIN) {
                adminMarkerRef.set(
                    mapOf(
                        "userId" to targetUser.userId,
                        "email" to targetUser.email.ifBlank { "admin@goprime.app" },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                adminMarkerRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveWhatsappSettings(
        enabled: Boolean,
        phones: List<String>,
        notifyCustomerOnOrder: Boolean,
        templateName: String,
        customerTemplateName: String,
        languageCode: String
    ): Result<Unit> {
        requireUserId()
        val path = "settings/whatsapp"
        return try {
            val sanitizedPhones = phones.map { it.trim() }.filter { it.isNotEmpty() }.take(20)
            val payload = mapOf(
                "enabled" to enabled,
                "adminWhatsappPhones" to sanitizedPhones,
                "notifyCustomerOnOrder" to notifyCustomerOnOrder,
                "templateName" to templateName.trim().ifBlank { "goprime_new_order" }.take(80),
                "customerTemplateName" to customerTemplateName.trim().ifBlank { "goprime_order_confirmation" }.take(80),
                "languageCode" to languageCode.trim().ifBlank { "fr" }.take(16),
                "mobileMoneyProviderMode" to "Vérification Webhook / Cloud Functions requise",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("settings").document("whatsapp").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun testWhatsappNotificationCallable(
        toPhone: String?,
        recipientRole: String = "ADMIN"
    ): Result<String> {
        requireUserId()
        return try {
            val data = buildMap<String, Any> {
                if (!toPhone.isNullOrBlank()) put("toPhone", toPhone.trim())
                put("recipientRole", recipientRole)
            }
            val result = Firebase.functions
                .getHttpsCallable("testWhatsappNotification")
                .call(data)
                .await()
            @Suppress("UNCHECKED_CAST")
            val payload = result.getData() as? Map<String, Any?>
            val ok = payload?.get("ok") as? Boolean ?: false
            val messageId = payload?.get("messageId") as? String
            val statusCode = (payload?.get("statusCode") as? Number)?.toInt() ?: 0
            val errorDetail = payload?.get("errorDetail") as? String
            if (ok) {
                Result.success("Test WhatsApp ($recipientRole) envoyé avec succès (ID : ${messageId ?: "ok"}).")
            } else {
                Result.failure(IllegalStateException("Échec WhatsApp API (HTTP $statusCode) : ${errorDetail ?: "vérifiez la configuration Meta"}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Cloud Function indisponible (${e.localizedMessage ?: "déployez functions/ via Firebase CLI avec les secrets Meta"}).",
                    e
                )
            )
        }
    }

    suspend fun verifyAdminRoleCallable(requireAdmin: Boolean = false): Result<Boolean> {
        requireUserId()
        return try {
            val result = Firebase.functions
                .getHttpsCallable("verifyAdminRole")
                .call(hashMapOf("requireAdmin" to requireAdmin))
                .await()
            @Suppress("UNCHECKED_CAST")
            val payload = result.getData() as? Map<String, Any?>
            val isAdmin = payload?.get("isAdmin") as? Boolean ?: false
            Result.success(isAdmin)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Impossible de vérifier le rôle administrateur côté serveur.",
                    e
                )
            )
        }
    }
}
