package com.example.ui.state

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firestore.DeliveryQuote
import com.example.data.firestore.GoPrimeRepository
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.DeliveryStatusCategory
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
import com.example.data.model.WhatsappIntegrationSettings
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveSpace(val labelFr: String) {
    CLIENT("Client"),
    DRIVER("Livraison"),
    ADMIN("Admin")
}

enum class CatalogSortOption(val labelFr: String) {
    FEATURED("Pertinence"),
    PRICE_ASC("Prix croissant"),
    PRICE_DESC("Prix décroissant"),
    NAME_ASC("Nom A–Z")
}

@OptIn(ExperimentalCoroutinesApi::class)
class GoPrimeViewModel(
    private val repository: GoPrimeRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _currentRole = MutableStateFlow(UserRole.CLIENT)

    private val _activeSpace = MutableStateFlow(ActiveSpace.CLIENT)
    val activeSpace: StateFlow<ActiveSpace> = _activeSpace.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _deliveryQuote = MutableStateFlow<DeliveryQuote?>(null)
    val deliveryQuote: StateFlow<DeliveryQuote?> = _deliveryQuote.asStateFlow()

    private val _isCalculatingQuote = MutableStateFlow(false)
    val isCalculatingQuote: StateFlow<Boolean> = _isCalculatingQuote.asStateFlow()

    private var quoteJob: Job? = null

    private fun <T> Flow<T>.toUiStateFlow(tag: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $tag", rootError)
                emit(UiState.Error(rootError.message ?: "Impossible de charger $tag"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiStateFlow("profil")

    private val currentRoleFlow: Flow<UserRole> = userProfileState.map { state ->
        (state as? UiState.Success)?.data?.parsedRole ?: UserRole.CLIENT
    }

    init {
        viewModelScope.launch {
            repository.ensureUserProfile().onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "Erreur d'initialisation du profil."
            }
        }
        viewModelScope.launch {
            currentRoleFlow.collect { role ->
                _currentRole.value = role
                val clamped = clampSpaceForRole(role, _activeSpace.value)
                if (_activeSpace.value != clamped) {
                    _activeSpace.value = clamped
                }
            }
        }
    }

    val activeCategoriesState: StateFlow<UiState<List<Category>>> =
        repository.observeActiveCategories(currentUserId).toUiStateFlow("catégories")

    val activeProductsState: StateFlow<UiState<List<Product>>> =
        repository.observeActiveProducts(currentUserId).toUiStateFlow("produits")

    val activeZonesState: StateFlow<UiState<List<DeliveryZone>>> =
        repository.observeActiveZones(currentUserId).toUiStateFlow("zones de livraison")

    private val _localCartOverlay = MutableStateFlow<Map<String, CartItem?>>(emptyMap())

    val cartState: StateFlow<UiState<List<CartItem>>> =
        combine(
            repository.observeCart(currentUserId).toUiStateFlow("panier"),
            _localCartOverlay
        ) { remoteState, overlay ->
            if (overlay.isEmpty()) {
                remoteState
            } else {
                val baseItems = (remoteState as? UiState.Success)?.data.orEmpty()
                UiState.Success(mergeLocalCartOverlay(baseItems, overlay))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val cartItemCount: StateFlow<Int> = cartState
        .map { state ->
            ((state as? UiState.Success)?.data.orEmpty()).sumOf { it.quantity }.toInt()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0
        )

    val customerOrdersState: StateFlow<UiState<List<Order>>> =
        repository.observeCustomerOrders(currentUserId).toUiStateFlow("commandes client")

    val reviewsState: StateFlow<UiState<List<OrderReview>>> =
        repository.observeAllReviews(currentUserId).toUiStateFlow("avis et notes")

    val driverOrdersState: StateFlow<UiState<List<Order>>> =
        currentRoleFlow.flatMapLatest { role ->
            if (role == UserRole.DRIVER || role == UserRole.ADMIN) {
                repository.observeDriverOrders(currentUserId)
            } else {
                flowOf(emptyList())
            }
        }.toUiStateFlow("missions livreur")

    val adminCategoriesState: StateFlow<UiState<List<Category>>> =
        currentRoleFlow.flatMapLatest { role ->
            if (role == UserRole.ADMIN) repository.observeAllCategories(currentUserId)
            else flowOf(emptyList())
        }.toUiStateFlow("catégories admin")

    val adminProductsState: StateFlow<UiState<List<Product>>> =
        currentRoleFlow.flatMapLatest { role ->
            if (role == UserRole.ADMIN) repository.observeAllProducts(currentUserId)
            else flowOf(emptyList())
        }.toUiStateFlow("produits admin")

    val adminZonesState: StateFlow<UiState<List<DeliveryZone>>> =
        currentRoleFlow.flatMapLatest { role ->
            if (role == UserRole.ADMIN) repository.observeAllZones(currentUserId)
            else flowOf(emptyList())
        }.toUiStateFlow("zones admin")

    val adminOrdersState: StateFlow<UiState<List<Order>>> =
        currentRoleFlow.flatMapLatest { role ->
            if (role == UserRole.ADMIN) repository.observeAllOrders(currentUserId)
            else flowOf(emptyList())
        }.toUiStateFlow("commandes admin")

    val adminUsersState: StateFlow<UiState<List<UserProfile>>> =
        currentRoleFlow.flatMapLatest { role ->
            if (role == UserRole.ADMIN) repository.observeAllUsers(currentUserId)
            else flowOf(emptyList())
        }.toUiStateFlow("utilisateurs admin")

    val whatsappSettingsState: StateFlow<UiState<WhatsappIntegrationSettings?>> =
        currentRoleFlow.flatMapLatest { role ->
            if (role == UserRole.ADMIN) repository.observeWhatsappSettings(currentUserId)
            else flowOf(null)
        }.toUiStateFlow("paramètres WhatsApp")

    val notificationLogsState: StateFlow<UiState<List<NotificationLog>>> =
        currentRoleFlow.flatMapLatest { role ->
            if (role == UserRole.ADMIN) repository.observeNotificationLogs(currentUserId)
            else flowOf(emptyList())
        }.toUiStateFlow("journaux WhatsApp")

    fun switchSpace(space: ActiveSpace) {
        val role = _currentRole.value
        if (space !in allowedSpacesFor(role)) {
            _activeSpace.value = clampSpaceForRole(role, _activeSpace.value)
            _feedbackMessage.value = "Accès refusé : un compte client ne peut pas accéder à l'espace administrateur."
            return
        }
        _activeSpace.value = space
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    private fun runMutation(successMessage: String? = null, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = block()
            _isBusy.value = false
            result.onSuccess {
                if (!successMessage.isNullOrBlank()) _feedbackMessage.value = successMessage
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Une erreur est survenue."
            }
        }
    }

    fun addToCart(product: Product, delta: Long = 1L, existingQty: Long = 0L) {
        val currentLocalQty = (_localCartOverlay.value[product.id]?.quantity)
            ?: ((cartState.value as? UiState.Success)?.data?.firstOrNull { it.productId == product.id }?.quantity)
            ?: existingQty
        val targetQty = (currentLocalQty + delta).coerceAtMost(minOf(99L, maxOf(1L, product.stock)))
        applyLocalCartChange(product, targetQty)
        runMutation("Panier mis à jour") {
            try {
                repository.upsertCartItem(product, targetQty)
            } finally {
                clearLocalCartOverlayKey(product.id)
            }
        }
    }

    fun setCartQuantity(product: Product, quantity: Long) {
        val targetQty = if (quantity <= 0L) 0L else quantity.coerceIn(1L, minOf(99L, maxOf(1L, product.stock)))
        applyLocalCartChange(product, targetQty)
        runMutation {
            try {
                repository.upsertCartItem(product, targetQty)
            } finally {
                clearLocalCartOverlayKey(product.id)
            }
        }
    }

    fun removeFromCart(productId: String) {
        _localCartOverlay.update { it + (productId to null) }
        runMutation("Article retiré du panier") {
            try {
                repository.removeCartItem(productId)
            } finally {
                clearLocalCartOverlayKey(productId)
            }
        }
    }

    private fun applyLocalCartChange(product: Product, targetQty: Long) {
        if (product.id.isBlank()) return
        val entry = if (targetQty <= 0L) {
            null
        } else {
            CartItem(
                id = product.id,
                productId = product.id,
                productName = product.name.take(120),
                unitPriceCdf = product.priceCdf,
                quantity = targetQty,
                imageUrl = product.imageUrl
            )
        }
        _localCartOverlay.update { it + (product.id to entry) }
    }

    private fun clearLocalCartOverlayKey(productId: String) {
        _localCartOverlay.update { it - productId }
    }

    fun updateProfile(
        displayName: String,
        phone: String,
        defaultDistrictId: String,
        defaultAddress: String
    ) {
        runMutation("Profil enregistré") {
            repository.updateUserProfile(displayName, phone, defaultDistrictId, defaultAddress)
        }
    }

    fun refreshDeliveryQuote(
        fulfillmentMode: FulfillmentMode,
        selectedZone: DeliveryZone?,
        cartItems: List<CartItem>
    ) {
        quoteJob?.cancel()
        if (cartItems.isEmpty()) {
            _deliveryQuote.value = null
            _isCalculatingQuote.value = false
            return
        }
        quoteJob = viewModelScope.launch {
            _isCalculatingQuote.value = true
            val quote = repository.calculateOrderQuote(
                fulfillmentMode = fulfillmentMode,
                selectedZone = selectedZone,
                cartItems = cartItems
            )
            _deliveryQuote.value = quote
            _isCalculatingQuote.value = false
        }
    }

    fun placeOrder(
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
        cartItems: List<CartItem>,
        onOrderPlaced: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.placeOrder(
                customerName,
                customerPhone,
                fulfillmentMode,
                selectedZone,
                deliveryAddress,
                paymentMethod,
                paymentProvider,
                paymentPhone,
                paymentReference,
                notes,
                cartItems
            )
            _isBusy.value = false
            result.onSuccess { orderNumber ->
                _localCartOverlay.value = emptyMap()
                _feedbackMessage.value = "Commande $orderNumber enregistrée (en attente de confirmation)."
                onOrderPlaced(orderNumber)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Impossible de valider la commande."
            }
        }
    }

    fun cancelOrder(orderId: String) {
        runMutation("Commande annulée") {
            repository.cancelPendingOrder(orderId)
        }
    }

    fun submitOrderReview(
        order: Order,
        productRating: Int,
        deliveryRating: Int,
        comment: String
    ) {
        val displayName = (userProfileState.value as? UiState.Success)?.data?.displayName.orEmpty()
        runMutation("Merci pour votre évaluation !") {
            repository.submitOrderReview(
                order = order,
                customerDisplayName = displayName,
                productRating = productRating,
                deliveryRating = deliveryRating,
                comment = comment
            )
        }
    }

    fun driverStartDelivery(orderId: String) {
        runMutation("Livraison démarrée") {
            repository.driverStartDelivery(orderId)
        }
    }

    fun driverCompleteDelivery(order: Order) {
        runMutation("Livraison marquée terminée") {
            repository.driverCompleteDelivery(order)
        }
    }

    fun saveCategory(
        categoryId: String?,
        name: String,
        description: String,
        sortOrder: Long,
        active: Boolean
    ) {
        runMutation("Catégorie enregistrée") {
            repository.saveCategory(categoryId, name, description, sortOrder, active)
        }
    }

    fun deleteCategory(categoryId: String) {
        runMutation("Catégorie supprimée") {
            repository.deleteCategory(categoryId)
        }
    }

    fun saveProduct(
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
        pickedPhotoUri: Uri? = null,
        context: Context? = null
    ) {
        runMutation("Produit enregistré") {
            var finalImageUrl = imageUrl
            var finalStoragePath = storagePath
            var preallocatedId = productId
            var isNewPreallocated = false

            if (pickedPhotoUri != null && context != null) {
                if (preallocatedId.isNullOrBlank()) {
                    preallocatedId = repository.allocateProductId()
                    isNewPreallocated = true
                }
                val uploadResult = repository.uploadProductImage(context, preallocatedId, pickedPhotoUri)
                if (uploadResult.isFailure) {
                    return@runMutation Result.failure(
                        uploadResult.exceptionOrNull() ?: IllegalStateException("Échec téléversement image.")
                    )
                }
                val (uploadedUrl, uploadedPath) = uploadResult.getOrThrow()
                finalImageUrl = uploadedUrl
                finalStoragePath = uploadedPath
            }

            repository.saveProduct(
                productId = preallocatedId,
                name = name,
                description = description,
                category = category,
                priceCdf = priceCdf,
                stock = stock,
                imageUrl = finalImageUrl,
                storagePath = finalStoragePath,
                featured = featured,
                active = active,
                isNewProductWithPreallocatedId = isNewPreallocated
            )
        }
    }

    fun deleteProduct(productId: String) {
        runMutation("Produit supprimé") {
            repository.deleteProduct(productId)
        }
    }

    fun saveZone(
        zoneId: String?,
        name: String,
        commune: String,
        feeCdf: Long,
        estimatedMinutes: Long,
        active: Boolean
    ) {
        runMutation("Zone de livraison enregistrée") {
            repository.saveDeliveryZone(zoneId, name, commune, feeCdf, estimatedMinutes, active)
        }
    }

    fun deleteZone(zoneId: String) {
        runMutation("Zone supprimée") {
            repository.deleteDeliveryZone(zoneId)
        }
    }

    fun adminUpdateOrder(
        orderId: String,
        newStatus: OrderStatus,
        driver: UserProfile?,
        paymentStatus: PaymentStatus,
        notes: String = ""
    ) {
        runMutation("Commande mise à jour") {
            repository.adminUpdateOrder(orderId, newStatus, driver, paymentStatus, notes)
        }
    }

    fun adminUpdateUserRole(targetUser: UserProfile, newRole: UserRole) {
        runMutation("Rôle mis à jour") {
            repository.adminUpdateUserRole(targetUser, newRole)
        }
    }

    fun adminSetUserActive(targetUserId: String, active: Boolean) {
        runMutation(if (active) "Compte activé" else "Compte désactivé") {
            repository.adminSetUserActive(targetUserId, active)
        }
    }

    fun saveWhatsappSettings(
        enabled: Boolean,
        phones: List<String>,
        notifyCustomerOnOrder: Boolean,
        templateName: String,
        customerTemplateName: String,
        languageCode: String
    ) {
        runMutation("Configuration WhatsApp enregistrée") {
            repository.saveWhatsappSettings(
                enabled = enabled,
                phones = phones,
                notifyCustomerOnOrder = notifyCustomerOnOrder,
                templateName = templateName,
                customerTemplateName = customerTemplateName,
                languageCode = languageCode
            )
        }
    }

    fun testWhatsappNotification(
        toPhone: String?,
        recipientRole: String = "ADMIN"
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.testWhatsappNotificationCallable(toPhone, recipientRole)
            _isBusy.value = false
            result.onSuccess { msg ->
                _feedbackMessage.value = msg
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Échec du test WhatsApp."
            }
        }
    }

    companion object {
        private const val TAG = "GoPrimeVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun allowedSpacesFor(role: UserRole): List<ActiveSpace> = when (role) {
            UserRole.ADMIN -> listOf(ActiveSpace.CLIENT, ActiveSpace.DRIVER, ActiveSpace.ADMIN)
            UserRole.DRIVER -> listOf(ActiveSpace.CLIENT, ActiveSpace.DRIVER)
            UserRole.CLIENT -> listOf(ActiveSpace.CLIENT)
        }

        fun clampSpaceForRole(role: UserRole, requested: ActiveSpace): ActiveSpace =
            if (requested in allowedSpacesFor(role)) requested else ActiveSpace.CLIENT

        fun filterAndSortProducts(
            products: List<Product>,
            selectedCategoryId: String?,
            featuredOnly: Boolean,
            searchQuery: String,
            sortOption: CatalogSortOption
        ): List<Product> {
            val q = searchQuery.trim()
            val filtered = products.filter { product ->
                val matchesCategory = selectedCategoryId == null || product.categoryId == selectedCategoryId
                val matchesFeatured = !featuredOnly || product.featured
                val matchesSearch = q.isEmpty() ||
                    product.name.contains(q, ignoreCase = true) ||
                    product.description.contains(q, ignoreCase = true) ||
                    product.categoryName.contains(q, ignoreCase = true)
                matchesCategory && matchesFeatured && matchesSearch
            }
            return when (sortOption) {
                CatalogSortOption.FEATURED -> filtered.sortedWith(
                    compareByDescending<Product> { it.featured }
                        .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                )
                CatalogSortOption.PRICE_ASC -> filtered.sortedWith(
                    compareBy<Product> { it.priceCdf }
                        .thenBy { it.name.lowercase() }
                )
                CatalogSortOption.PRICE_DESC -> filtered.sortedWith(
                    compareByDescending<Product> { it.priceCdf }
                        .thenBy { it.name.lowercase() }
                )
                CatalogSortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            }
        }

        fun filterOrders(
            orders: List<Order>,
            categoryFilter: DeliveryStatusCategory?,
            searchQuery: String = ""
        ): List<Order> {
            val q = searchQuery.trim()
            return orders.filter { order ->
                val matchesCategory = categoryFilter == null || order.deliveryStatusCategory == categoryFilter
                val matchesSearch = q.isEmpty() ||
                    order.orderNumber.contains(q, ignoreCase = true) ||
                    order.districtName.contains(q, ignoreCase = true) ||
                    order.deliveryAddress.contains(q, ignoreCase = true) ||
                    order.parsedItems().any { it.productName.contains(q, ignoreCase = true) }
                matchesCategory && matchesSearch
            }
        }

        fun mergeLocalCartOverlay(
            remoteItems: List<CartItem>,
            overlay: Map<String, CartItem?>
        ): List<CartItem> {
            if (overlay.isEmpty()) return remoteItems
            val merged = remoteItems.associateBy { it.productId }.toMutableMap()
            overlay.forEach { (productId, localItem) ->
                if (localItem == null || localItem.quantity <= 0L) {
                    merged.remove(productId)
                } else {
                    merged[productId] = localItem
                }
            }
            return merged.values.sortedBy { it.productName }
        }

        fun summarizeProductReviews(
            productId: String,
            reviews: List<OrderReview>
        ): Pair<Double, Int> {
            val matching = reviews.filter { productId in it.productIds }
            if (matching.isEmpty()) return 0.0 to 0
            val avg = matching.map { it.productRating }.average()
            return avg to matching.size
        }

        fun summarizeDeliveryReviews(reviews: List<OrderReview>): Pair<Double, Int> {
            if (reviews.isEmpty()) return 0.0 to 0
            return reviews.map { it.deliveryRating }.average() to reviews.size
        }
    }
}
