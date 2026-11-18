package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.DeliveryStatusCategory
import com.example.data.model.DeliveryZone
import com.example.data.model.FulfillmentMode
import com.example.data.model.Order
import com.example.data.model.OrderReview
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.preferences.AppThemeMode
import com.example.ui.components.AvatarBadge
import com.example.ui.components.CdfPriceText
import com.example.ui.components.DeliveryStatusBadge
import com.example.ui.components.EmptyCatalogCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.OrderProgressTimeline
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.StarRatingBar
import com.example.ui.components.ThemeToggleCard
import com.example.ui.components.WhatsappStatusBadge
import com.example.ui.components.formatCdf
import com.example.ui.components.formatTimestamp
import com.example.ui.state.ActiveSpace
import com.example.ui.state.CatalogSortOption
import com.example.ui.state.GoPrimeViewModel
import com.example.ui.state.UiState
import com.example.ui.theme.StatusWarning
import java.util.Locale

@Composable
fun ClientCatalogScreen(
    categoriesState: UiState<List<Category>>,
    productsState: UiState<List<Product>>,
    cartState: UiState<List<CartItem>>,
    reviewsState: UiState<List<OrderReview>> = UiState.Success(emptyList()),
    userProfile: UserProfile?,
    viewModel: GoPrimeViewModel,
    onOpenCart: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var featuredOnly by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(CatalogSortOption.FEATURED) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    val allProducts = (productsState as? UiState.Success)?.data.orEmpty()
    val allReviews = (reviewsState as? UiState.Success)?.data.orEmpty()
    val dynamicCategories = (categoriesState as? UiState.Success)?.data.orEmpty()
    val effectiveCategoryId = selectedCategoryId?.takeIf { id ->
        dynamicCategories.any { it.id == id }
    }
    val productCountsByCategory = remember(allProducts) {
        allProducts.groupingBy { it.categoryId }.eachCount()
    }

    val cartItems = (cartState as? UiState.Success)?.data.orEmpty()
    val cartQtyByProduct = remember(cartItems) { cartItems.associate { it.productId to it.quantity } }

    if (selectedProduct != null) {
        val currentProduct = selectedProduct!!
        val productReviews = remember(currentProduct.id, allReviews) {
            allReviews.filter { currentProduct.id in it.productIds }
        }
        ProductDetailDialog(
            product = currentProduct,
            currentCartQty = cartQtyByProduct[currentProduct.id] ?: 0L,
            productReviews = productReviews,
            onDismiss = { selectedProduct = null },
            onAddToCart = { qty ->
                viewModel.setCartQuantity(currentProduct, qty)
                selectedProduct = null
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 600.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("catalog_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bienvenue sur GoPrime",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Votre satisfaction, notre priorité • Livraison par quartier ou retrait en CDF.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                        )
                        if (cartItems.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onOpenCart() }
                                    .testTag("banner_open_cart_button")
                            ) {
                                Text(
                                    text = "Voir panier (${cartItems.sumOf { it.quantity }})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = R.drawable.img_goprime_icon,
                        contentDescription = "Emblème GoPrime",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Rechercher un produit...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Rechercher") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("catalog_search_clear")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_search_input")
            )
        }

        when (categoriesState) {
            is UiState.Success -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.testTag("catalog_filter_bar")
                        ) {
                            item {
                                FilterChip(
                                    selected = effectiveCategoryId == null && !featuredOnly,
                                    onClick = {
                                        selectedCategoryId = null
                                        featuredOnly = false
                                    },
                                    label = {
                                        Text(
                                            if (allProducts.isNotEmpty()) "Tout (${allProducts.size})"
                                            else "Tout"
                                        )
                                    },
                                    modifier = Modifier.testTag("filter_chip_all")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = featuredOnly,
                                    onClick = { featuredOnly = !featuredOnly },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("En vedette") },
                                    modifier = Modifier.testTag("filter_chip_featured")
                                )
                            }
                            items(dynamicCategories, key = { it.id }) { cat ->
                                val count = productCountsByCategory[cat.id] ?: 0
                                FilterChip(
                                    selected = effectiveCategoryId == cat.id,
                                    onClick = {
                                        selectedCategoryId = if (effectiveCategoryId == cat.id) null else cat.id
                                    },
                                    label = {
                                        Text(
                                            if (allProducts.isNotEmpty()) "${cat.name} ($count)"
                                            else cat.name
                                        )
                                    },
                                    modifier = Modifier.testTag("filter_chip_category_${cat.id}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                AssistChip(
                                    onClick = { sortMenuExpanded = true },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = "Trier",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Trier : ${sortOption.labelFr}") },
                                    modifier = Modifier.testTag("catalog_sort_chip")
                                )
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    CatalogSortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.labelFr) },
                                            trailingIcon = {
                                                if (sortOption == option) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                sortOption = option
                                                sortMenuExpanded = false
                                            },
                                            modifier = Modifier.testTag("sort_option_${option.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            val hasActiveFilter = effectiveCategoryId != null ||
                                featuredOnly ||
                                searchQuery.isNotBlank() ||
                                sortOption != CatalogSortOption.FEATURED
                            if (hasActiveFilter) {
                                TextButton(
                                    onClick = {
                                        selectedCategoryId = null
                                        featuredOnly = false
                                        searchQuery = ""
                                        sortOption = CatalogSortOption.FEATURED
                                    },
                                    modifier = Modifier.testTag("catalog_reset_filters")
                                ) {
                                    Text("Réinitialiser")
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Error -> {
                item { ErrorStateCard(categoriesState.message) }
            }
            UiState.Loading -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Chargement des catégories Firestore...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        when (productsState) {
            UiState.Loading -> {
                item { LoadingStateView("Chargement du catalogue...") }
            }
            is UiState.Error -> {
                item { ErrorStateCard(productsState.message) }
            }
            is UiState.Success -> {
                val filtered = GoPrimeViewModel.filterAndSortProducts(
                    products = productsState.data,
                    selectedCategoryId = effectiveCategoryId,
                    featuredOnly = featuredOnly,
                    searchQuery = searchQuery,
                    sortOption = sortOption
                )

                if (filtered.isEmpty()) {
                    item {
                        val isAdmin = userProfile?.parsedRole == UserRole.ADMIN
                        val isCatalogEmpty = productsState.data.isEmpty()
                        EmptyCatalogCard(
                            title = if (isCatalogEmpty) "Catalogue en attente" else "Aucun résultat",
                            subtitle = if (isCatalogEmpty) {
                                "Le catalogue GoPrime est géré exclusivement par l'administrateur. Aucun produit fictif n'est pré-rempli."
                            } else {
                                "Aucun produit ne correspond aux filtres ou à la catégorie sélectionnée."
                            },
                            actionLabel = when {
                                isAdmin && isCatalogEmpty -> "Gérer le catalogue (Admin)"
                                !isCatalogEmpty -> "Réinitialiser les filtres"
                                else -> null
                            },
                            onAction = when {
                                isAdmin && isCatalogEmpty -> {
                                    { viewModel.switchSpace(ActiveSpace.ADMIN) }
                                }
                                !isCatalogEmpty -> {
                                    {
                                        selectedCategoryId = null
                                        featuredOnly = false
                                        searchQuery = ""
                                        sortOption = CatalogSortOption.FEATURED
                                    }
                                }
                                else -> null
                            }
                        )
                    }
                } else {
                    val activeCatName = dynamicCategories.firstOrNull { it.id == effectiveCategoryId }?.name
                    item {
                        SectionHeader(
                            title = if (activeCatName != null) "$activeCatName (${filtered.size})" else "Catalogue GoPrime (${filtered.size})",
                            subtitle = "Prix en CDF • Tri : ${sortOption.labelFr} • Touchez un produit pour voir la fiche"
                        )
                    }
                    val productRows = filtered.chunked(columns)
                    items(
                        items = productRows,
                        key = { row -> row.joinToString("_") { it.id } }
                    ) { rowProducts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowProducts.forEach { product ->
                                val qtyInCart = cartQtyByProduct[product.id] ?: 0L
                                val (avgRating, ratingCount) = remember(product.id, allReviews) {
                                    GoPrimeViewModel.summarizeProductReviews(product.id, allReviews)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ProductCatalogGridCard(
                                        product = product,
                                        qtyInCart = qtyInCart,
                                        averageRating = avgRating,
                                        reviewCount = ratingCount,
                                        onClick = { selectedProduct = product },
                                        onQuickAdd = {
                                            viewModel.addToCart(product, delta = 1L, existingQty = qtyInCart)
                                        },
                                        onQuickDecrement = {
                                            viewModel.setCartQuantity(product, qtyInCart - 1L)
                                        }
                                    )
                                }
                            }
                            repeat(columns - rowProducts.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ProductCatalogGridCard(
    product: Product,
    qtyInCart: Long,
    averageRating: Double = 0.0,
    reviewCount: Int = 0,
    onClick: () -> Unit,
    onQuickAdd: () -> Unit,
    onQuickDecrement: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    modifier = Modifier.size(36.dp)
                )
                if (product.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = product.categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (product.featured) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "En vedette",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Top",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                CdfPriceText(amount = product.priceCdf)

                if (reviewCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Note produit",
                            tint = StatusWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f (%d)", averageRating, reviewCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = if (product.stock > 0L) "En stock : ${product.stock}" else "Rupture de stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (product.stock > 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                if (qtyInCart > 0L) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onQuickDecrement,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Retirer 1",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "$qtyInCart",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(
                                onClick = onQuickAdd,
                                enabled = product.stock > qtyInCart,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("add_to_cart_${product.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Ajouter 1",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onQuickAdd,
                        enabled = product.stock > 0L,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("add_to_cart_${product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (product.stock > 0L) "Ajouter" else "Épuisé",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDetailDialog(
    product: Product,
    currentCartQty: Long,
    productReviews: List<OrderReview> = emptyList(),
    onDismiss: () -> Unit,
    onAddToCart: (Long) -> Unit
) {
    var quantity by remember(product.id) {
        mutableLongStateOf(if (currentCartQty > 0) currentCartQty else 1L)
    }
    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (product.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = product.categoryName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                CdfPriceText(amount = product.priceCdf)
                if (productReviews.isNotEmpty()) {
                    val avg = productReviews.map { it.productRating }.average()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StarRatingBar(rating = avg.toInt().coerceIn(1, 5), starSize = 16.dp)
                        Text(
                            text = String.format(Locale.US, "%.1f / 5 (%d avis)", avg, productReviews.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = product.description.ifBlank { "Aucune description complémentaire." },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Stock disponible : ${product.stock}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val comments = productReviews.filter { it.comment.isNotBlank() }.take(3)
                if (comments.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Avis clients récents", style = MaterialTheme.typography.labelLarge)
                    comments.forEach { rev ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rev.customerName.ifBlank { "Client" },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${rev.productRating}/5 ★",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusWarning,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = rev.comment,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                if (product.stock > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            enabled = quantity > 1
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Diminuer")
                        }
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(
                            onClick = { if (quantity < product.stock && quantity < 99) quantity++ },
                            enabled = quantity < product.stock && quantity < 99
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Augmenter")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddToCart(quantity) },
                enabled = product.stock > 0
            ) {
                Text("Ajouter (${formatCdf(product.priceCdf * quantity)})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

@Composable
fun ClientCartAndCheckoutScreen(
    cartState: UiState<List<CartItem>>,
    productsState: UiState<List<Product>>,
    zonesState: UiState<List<DeliveryZone>>,
    userProfile: UserProfile?,
    isBusy: Boolean,
    viewModel: GoPrimeViewModel,
    onOrderPlacedNavigate: () -> Unit
) {
    val cartItems = (cartState as? UiState.Success)?.data.orEmpty()
    val productsById = ((productsState as? UiState.Success)?.data.orEmpty()).associateBy { it.id }
    val zones = (zonesState as? UiState.Success)?.data.orEmpty()
    val liveQuote by viewModel.deliveryQuote.collectAsStateWithLifecycle()
    val isCalculatingQuote by viewModel.isCalculatingQuote.collectAsStateWithLifecycle()

    var fulfillmentMode by remember { mutableStateOf(FulfillmentMode.DELIVERY) }
    var selectedZoneId by remember(userProfile?.defaultDistrictId, zones) {
        mutableStateOf(
            userProfile?.defaultDistrictId?.takeIf { id -> zones.any { it.id == id } }
                ?: zones.firstOrNull()?.id.orEmpty()
        )
    }
    var customerName by remember(userProfile?.displayName) { mutableStateOf(userProfile?.displayName.orEmpty()) }
    var customerPhone by remember(userProfile?.phone) { mutableStateOf(userProfile?.phone.orEmpty()) }
    var deliveryAddress by remember(userProfile?.defaultAddress) { mutableStateOf(userProfile?.defaultAddress.orEmpty()) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var paymentProvider by remember { mutableStateOf("M-Pesa") }
    var paymentPhone by remember(userProfile?.phone) { mutableStateOf(userProfile?.phone.orEmpty()) }
    var paymentReference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }

    val selectedZone = zones.firstOrNull { it.id == selectedZoneId }

    LaunchedEffect(fulfillmentMode, selectedZone?.id, cartItems) {
        viewModel.refreshDeliveryQuote(
            fulfillmentMode = fulfillmentMode,
            selectedZone = selectedZone,
            cartItems = cartItems
        )
    }

    val subtotalCdf = liveQuote?.subtotalCdf ?: cartItems.sumOf { it.lineTotalCdf }
    val deliveryFeeCdf = liveQuote?.deliveryFeeCdf
        ?: if (fulfillmentMode == FulfillmentMode.DELIVERY) selectedZone?.feeCdf ?: 0L else 0L
    val totalCdf = liveQuote?.totalCdf ?: (subtotalCdf + deliveryFeeCdf)

    if (cartState is UiState.Loading) {
        LoadingStateView("Chargement du panier...")
        return
    }

    if (cartItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyCatalogCard(
                title = "Votre panier est vide",
                subtitle = "Parcourez le catalogue GoPrime pour ajouter des articles et calculer vos frais de livraison par quartier."
            )
        }
        return
    }

    val nameError = attemptedSubmit && customerName.trim().length < 2
    val phoneError = attemptedSubmit && customerPhone.trim().length < 6
    val zoneError = attemptedSubmit && fulfillmentMode == FulfillmentMode.DELIVERY && selectedZone == null
    val addressError = attemptedSubmit && fulfillmentMode == FulfillmentMode.DELIVERY && deliveryAddress.trim().length < 5
    val paymentPhoneError = attemptedSubmit && paymentMethod == PaymentMethod.MOBILE_MONEY && paymentPhone.trim().length < 6

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("checkout_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Validation de commande (${cartItems.sumOf { it.quantity }})",
                subtitle = "Montants et frais calculés en CDF"
            )
        }

        items(cartItems, key = { it.productId }) { item ->
            val product = productsById[item.productId]
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.productName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${formatCdf(item.unitPriceCdf)} × ${item.quantity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CdfPriceText(amount = item.lineTotalCdf)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (product != null) viewModel.setCartQuantity(product, item.quantity - 1)
                                else if (item.quantity <= 1) viewModel.removeFromCart(item.productId)
                            }
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Diminuer")
                        }
                        Text("${item.quantity}", style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            onClick = {
                                if (product != null && item.quantity < product.stock) {
                                    viewModel.setCartQuantity(product, item.quantity + 1)
                                }
                            },
                            enabled = product != null && item.quantity < product.stock
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Augmenter")
                        }
                        IconButton(onClick = { viewModel.removeFromCart(item.productId) }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("1. Mode de réception", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = fulfillmentMode == FulfillmentMode.DELIVERY,
                            onClick = { fulfillmentMode = FulfillmentMode.DELIVERY },
                            leadingIcon = { Icon(Icons.Filled.LocalShipping, null, Modifier.size(16.dp)) },
                            label = { Text("Livraison à domicile") },
                            modifier = Modifier.testTag("checkout_mode_delivery")
                        )
                        FilterChip(
                            selected = fulfillmentMode == FulfillmentMode.PICKUP,
                            onClick = { fulfillmentMode = FulfillmentMode.PICKUP },
                            leadingIcon = { Icon(Icons.Filled.Storefront, null, Modifier.size(16.dp)) },
                            label = { Text("Retrait (0 CDF)") },
                            modifier = Modifier.testTag("checkout_mode_pickup")
                        )
                    }

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Nom complet *") },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text("Saisissez votre nom complet (min. 2 caractères).") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_name_input")
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Téléphone de contact *") },
                        placeholder = { Text("+243...") },
                        isError = phoneError,
                        supportingText = if (phoneError) {
                            { Text("Numéro de téléphone valide requis (min. 6 chiffres).") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_phone_input")
                    )

                    if (fulfillmentMode == FulfillmentMode.PICKUP) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Point de retrait GoPrime",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Aucun frais de livraison (0 CDF). Vous serez notifié dès que la commande est prête au retrait.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Text("2. Quartier & adresse précise *", style = MaterialTheme.typography.titleMedium)
                        if (zones.isEmpty()) {
                            ErrorStateCard("Aucune zone de livraison active. L'administrateur doit configurer les quartiers et frais dans l'espace Admin, ou choisissez le retrait.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                zones.forEach { zone ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (zone.id == selectedZoneId) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedZoneId = zone.id }
                                            .testTag("checkout_zone_${zone.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = zone.id == selectedZoneId,
                                                onClick = { selectedZoneId = zone.id }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${zone.name} (${zone.commune})", style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    "Délai estimé : ~${zone.estimatedMinutes} min",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            CdfPriceText(amount = zone.feeCdf)
                                        }
                                    }
                                }
                            }
                        }
                        if (zoneError) {
                            Text(
                                text = "Veuillez sélectionner un quartier de livraison.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedTextField(
                            value = deliveryAddress,
                            onValueChange = { deliveryAddress = it },
                            label = { Text("Adresse précise (avenue, n°, référence/repère) *") },
                            placeholder = { Text("Ex. Av. du Commerce n°14, près du rond-point...") },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                            isError = addressError,
                            supportingText = if (addressError) {
                                { Text("Saisissez une adresse précise (min. 5 caractères) pour le livreur.") }
                            } else null,
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_address_input")
                        )
                    }

                    HorizontalDivider()

                    Text("3. Mode de paiement", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = paymentMethod == PaymentMethod.CASH,
                            onClick = { paymentMethod = PaymentMethod.CASH },
                            leadingIcon = { Icon(Icons.Filled.Payments, null, Modifier.size(16.dp)) },
                            label = { Text("Espèces") },
                            modifier = Modifier.testTag("checkout_payment_cash")
                        )
                        FilterChip(
                            selected = paymentMethod == PaymentMethod.MOBILE_MONEY,
                            onClick = { paymentMethod = PaymentMethod.MOBILE_MONEY },
                            leadingIcon = { Icon(Icons.Filled.ShoppingCart, null, Modifier.size(16.dp)) },
                            label = { Text("Mobile Money") },
                            modifier = Modifier.testTag("checkout_payment_mobile_money")
                        )
                    }

                    if (paymentMethod == PaymentMethod.MOBILE_MONEY) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("M-Pesa", "Orange Money", "Airtel Money").forEach { provider ->
                                FilterChip(
                                    selected = paymentProvider == provider,
                                    onClick = { paymentProvider = provider },
                                    label = { Text(provider) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = paymentPhone,
                            onValueChange = { paymentPhone = it },
                            label = { Text("Numéro Mobile Money *") },
                            placeholder = { Text("+243...") },
                            isError = paymentPhoneError,
                            supportingText = if (paymentPhoneError) {
                                { Text("Numéro Mobile Money valide requis.") }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_payment_phone_input")
                        )
                        OutlinedTextField(
                            value = paymentReference,
                            onValueChange = { paymentReference = it },
                            label = { Text("Référence transaction (optionnel)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Le paiement Mobile Money reste en attente de vérification fournisseur/serveur avant d'être marqué Payé.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Instructions pour la livraison / commande (optionnel)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    if (isCalculatingQuote) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = if (liveQuote?.isFromCloudFunction == true) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    isCalculatingQuote -> "Calcul des frais en cours via Cloud Functions..."
                                    liveQuote?.isFromCloudFunction == true -> "Frais et total vérifiés en direct par Cloud Function (calculateOrderQuote)."
                                    else -> "Frais estimés depuis le tarif du quartier Firestore (déployez Cloud Functions pour le calcul serveur en direct)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sous-total produits")
                        Text(formatCdf(subtotalCdf), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (fulfillmentMode == FulfillmentMode.DELIVERY && selectedZone != null) {
                                "Frais de livraison (${selectedZone.name})"
                            } else {
                                "Frais de livraison"
                            }
                        )
                        Text(formatCdf(deliveryFeeCdf), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total à payer", style = MaterialTheme.typography.titleMedium)
                        CdfPriceText(amount = totalCdf)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Validation instantanée : une notification WhatsApp automatique avec les détails de la transaction est déclenchée pour le client et l'administrateur.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    val formIsValid = customerName.trim().length >= 2 &&
                        customerPhone.trim().length >= 6 &&
                        (fulfillmentMode == FulfillmentMode.PICKUP ||
                            (selectedZone != null && deliveryAddress.trim().length >= 5)) &&
                        (paymentMethod == PaymentMethod.CASH || paymentPhone.trim().length >= 6)

                    Button(
                        onClick = {
                            attemptedSubmit = true
                            if (formIsValid) {
                                viewModel.placeOrder(
                                    customerName = customerName,
                                    customerPhone = customerPhone,
                                    fulfillmentMode = fulfillmentMode,
                                    selectedZone = selectedZone,
                                    deliveryAddress = deliveryAddress,
                                    paymentMethod = paymentMethod,
                                    paymentProvider = paymentProvider,
                                    paymentPhone = paymentPhone,
                                    paymentReference = paymentReference,
                                    notes = notes,
                                    cartItems = cartItems,
                                    onOrderPlaced = { onOrderPlacedNavigate() }
                                )
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("checkout_submit_button")
                    ) {
                        Text(
                            if (isBusy) "Validation en cours..."
                            else "Confirmer la commande • ${formatCdf(totalCdf)}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClientOrdersScreen(
    ordersState: UiState<List<Order>>,
    reviewsState: UiState<List<OrderReview>> = UiState.Success(emptyList()),
    isBusy: Boolean = false,
    viewModel: GoPrimeViewModel,
    onBrowseCatalog: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf<DeliveryStatusCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var orderPendingCancel by remember { mutableStateOf<Order?>(null) }
    var reviewingOrder by remember { mutableStateOf<Order?>(null) }

    val reviewsByOrderId = remember(reviewsState) {
        ((reviewsState as? UiState.Success)?.data.orEmpty()).associateBy { it.orderId }
    }

    if (reviewingOrder != null) {
        val targetOrder = reviewingOrder!!
        OrderReviewDialog(
            order = targetOrder,
            existingReview = reviewsByOrderId[targetOrder.id],
            isBusy = isBusy,
            onDismiss = { reviewingOrder = null },
            onSubmit = { prodRating, delRating, comment ->
                viewModel.submitOrderReview(targetOrder, prodRating, delRating, comment)
                reviewingOrder = null
            }
        )
    }

    if (orderPendingCancel != null) {
        val target = orderPendingCancel!!
        BackHandler { orderPendingCancel = null }
        AlertDialog(
            onDismissRequest = { orderPendingCancel = null },
            title = { Text("Annuler la commande ?") },
            text = {
                Text(
                    "Voulez-vous vraiment annuler la commande ${target.orderNumber} (${formatCdf(target.totalCdf)}) ? Cette action est irréversible."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelOrder(target.id)
                        orderPendingCancel = null
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_cancel_order_button")
                ) {
                    Text("Confirmer l'annulation")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderPendingCancel = null }) {
                    Text("Garder")
                }
            }
        )
    }

    when (ordersState) {
        UiState.Loading -> LoadingStateView("Chargement de vos commandes...")
        is UiState.Error -> ErrorStateCard(ordersState.message, Modifier.padding(16.dp))
        is UiState.Success -> {
            val allOrders = ordersState.data
            if (allOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyCatalogCard(
                        title = "Aucune commande passée",
                        subtitle = "Vos commandes GoPrime et leur statut de livraison (En cours, Livré, Annulé) apparaîtront ici en temps réel.",
                        actionLabel = "Parcourir le catalogue",
                        onAction = onBrowseCatalog
                    )
                }
            } else {
                val inProgressCount = allOrders.count { it.deliveryStatusCategory == DeliveryStatusCategory.IN_PROGRESS }
                val deliveredCount = allOrders.count { it.deliveryStatusCategory == DeliveryStatusCategory.DELIVERED }
                val cancelledCount = allOrders.count { it.deliveryStatusCategory == DeliveryStatusCategory.CANCELLED }

                val filteredOrders = remember(allOrders, selectedCategory, searchQuery) {
                    GoPrimeViewModel.filterOrders(
                        orders = allOrders,
                        categoryFilter = selectedCategory,
                        searchQuery = searchQuery
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("client_orders_lazy_column"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Mes commandes",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text(
                                            text = "${allOrders.size} commande(s) sur votre compte",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OrderSummaryKpiTile(
                                        label = DeliveryStatusCategory.IN_PROGRESS.labelFr,
                                        count = inProgressCount,
                                        selected = selectedCategory == DeliveryStatusCategory.IN_PROGRESS,
                                        onClick = {
                                            selectedCategory = if (selectedCategory == DeliveryStatusCategory.IN_PROGRESS) null
                                            else DeliveryStatusCategory.IN_PROGRESS
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("orders_kpi_in_progress")
                                    )
                                    OrderSummaryKpiTile(
                                        label = DeliveryStatusCategory.DELIVERED.labelFr,
                                        count = deliveredCount,
                                        selected = selectedCategory == DeliveryStatusCategory.DELIVERED,
                                        onClick = {
                                            selectedCategory = if (selectedCategory == DeliveryStatusCategory.DELIVERED) null
                                            else DeliveryStatusCategory.DELIVERED
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("orders_kpi_delivered")
                                    )
                                    OrderSummaryKpiTile(
                                        label = DeliveryStatusCategory.CANCELLED.labelFr,
                                        count = cancelledCount,
                                        selected = selectedCategory == DeliveryStatusCategory.CANCELLED,
                                        onClick = {
                                            selectedCategory = if (selectedCategory == DeliveryStatusCategory.CANCELLED) null
                                            else DeliveryStatusCategory.CANCELLED
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("orders_kpi_cancelled")
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Rechercher (n° commande, produit, quartier...)") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Rechercher") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.testTag("orders_search_clear")
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("orders_search_input")
                        )
                    }

                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.testTag("orders_filter_bar")
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                    label = { Text("Toutes (${allOrders.size})") },
                                    modifier = Modifier.testTag("orders_filter_all")
                                )
                            }
                            items(DeliveryStatusCategory.entries) { category ->
                                val count = when (category) {
                                    DeliveryStatusCategory.IN_PROGRESS -> inProgressCount
                                    DeliveryStatusCategory.DELIVERED -> deliveredCount
                                    DeliveryStatusCategory.CANCELLED -> cancelledCount
                                }
                                val icon = when (category) {
                                    DeliveryStatusCategory.IN_PROGRESS -> Icons.Filled.LocalShipping
                                    DeliveryStatusCategory.DELIVERED -> Icons.Filled.CheckCircle
                                    DeliveryStatusCategory.CANCELLED -> Icons.Filled.Cancel
                                }
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = {
                                        selectedCategory = if (selectedCategory == category) null else category
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("${category.labelFr} ($count)") },
                                    modifier = Modifier.testTag("orders_filter_${category.name.lowercase()}")
                                )
                            }
                        }
                    }

                    if (filteredOrders.isEmpty()) {
                        item {
                            EmptyCatalogCard(
                                title = "Aucune commande trouvée",
                                subtitle = if (selectedCategory != null) {
                                    "Aucune commande avec le statut « ${selectedCategory!!.labelFr} » ne correspond à votre recherche."
                                } else {
                                    "Aucune commande ne correspond à votre recherche."
                                },
                                actionLabel = "Afficher toutes les commandes",
                                onAction = {
                                    selectedCategory = null
                                    searchQuery = ""
                                }
                            )
                        }
                    } else {
                        items(filteredOrders, key = { it.id }) { order ->
                            CustomerOrderCard(
                                order = order,
                                review = reviewsByOrderId[order.id],
                                isBusy = isBusy,
                                onRequestCancel = { orderPendingCancel = order },
                                onRequestReview = { reviewingOrder = order }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSummaryKpiTile(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
        },
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onPrimary) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CustomerOrderCard(
    order: Order,
    review: OrderReview?,
    isBusy: Boolean,
    onRequestCancel: () -> Unit,
    onRequestReview: () -> Unit
) {
    val category = order.deliveryStatusCategory
    var expanded by remember(order.id) {
        mutableStateOf(category == DeliveryStatusCategory.IN_PROGRESS)
    }
    val parsedItems = remember(order.items) { order.parsedItems() }
    val itemCount = remember(parsedItems) { parsedItems.sumOf { it.quantity } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_card_${order.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            width = if (category == DeliveryStatusCategory.IN_PROGRESS) 1.5.dp else 1.dp,
            color = if (category == DeliveryStatusCategory.IN_PROGRESS) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.orderNumber.ifBlank { "Commande #${order.id.take(6)}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatTimestamp(order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DeliveryStatusBadge(
                        category = category,
                        modifier = Modifier.testTag("order_delivery_badge_${order.id}")
                    )
                    if (category == DeliveryStatusCategory.IN_PROGRESS || order.parsedStatus == OrderStatus.REJECTED) {
                        OrderStatusBadge(status = order.parsedStatus)
                    }
                }
            }

            OrderProgressTimeline(
                status = order.parsedStatus,
                isPickup = order.parsedFulfillment == FulfillmentMode.PICKUP
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (order.parsedFulfillment == FulfillmentMode.PICKUP) {
                            Icons.Filled.Storefront
                        } else {
                            Icons.Filled.LocationOn
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${order.parsedFulfillment.labelFr} • ${order.districtName}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (order.deliveryAddress.isNotBlank()) {
                            Text(
                                text = order.deliveryAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    PaymentStatusBadge(status = order.parsedPaymentStatus)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = order.parsedPaymentMethod.labelFr,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                CdfPriceText(amount = order.totalCdf)
            }

            if (order.assignedDriverName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Livreur assigné : ${order.assignedDriverName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .minimumInteractiveComponentSize()
                    .testTag("order_details_toggle_${order.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Masquer les articles ($itemCount)" else "Voir les articles ($itemCount)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Réduire" else "Déplier",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    parsedItems.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${line.quantity}× ${line.productName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatCdf(line.unitPriceCdf * line.quantity),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sous-total", style = MaterialTheme.typography.bodySmall)
                        Text(formatCdf(order.subtotalCdf), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frais livraison", style = MaterialTheme.typography.bodySmall)
                        Text(formatCdf(order.deliveryFeeCdf), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleSmall)
                        CdfPriceText(amount = order.totalCdf)
                    }

                    if (order.parsedPaymentMethod == PaymentMethod.MOBILE_MONEY && order.paymentProvider.isNotBlank()) {
                        Text(
                            text = "Mobile Money : ${order.paymentProvider} • ${order.paymentPhone}${if (order.paymentReference.isNotBlank()) " (Réf : ${order.paymentReference})" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (order.notes.isNotBlank()) {
                        Text(
                            text = "Note : ${order.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WhatsappStatusBadge(
                            status = order.parsedCustomerWhatsappStatus,
                            labelPrefix = "WhatsApp Client"
                        )
                    }
                }
            }

            if (order.parsedStatus == OrderStatus.COMPLETED) {
                if (review != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Votre avis",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = onRequestReview,
                                    modifier = Modifier.testTag("rate_order_${order.id}")
                                ) {
                                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Modifier")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Produits", style = MaterialTheme.typography.bodySmall)
                                StarRatingBar(rating = review.productRating, starSize = 16.dp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Qualité livraison", style = MaterialTheme.typography.bodySmall)
                                StarRatingBar(rating = review.deliveryRating, starSize = 16.dp)
                            }
                            if (review.comment.isNotBlank()) {
                                Text(
                                    text = "« ${review.comment} »",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onRequestReview,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("rate_order_${order.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Évaluer produits & livraison")
                    }
                }
            }

            if (order.parsedStatus == OrderStatus.PENDING) {
                OutlinedButton(
                    onClick = onRequestCancel,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("cancel_order_${order.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Annuler la commande", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun OrderReviewDialog(
    order: Order,
    existingReview: OrderReview?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int, Int, String) -> Unit
) {
    var productRating by remember(order.id) { mutableIntStateOf(existingReview?.productRating ?: 5) }
    var deliveryRating by remember(order.id) { mutableIntStateOf(existingReview?.deliveryRating ?: 5) }
    var comment by remember(order.id) { mutableStateOf(existingReview?.comment.orEmpty()) }
    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingReview != null) "Modifier votre avis" else "Évaluer la commande ${order.orderNumber}"
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Notez la qualité des produits reçus ainsi que votre expérience de livraison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Qualité des produits ($productRating / 5)", style = MaterialTheme.typography.labelLarge)
                    StarRatingBar(
                        rating = productRating,
                        onRatingChanged = { productRating = it },
                        starSize = 28.dp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Qualité de la livraison ($deliveryRating / 5)", style = MaterialTheme.typography.labelLarge)
                    StarRatingBar(
                        rating = deliveryRating,
                        onRatingChanged = { deliveryRating = it },
                        starSize = 28.dp
                    )
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 500) comment = it },
                    label = { Text("Commentaire (optionnel)") },
                    placeholder = { Text("Produits conformes, livreur ponctuel...") },
                    minLines = 3,
                    maxLines = 5,
                    supportingText = { Text("${comment.length}/500") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_comment_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(productRating, deliveryRating, comment) },
                enabled = !isBusy,
                modifier = Modifier.testTag("submit_review_button")
            ) {
                Text(if (existingReview != null) "Mettre à jour" else "Publier l'avis")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun ClientProfileScreen(
    userProfile: UserProfile?,
    zonesState: UiState<List<DeliveryZone>>,
    isBusy: Boolean,
    viewModel: GoPrimeViewModel,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isDarkTheme: Boolean = false,
    onChangeThemeMode: (AppThemeMode) -> Unit = {},
    onSignOut: () -> Unit
) {
    val zones = (zonesState as? UiState.Success)?.data.orEmpty()
    var displayName by remember(userProfile?.displayName) { mutableStateOf(userProfile?.displayName.orEmpty()) }
    var phone by remember(userProfile?.phone) { mutableStateOf(userProfile?.phone.orEmpty()) }
    var defaultDistrictId by remember(userProfile?.defaultDistrictId) { mutableStateOf(userProfile?.defaultDistrictId.orEmpty()) }
    var defaultAddress by remember(userProfile?.defaultAddress) { mutableStateOf(userProfile?.defaultAddress.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarBadge(name = userProfile?.displayName ?: "G")
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userProfile?.displayName ?: "Client GoPrime",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = userProfile?.email.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = userProfile?.parsedRole?.labelFr ?: "Client",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        ThemeToggleCard(
            themeMode = themeMode,
            isDarkTheme = isDarkTheme,
            onChangeThemeMode = onChangeThemeMode
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Coordonnées & livraison", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nom complet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone (+243...)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (zones.isNotEmpty()) {
                    Text("Quartier favori", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(zones, key = { it.id }) { zone ->
                            FilterChip(
                                selected = defaultDistrictId == zone.id,
                                onClick = { defaultDistrictId = zone.id },
                                label = { Text("${zone.name} (${formatCdf(zone.feeCdf)})") }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = defaultAddress,
                    onValueChange = { defaultAddress = it },
                    label = { Text("Adresse par défaut") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.updateProfile(displayName, phone, defaultDistrictId, defaultAddress)
                    },
                    enabled = !isBusy && displayName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_profile_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enregistrer le profil")
                }
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Se déconnecter")
        }
    }
}
