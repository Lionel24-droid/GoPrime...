package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.ui.components.CdfPriceText
import com.example.ui.components.EmptyCatalogCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.formatCdf
import com.example.ui.components.formatTimestamp
import com.example.ui.state.GoPrimeViewModel
import com.example.ui.state.UiState

private enum class DriverMissionFilter(val label: String) {
    ACTIVE("Actives"),
    COMPLETED("Terminées"),
    ALL("Toutes")
}

@Composable
fun DriverSpaceScreen(
    driverOrdersState: UiState<List<Order>>,
    isBusy: Boolean,
    viewModel: GoPrimeViewModel
) {
    val context = LocalContext.current
    var missionFilter by remember { mutableStateOf(DriverMissionFilter.ACTIVE) }

    when (driverOrdersState) {
        UiState.Loading -> LoadingStateView("Chargement de vos missions...")
        is UiState.Error -> ErrorStateCard(driverOrdersState.message, Modifier.padding(16.dp))
        is UiState.Success -> {
            val orders = driverOrdersState.data
            if (orders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyCatalogCard(
                        title = "Aucune mission assignée",
                        subtitle = "Dès qu'un administrateur vous assigne une livraison, les coordonnées client, l'adresse et le montant CDF apparaîtront ici."
                    )
                }
            } else {
                val activeOrders = orders.filter { !it.parsedStatus.isTerminal }
                val completedOrders = orders.filter { it.parsedStatus.isTerminal }
                val cashToCollectCdf = activeOrders
                    .filter { it.parsedPaymentMethod == PaymentMethod.CASH && it.parsedPaymentStatus != PaymentStatus.PAID }
                    .sumOf { it.totalCdf }
                val filteredOrders = when (missionFilter) {
                    DriverMissionFilter.ACTIVE -> activeOrders
                    DriverMissionFilter.COMPLETED -> completedOrders
                    DriverMissionFilter.ALL -> orders
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SectionHeader(
                            title = "Missions de livraison",
                            subtitle = "${activeOrders.size} course(s) active(s) • ${orders.size} total"
                        )
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Espèces à encaisser",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${activeOrders.size} livraison(s) en attente",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                CdfPriceText(
                                    amount = cashToCollectCdf,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DriverMissionFilter.entries) { option ->
                                val count = when (option) {
                                    DriverMissionFilter.ACTIVE -> activeOrders.size
                                    DriverMissionFilter.COMPLETED -> completedOrders.size
                                    DriverMissionFilter.ALL -> orders.size
                                }
                                FilterChip(
                                    selected = missionFilter == option,
                                    onClick = { missionFilter = option },
                                    label = { Text("${option.label} ($count)") }
                                )
                            }
                        }
                    }
                    if (filteredOrders.isEmpty()) {
                        item {
                            EmptyCatalogCard(
                                title = "Aucune mission dans cette catégorie",
                                subtitle = "Utilisez les filtres ci-dessus pour voir l'historique complet de vos livraisons."
                            )
                        }
                    } else {
                        items(filteredOrders, key = { it.id }) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
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
                                    Column {
                                        Text(order.orderNumber, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            formatTimestamp(order.createdAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OrderStatusBadge(order.parsedStatus)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text("${order.districtName} — ${order.customerName}", style = MaterialTheme.typography.titleSmall)
                                        Text(order.deliveryAddress, style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PaymentStatusBadge(order.parsedPaymentStatus)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (order.parsedPaymentMethod == PaymentMethod.CASH) {
                                                "À encaisser (Espèces)"
                                            } else {
                                                "Mobile Money"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        CdfPriceText(amount = order.totalCdf)
                                    }
                                }

                                HorizontalDivider()

                                order.parsedItems().forEach { line ->
                                    Text(
                                        text = "• ${line.quantity}× ${line.productName} (${formatCdf(line.unitPriceCdf)})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                if (order.notes.isNotBlank()) {
                                    Text(
                                        text = "Note client : ${order.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customerPhone}"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Call, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Appeler")
                                    }

                                    when (order.parsedStatus) {
                                        OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP -> {
                                            Button(
                                                onClick = { viewModel.driverStartDelivery(order.id) },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("driver_start_${order.id}")
                                            ) {
                                                Icon(Icons.Filled.LocalShipping, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Démarrer")
                                            }
                                        }
                                        OrderStatus.OUT_FOR_DELIVERY -> {
                                            Button(
                                                onClick = { viewModel.driverCompleteDelivery(order) },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("driver_complete_${order.id}")
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Livré")
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
