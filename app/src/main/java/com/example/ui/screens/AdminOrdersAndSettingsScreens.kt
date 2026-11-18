package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DEFAULT_ADMIN_WHATSAPP_PHONE
import com.example.data.model.NotificationLog
import com.example.data.model.Order
import com.example.data.model.OrderReview
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WhatsappIntegrationSettings
import com.example.data.preferences.AppThemeMode
import com.example.ui.components.CdfPriceText
import com.example.ui.components.EmptyCatalogCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.StarRatingBar
import com.example.ui.components.StatusPill
import com.example.ui.components.ThemeToggleCard
import com.example.ui.components.WhatsappStatusBadge
import com.example.ui.components.formatCdf
import com.example.ui.components.formatTimestamp
import com.example.ui.state.GoPrimeViewModel
import com.example.ui.state.UiState

@Composable
fun AdminOrdersTab(
    ordersState: UiState<List<Order>>,
    usersState: UiState<List<UserProfile>>,
    reviewsState: UiState<List<OrderReview>> = UiState.Success(emptyList()),
    isBusy: Boolean,
    viewModel: GoPrimeViewModel
) {
    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val drivers = remember(users) { users.filter { it.parsedRole == UserRole.DRIVER || it.parsedRole == UserRole.ADMIN } }
    val reviewsByOrderId = remember(reviewsState) {
        ((reviewsState as? UiState.Success)?.data.orEmpty()).associateBy { it.orderId }
    }
    var filterStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var orderSearch by remember { mutableStateOf("") }
    var managingOrder by remember { mutableStateOf<Order?>(null) }

    if (managingOrder != null) {
        AdminOrderManageDialog(
            order = managingOrder!!,
            drivers = drivers,
            isBusy = isBusy,
            onDismiss = { managingOrder = null },
            onSave = { status, driver, payStatus, notes ->
                viewModel.adminUpdateOrder(managingOrder!!.id, status, driver, payStatus, notes)
                managingOrder = null
            }
        )
    }

    when (ordersState) {
        UiState.Loading -> LoadingStateView("Chargement des commandes...")
        is UiState.Error -> ErrorStateCard(ordersState.message, Modifier.padding(16.dp))
        is UiState.Success -> {
            val filtered = ordersState.data.filter { order ->
                val matchesStatus = filterStatus == null || order.parsedStatus == filterStatus
                val q = orderSearch.trim()
                val matchesQ = q.isEmpty() ||
                    order.orderNumber.contains(q, ignoreCase = true) ||
                    order.customerName.contains(q, ignoreCase = true) ||
                    order.customerPhone.contains(q, ignoreCase = true)
                matchesStatus && matchesQ
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SectionHeader(
                        title = "Commandes (${ordersState.data.size})",
                        subtitle = "Assignation livreurs, paiements et suivi WhatsApp"
                    )
                }
                item {
                    OutlinedTextField(
                        value = orderSearch,
                        onValueChange = { orderSearch = it },
                        label = { Text("Rechercher commande, client, téléphone...") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = filterStatus == null,
                                onClick = { filterStatus = null },
                                label = { Text("Toutes") }
                            )
                        }
                        items(OrderStatus.entries) { status ->
                            FilterChip(
                                selected = filterStatus == status,
                                onClick = { filterStatus = if (filterStatus == status) null else status },
                                label = { Text(status.labelFr) }
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyCatalogCard(
                            title = "Aucune commande",
                            subtitle = "Les nouvelles commandes clients apparaîtront ici en temps réel."
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${order.orderNumber} • ${order.customerName}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${order.customerPhone} • ${formatTimestamp(order.createdAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    CdfPriceText(amount = order.totalCdf)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OrderStatusBadge(order.parsedStatus)
                                    PaymentStatusBadge(order.parsedPaymentStatus)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    WhatsappStatusBadge(
                                        status = order.parsedWhatsappStatus,
                                        labelPrefix = "WA Admin"
                                    )
                                    WhatsappStatusBadge(
                                        status = order.parsedCustomerWhatsappStatus,
                                        labelPrefix = "WA Client"
                                    )
                                }
                                Text(
                                    text = "${order.parsedFulfillment.labelFr} : ${order.districtName} (${order.deliveryAddress})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (order.assignedDriverName.isNotBlank()) {
                                    Text(
                                        text = "Livreur : ${order.assignedDriverName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                val orderReview = reviewsByOrderId[order.id]
                                if (orderReview != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Avis client • Produits", style = MaterialTheme.typography.labelMedium)
                                                StarRatingBar(rating = orderReview.productRating, starSize = 14.dp)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Qualité livraison", style = MaterialTheme.typography.labelMedium)
                                                StarRatingBar(rating = orderReview.deliveryRating, starSize = 14.dp)
                                            }
                                            if (orderReview.comment.isNotBlank()) {
                                                Text(
                                                    text = "« ${orderReview.comment} »",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                Button(
                                    onClick = { managingOrder = order },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Gérer / Assigner")
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
private fun AdminOrderManageDialog(
    order: Order,
    drivers: List<UserProfile>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (OrderStatus, UserProfile?, PaymentStatus, String) -> Unit
) {
    val context = LocalContext.current
    var selectedStatus by remember { mutableStateOf(order.parsedStatus) }
    var selectedPaymentStatus by remember { mutableStateOf(order.parsedPaymentStatus) }
    var selectedDriver by remember {
        mutableStateOf(drivers.firstOrNull { it.userId == order.assignedDriverId })
    }
    var notes by remember { mutableStateOf(order.notes) }
    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commande ${order.orderNumber}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${order.customerName} • ${order.customerPhone}", style = MaterialTheme.typography.titleSmall)
                        Text("${order.districtName} — ${order.deliveryAddress}", style = MaterialTheme.typography.bodySmall)
                        order.parsedItems().forEach { line ->
                            Text("• ${line.quantity}× ${line.productName} (${formatCdf(line.unitPriceCdf)})", style = MaterialTheme.typography.bodySmall)
                        }
                        if (order.parsedPaymentMethod == PaymentMethod.MOBILE_MONEY) {
                            Text(
                                text = "Mobile Money : ${order.paymentProvider.ifBlank { "Opérateur" }} (${order.paymentPhone}) ${if (order.paymentReference.isNotBlank()) "• Réf: ${order.paymentReference}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customerPhone}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Call, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Appeler le client")
                        }
                    }
                }

                Text("Statut de la commande", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(OrderStatus.entries) { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(status.labelFr) }
                        )
                    }
                }
                Text("Assigner un livreur", style = MaterialTheme.typography.labelLarge)
                if (drivers.isEmpty()) {
                    Text(
                        "Aucun livreur enregistré. Attribuez le rôle Livreur dans l'onglet Équipe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedDriver == null,
                                onClick = { selectedDriver = null },
                                label = { Text("Non assigné") }
                            )
                        }
                        items(drivers, key = { it.userId }) { drv ->
                            FilterChip(
                                selected = selectedDriver?.userId == drv.userId,
                                onClick = { selectedDriver = drv },
                                label = { Text(drv.displayName) }
                            )
                        }
                    }
                }
                Text("Statut paiement (${order.parsedPaymentMethod.labelFr})", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PaymentStatus.entries) { pStatus ->
                        FilterChip(
                            selected = selectedPaymentStatus == pStatus,
                            onClick = { selectedPaymentStatus = pStatus },
                            label = { Text(pStatus.labelFr) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note interne / instruction livreur") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedStatus, selectedDriver, selectedPaymentStatus, notes) },
                enabled = !isBusy
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

@Composable
fun AdminTeamAndIntegrationsTab(
    usersState: UiState<List<UserProfile>>,
    ordersState: UiState<List<Order>>,
    whatsappSettingsState: UiState<WhatsappIntegrationSettings?>,
    logsState: UiState<List<NotificationLog>>,
    isBusy: Boolean,
    viewModel: GoPrimeViewModel,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isDarkTheme: Boolean = false,
    onChangeThemeMode: (AppThemeMode) -> Unit = {}
) {
    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val orders = (ordersState as? UiState.Success)?.data.orEmpty()
    val settings = (whatsappSettingsState as? UiState.Success)?.data
    val logs = (logsState as? UiState.Success)?.data.orEmpty()

    val ordersByUser = remember(orders) { orders.groupBy { it.userId } }
    var userSearch by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf<UserRole?>(null) }

    var waEnabled by remember(settings?.enabled) { mutableStateOf(settings?.enabled ?: true) }
    var waNotifyCustomer by remember(settings?.notifyCustomerOnOrder) {
        mutableStateOf(settings?.notifyCustomerOnOrder ?: true)
    }
    var waPhonesInput by remember(settings?.adminWhatsappPhones) {
        mutableStateOf(
            settings?.adminWhatsappPhones
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: DEFAULT_ADMIN_WHATSAPP_PHONE
        )
    }
    var waTemplate by remember(settings?.templateName) {
        mutableStateOf(settings?.templateName?.ifBlank { "goprime_new_order" } ?: "goprime_new_order")
    }
    var waCustomerTemplate by remember(settings?.customerTemplateName) {
        mutableStateOf(settings?.customerTemplateName?.ifBlank { "goprime_order_confirmation" } ?: "goprime_order_confirmation")
    }
    var waLang by remember(settings?.languageCode) {
        mutableStateOf(settings?.languageCode?.ifBlank { "fr" } ?: "fr")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Apparence de l'application",
                subtitle = "Thème clair ou sombre sauvegardé localement"
            )
        }

        item {
            ThemeToggleCard(
                themeMode = themeMode,
                isDarkTheme = isDarkTheme,
                onChangeThemeMode = onChangeThemeMode
            )
        }

        item {
            SectionHeader(
                title = "WhatsApp Business & Paiements",
                subtitle = "Intégration Cloud Functions + Meta Graph API v21.0"
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "État d'intégration serveur (goprime-754cf)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Les triggers automatiques WhatsApp (onOrderCreatedNotifyAdmins & onOrderStatusUpdatedNotifyCustomer) informent l'administrateur et le client des détails de la transaction via Firebase Cloud Functions. Déployez functions/ avec les secrets WHATSAPP_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID et MOBILE_MONEY_WEBHOOK_SECRET avant mise en production.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Alertes WhatsApp commandes", style = MaterialTheme.typography.titleMedium)
                        }
                        Switch(checked = waEnabled, onCheckedChange = { waEnabled = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notifier aussi le client automatiquement", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Envoie les détails de la transaction au numéro client à la commande et lors des mises à jour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = waNotifyCustomer,
                            onCheckedChange = { waNotifyCustomer = it },
                            modifier = Modifier.testTag("whatsapp_notify_customer_switch")
                        )
                    }

                    OutlinedTextField(
                        value = waPhonesInput,
                        onValueChange = { waPhonesInput = it },
                        label = { Text("Numéros WhatsApp Admins (séparés par virgule)") },
                        placeholder = { Text(DEFAULT_ADMIN_WHATSAPP_PHONE) },
                        modifier = Modifier.fillMaxWidth().testTag("whatsapp_phones_input")
                    )
                    OutlinedTextField(
                        value = waTemplate,
                        onValueChange = { waTemplate = it },
                        label = { Text("Modèle Meta Administrateur") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = waCustomerTemplate,
                        onValueChange = { waCustomerTemplate = it },
                        label = { Text("Modèle Meta Confirmation Client") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("whatsapp_customer_template_input")
                    )
                    OutlinedTextField(
                        value = waLang,
                        onValueChange = { waLang = it },
                        label = { Text("Code langue (ex. fr)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val list = waPhonesInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            viewModel.saveWhatsappSettings(
                                enabled = waEnabled,
                                phones = list,
                                notifyCustomerOnOrder = waNotifyCustomer,
                                templateName = waTemplate,
                                customerTemplateName = waCustomerTemplate,
                                languageCode = waLang
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("save_whatsapp_settings_button")
                    ) {
                        Text("Enregistrer configuration WhatsApp")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val firstPhone = waPhonesInput.split(",").firstOrNull()?.trim()
                                viewModel.testWhatsappNotification(firstPhone, recipientRole = "ADMIN")
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("test_whatsapp_button")
                        ) {
                            Icon(Icons.Filled.Send, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test Admin")
                        }
                        OutlinedButton(
                            onClick = {
                                val firstPhone = waPhonesInput.split(",").firstOrNull()?.trim()
                                viewModel.testWhatsappNotification(firstPhone, recipientRole = "CLIENT")
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("test_whatsapp_client_button")
                        ) {
                            Icon(Icons.Filled.Send, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test Client")
                        }
                    }
                }
            }
        }

        if (logs.isNotEmpty()) {
            item {
                Text("Journaux d'envoi WhatsApp (${logs.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(logs.take(15), key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${log.orderNumber} (${log.recipientRole}) → ${log.recipient}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            StatusPill(
                                text = log.status,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(log.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            SectionHeader(
                title = "Clients, Livreurs & Rôles (${users.size})",
                subtitle = "Statistiques commandes, activation compte et permissions"
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = userSearch,
                    onValueChange = { userSearch = it },
                    label = { Text("Rechercher client, email ou téléphone...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = roleFilter == null,
                            onClick = { roleFilter = null },
                            label = { Text("Tous (${users.size})") }
                        )
                    }
                    items(UserRole.entries) { role ->
                        val count = users.count { it.parsedRole == role }
                        FilterChip(
                            selected = roleFilter == role,
                            onClick = { roleFilter = if (roleFilter == role) null else role },
                            label = { Text("${role.labelFr} ($count)") }
                        )
                    }
                }
            }
        }

        when (usersState) {
            UiState.Loading -> item { LoadingStateView("Chargement des comptes...") }
            is UiState.Error -> item { ErrorStateCard(usersState.message) }
            is UiState.Success -> {
                val filteredUsers = users.filter { u ->
                    val matchesRole = roleFilter == null || u.parsedRole == roleFilter
                    val q = userSearch.trim()
                    val matchesQ = q.isEmpty() ||
                        u.displayName.contains(q, ignoreCase = true) ||
                        u.email.contains(q, ignoreCase = true) ||
                        u.phone.contains(q, ignoreCase = true)
                    matchesRole && matchesQ
                }
                items(filteredUsers, key = { it.userId }) { u ->
                    val userOrders = ordersByUser[u.userId].orEmpty()
                    val userSpentCdf = userOrders
                        .filter { it.parsedOrderStatusNotCancelled() }
                        .sumOf { it.totalCdf }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ManageAccounts, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${u.email}${if (u.phone.isNotBlank()) " • ${u.phone}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${userOrders.size} cmd • ${formatCdf(userSpentCdf)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Switch(
                                    checked = u.active,
                                    onCheckedChange = { checked ->
                                        viewModel.adminSetUserActive(u.userId, checked)
                                    },
                                    enabled = !isBusy
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UserRole.entries.forEach { role ->
                                    FilterChip(
                                        selected = u.parsedRole == role,
                                        onClick = {
                                            if (u.parsedRole != role) {
                                                viewModel.adminUpdateUserRole(u, role)
                                            }
                                        },
                                        label = { Text(role.labelFr) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Order.parsedOrderStatusNotCancelled(): Boolean =
    parsedStatus != OrderStatus.CANCELLED && parsedStatus != OrderStatus.REJECTED
