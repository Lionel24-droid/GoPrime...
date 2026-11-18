package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.Category
import com.example.data.model.DeliveryZone
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.CdfPriceText
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCdf
import com.example.ui.state.GoPrimeViewModel
import com.example.ui.state.UiState

@Composable
fun AdminDashboardTab(
    ordersState: UiState<List<Order>>,
    productsState: UiState<List<Product>>,
    usersState: UiState<List<UserProfile>>,
    zonesState: UiState<List<DeliveryZone>>,
    onNavigateOrders: () -> Unit,
    onNavigateCatalog: () -> Unit,
    onNavigateIntegrations: () -> Unit
) {
    val orders = (ordersState as? UiState.Success)?.data.orEmpty()
    val products = (productsState as? UiState.Success)?.data.orEmpty()
    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val zones = (zonesState as? UiState.Success)?.data.orEmpty()

    val paidRevenueCdf = orders.filter { it.parsedPaymentStatus == PaymentStatus.PAID }.sumOf { it.totalCdf }
    val pendingCashCdf = orders.filter {
        it.parsedPaymentStatus == PaymentStatus.PENDING && !it.parsedStatus.isTerminal
    }.sumOf { it.totalCdf }
    val pendingMobileMoneyCount = orders.count {
        it.parsedPaymentStatus == PaymentStatus.PENDING_VERIFICATION && !it.parsedStatus.isTerminal
    }
    val pendingOrdersCount = orders.count { it.parsedStatus == OrderStatus.PENDING }
    val lowStockCount = products.count { it.stock <= 5 }
    val driverCount = users.count { it.parsedRole == UserRole.DRIVER }
    val clientCount = users.count { it.parsedRole == UserRole.CLIENT }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Tableau de bord GoPrime",
                subtitle = "Statistiques en temps réel • Devise CDF"
            )
        }

        if (pendingOrdersCount > 0 || pendingMobileMoneyCount > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateOrders() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = buildString {
                                if (pendingOrdersCount > 0) append("$pendingOrdersCount commande(s) en attente")
                                if (pendingOrdersCount > 0 && pendingMobileMoneyCount > 0) append(" • ")
                                if (pendingMobileMoneyCount > 0) append("$pendingMobileMoneyCount Mobile Money à vérifier")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    title = "CA Encaissé",
                    value = formatCdf(paidRevenueCdf),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Espèces à percevoir",
                    value = formatCdf(pendingCashCdf),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    title = "Commandes",
                    value = "${orders.size} ($pendingOrdersCount att.)",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Clients / Livreurs",
                    value = "$clientCount / $driverCount",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    title = "Produits (stock bas)",
                    value = "${products.size} ($lowStockCount)",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Zones actives",
                    value = "${zones.count { it.active }} / ${zones.size}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Actions rapides", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = onNavigateCatalog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ajouter catégories & produits (CDF)")
                    }
                    OutlinedButton(
                        onClick = onNavigateOrders,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Traiter les commandes ($pendingOrdersCount en attente)")
                    }
                    OutlinedButton(
                        onClick = onNavigateIntegrations,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configurer WhatsApp Business & Paiements")
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AdminCatalogTab(
    categoriesState: UiState<List<Category>>,
    productsState: UiState<List<Product>>,
    isBusy: Boolean,
    viewModel: GoPrimeViewModel
) {
    val categories = (categoriesState as? UiState.Success)?.data.orEmpty()
    val products = (productsState as? UiState.Success)?.data.orEmpty()

    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    var productSearch by remember { mutableStateOf("") }
    var lowStockOnly by remember { mutableStateOf(false) }

    if (showCategoryDialog) {
        CategoryEditorDialog(
            initial = editingCategory,
            isBusy = isBusy,
            onDismiss = { showCategoryDialog = false },
            onSave = { name, desc, sortOrder, active ->
                viewModel.saveCategory(editingCategory?.id, name, desc, sortOrder, active)
                showCategoryDialog = false
            }
        )
    }

    if (showProductDialog) {
        val context = LocalContext.current
        ProductEditorDialog(
            initial = editingProduct,
            categories = categories,
            isBusy = isBusy,
            onDismiss = { showProductDialog = false },
            onSave = { name, desc, cat, price, stock, imgUrl, storagePath, featured, active, pickedUri ->
                viewModel.saveProduct(
                    productId = editingProduct?.id,
                    name = name,
                    description = desc,
                    category = cat,
                    priceCdf = price,
                    stock = stock,
                    imageUrl = imgUrl,
                    storagePath = storagePath,
                    featured = featured,
                    active = active,
                    pickedPhotoUri = pickedUri,
                    context = context
                )
                showProductDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Catégories (${categories.size})",
                subtitle = "Créez au moins une catégorie avant d'ajouter des produits",
                trailing = {
                    Button(
                        onClick = {
                            editingCategory = null
                            showCategoryDialog = true
                        },
                        modifier = Modifier.testTag("admin_add_category_button")
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Catégorie")
                    }
                }
            )
        }

        when (categoriesState) {
            UiState.Loading -> item { LoadingStateView("Chargement catégories...") }
            is UiState.Error -> item { ErrorStateCard(categoriesState.message) }
            is UiState.Success -> {
                items(categories, key = { it.id }) { cat ->
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
                                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                if (cat.description.isNotBlank()) {
                                    Text(cat.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            StatusPill(
                                text = if (cat.active) "Actif" else "Masqué",
                                containerColor = if (cat.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (cat.active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = {
                                editingCategory = cat
                                showCategoryDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                            }
                            IconButton(onClick = { viewModel.deleteCategory(cat.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            SectionHeader(
                title = "Produits (${products.size})",
                subtitle = "Prix en CDF & gestion du stock",
                trailing = {
                    Button(
                        onClick = {
                            editingProduct = null
                            showProductDialog = true
                        },
                        enabled = categories.isNotEmpty(),
                        modifier = Modifier.testTag("admin_add_product_button")
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Produit")
                    }
                }
            )
        }

        when (productsState) {
            UiState.Loading -> item { LoadingStateView("Chargement produits...") }
            is UiState.Error -> item { ErrorStateCard(productsState.message) }
            is UiState.Success -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = productSearch,
                            onValueChange = { productSearch = it },
                            label = { Text("Filtrer produits...") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterChip(
                            selected = lowStockOnly,
                            onClick = { lowStockOnly = !lowStockOnly },
                            label = { Text("Stock faible (≤ 5)") }
                        )
                    }
                }
                val filteredProducts = products.filter { p ->
                    val matchesQ = productSearch.isBlank() ||
                        p.name.contains(productSearch.trim(), ignoreCase = true) ||
                        p.categoryName.contains(productSearch.trim(), ignoreCase = true)
                    val matchesStock = !lowStockOnly || p.stock <= 5L
                    matchesQ && matchesStock
                }
                items(filteredProducts, key = { it.id }) { prod ->
                    val prodCategory = categories.firstOrNull { it.id == prod.categoryId }
                        ?: Category(id = prod.categoryId, name = prod.categoryName)
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
                                Text(prod.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${prod.categoryName} • Stock : ${prod.stock}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (prod.stock <= 5L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CdfPriceText(amount = prod.priceCdf)
                            }
                            IconButton(
                                onClick = {
                                    if (prod.stock > 0L) {
                                        viewModel.saveProduct(
                                            prod.id, prod.name, prod.description, prodCategory,
                                            prod.priceCdf, prod.stock - 1L, prod.imageUrl, prod.storagePath,
                                            prod.featured, prod.active
                                        )
                                    }
                                },
                                enabled = !isBusy && prod.stock > 0L
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Diminuer stock")
                            }
                            IconButton(
                                onClick = {
                                    viewModel.saveProduct(
                                        prod.id, prod.name, prod.description, prodCategory,
                                        prod.priceCdf, prod.stock + 1L, prod.imageUrl, prod.storagePath,
                                        prod.featured, prod.active
                                    )
                                },
                                enabled = !isBusy
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Augmenter stock")
                            }
                            IconButton(onClick = {
                                editingProduct = prod
                                showProductDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                            }
                            IconButton(onClick = { viewModel.deleteProduct(prod.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    initial: Category?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var sortOrderText by remember { mutableStateOf((initial?.sortOrder ?: 1L).toString()) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvelle catégorie" else "Modifier catégorie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("category_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sortOrderText,
                    onValueChange = { sortOrderText = it },
                    label = { Text("Ordre d'affichage") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Catégorie active")
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, description, sortOrderText.toLongOrNull() ?: 1L, active)
                },
                enabled = !isBusy && name.isNotBlank()
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun ProductEditorDialog(
    initial: Product?,
    categories: List<Category>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Category, Long, Long, String, String, Boolean, Boolean, Uri?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var selectedCategory by remember {
        mutableStateOf(categories.firstOrNull { it.id == initial?.categoryId } ?: categories.firstOrNull())
    }
    var priceText by remember { mutableStateOf(initial?.priceCdf?.toString().orEmpty()) }
    var stockText by remember { mutableStateOf((initial?.stock ?: 10L).toString()) }
    var imageUrl by remember { mutableStateOf(initial?.imageUrl.orEmpty()) }
    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var featured by remember { mutableStateOf(initial?.featured ?: false) }
    var active by remember { mutableStateOf(initial?.active ?: true) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedPhotoUri = uri
        }
    }

    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau produit (CDF)" else "Modifier produit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du produit *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )
                if (categories.isNotEmpty()) {
                    Text("Catégorie *", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCategory?.id == cat.id,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Prix en CDF *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_price_input")
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it },
                    label = { Text("Stock disponible *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_stock_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("URL image (optionnel si photo importée)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Image, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (pickedPhotoUri == null) "Importer photo (Firebase Storage)" else "Changer la photo sélectionnée")
                }
                val previewModel: Any? = pickedPhotoUri ?: imageUrl.takeIf { it.isNotBlank() }
                if (previewModel != null) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = "Aperçu produit",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (pickedPhotoUri != null) {
                        Text(
                            text = "La photo sera compressée et téléversée automatiquement vers Firebase Storage lors de l'enregistrement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mettre en avant")
                    Switch(checked = featured, onCheckedChange = { featured = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Produit publié (actif)")
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            val price = priceText.toLongOrNull() ?: 0L
            val stock = stockText.toLongOrNull() ?: -1L
            Button(
                onClick = {
                    val cat = selectedCategory ?: return@Button
                    onSave(
                        name,
                        description,
                        cat,
                        price,
                        stock,
                        imageUrl,
                        initial?.storagePath.orEmpty(),
                        featured,
                        active,
                        pickedPhotoUri
                    )
                },
                enabled = !isBusy && name.isNotBlank() && selectedCategory != null && price > 0L && stock >= 0L
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun AdminZonesTab(
    zonesState: UiState<List<DeliveryZone>>,
    isBusy: Boolean,
    viewModel: GoPrimeViewModel
) {
    val zones = (zonesState as? UiState.Success)?.data.orEmpty()
    var editingZone by remember { mutableStateOf<DeliveryZone?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        ZoneEditorDialog(
            initial = editingZone,
            isBusy = isBusy,
            onDismiss = { showDialog = false },
            onSave = { name, commune, fee, mins, active ->
                viewModel.saveZone(editingZone?.id, name, commune, fee, mins, active)
                showDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Zones de livraison (${zones.size})",
                subtitle = "Quartiers, communes et frais en CDF",
                trailing = {
                    Button(
                        onClick = {
                            editingZone = null
                            showDialog = true
                        },
                        modifier = Modifier.testTag("admin_add_zone_button")
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Zone")
                    }
                }
            )
        }
        when (zonesState) {
            UiState.Loading -> item { LoadingStateView("Chargement des zones...") }
            is UiState.Error -> item { ErrorStateCard(zonesState.message) }
            is UiState.Success -> {
                items(zones, key = { it.id }) { zone ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${zone.name} (${zone.commune})", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Délai estimé : ~${zone.estimatedMinutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CdfPriceText(amount = zone.feeCdf)
                            }
                            IconButton(onClick = {
                                editingZone = zone
                                showDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                            }
                            IconButton(onClick = { viewModel.deleteZone(zone.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneEditorDialog(
    initial: DeliveryZone?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Long, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var commune by remember { mutableStateOf(initial?.commune.orEmpty()) }
    var feeText by remember { mutableStateOf((initial?.feeCdf ?: 5000L).toString()) }
    var minsText by remember { mutableStateOf((initial?.estimatedMinutes ?: 35L).toString()) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvelle zone de livraison" else "Modifier zone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Quartier *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = commune,
                    onValueChange = { commune = it },
                    label = { Text("Commune *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = feeText,
                    onValueChange = { feeText = it },
                    label = { Text("Frais de livraison (CDF) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minsText,
                    onValueChange = { minsText = it },
                    label = { Text("Délai estimé (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Zone active")
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            val fee = feeText.toLongOrNull() ?: -1L
            val mins = minsText.toLongOrNull() ?: 30L
            Button(
                onClick = { onSave(name, commune, fee, mins.coerceIn(5L, 1440L), active) },
                enabled = !isBusy && name.isNotBlank() && commune.isNotBlank() && fee >= 0L
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
