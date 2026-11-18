package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.DeliveryStatusCategory
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.UserRole
import com.example.data.model.WhatsappDispatchStatus
import com.example.data.preferences.AppThemeMode
import com.example.ui.state.ActiveSpace
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusDangerBg
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

fun formatCdf(amount: Long): String {
    val formatter = NumberFormat.getIntegerInstance(Locale.FRANCE)
    return "${formatter.format(amount)} CDF"
}

fun formatTimestamp(timestamp: Timestamp?): String {
    val date = (timestamp ?: Timestamp.now()).toDate()
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.FRENCH)
    return sdf.format(date)
}

@Composable
fun SpaceSwitcherBar(
    role: UserRole,
    activeSpace: ActiveSpace,
    onSelectSpace: (ActiveSpace) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableSpaces = com.example.ui.state.GoPrimeViewModel.allowedSpacesFor(role)
    if (availableSpaces.size <= 1) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            availableSpaces.forEach { space ->
                val selected = space == activeSpace
                val icon = when (space) {
                    ActiveSpace.CLIENT -> Icons.Filled.ShoppingBag
                    ActiveSpace.DRIVER -> Icons.Filled.LocalShipping
                    ActiveSpace.ADMIN -> Icons.Filled.AdminPanelSettings
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectSpace(space) }
                        .testTag("space_tab_${space.name.lowercase()}"),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = space.labelFr,
                            style = MaterialTheme.typography.labelLarge,
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
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun OrderStatusBadge(status: OrderStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        OrderStatus.PENDING -> StatusWarningBg to StatusWarning
        OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP -> StatusInfoBg to StatusInfo
        OrderStatus.OUT_FOR_DELIVERY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        OrderStatus.COMPLETED -> StatusSuccessBg to StatusSuccess
        OrderStatus.REJECTED, OrderStatus.CANCELLED -> StatusDangerBg to StatusDanger
    }
    StatusPill(text = status.labelFr, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun DeliveryStatusBadge(
    category: DeliveryStatusCategory,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (category) {
        DeliveryStatusCategory.IN_PROGRESS -> Triple(StatusInfoBg, StatusInfo, Icons.Filled.LocalShipping)
        DeliveryStatusCategory.DELIVERED -> Triple(StatusSuccessBg, StatusSuccess, Icons.Filled.CheckCircle)
        DeliveryStatusCategory.CANCELLED -> Triple(StatusDangerBg, StatusDanger, Icons.Filled.Cancel)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = category.labelFr,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PaymentStatusBadge(status: PaymentStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        PaymentStatus.PAID -> StatusSuccessBg to StatusSuccess
        PaymentStatus.PENDING_VERIFICATION, PaymentStatus.PENDING -> StatusWarningBg to StatusWarning
        PaymentStatus.FAILED -> StatusDangerBg to StatusDanger
    }
    StatusPill(text = status.labelFr, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun WhatsappStatusBadge(
    status: WhatsappDispatchStatus,
    modifier: Modifier = Modifier,
    labelPrefix: String = "WhatsApp"
) {
    val (bg, fg) = when (status) {
        WhatsappDispatchStatus.SENT -> StatusSuccessBg to StatusSuccess
        WhatsappDispatchStatus.PENDING -> StatusInfoBg to StatusInfo
        WhatsappDispatchStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        WhatsappDispatchStatus.FAILED -> StatusDangerBg to StatusDanger
    }
    StatusPill(
        text = "$labelPrefix : ${status.labelFr}",
        containerColor = bg,
        contentColor = fg,
        modifier = modifier
    )
}

@Composable
fun CdfPriceText(
    amount: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = formatCdf(amount),
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold
        ),
        color = color,
        modifier = modifier
    )
}

@Composable
fun LoadingStateView(label: String = "Chargement en cours...") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorStateCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Erreur",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun EmptyCatalogCard(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = R.drawable.img_empty_catalog,
                contentDescription = title,
                modifier = Modifier
                    .height(140.dp)
                    .fillMaxWidth(0.7f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.testTag("empty_state_action_button")
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun AvatarBadge(name: String, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun OrderProgressTimeline(
    status: OrderStatus,
    isPickup: Boolean,
    modifier: Modifier = Modifier
) {
    if (status == OrderStatus.REJECTED || status == OrderStatus.CANCELLED) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = StatusDangerBg
        ) {
            Text(
                text = if (status == OrderStatus.REJECTED) {
                    "Commande refusée par l'administrateur."
                } else {
                    "Commande annulée."
                },
                style = MaterialTheme.typography.bodySmall,
                color = StatusDanger,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        return
    }

    val steps = if (isPickup) {
        listOf(
            OrderStatus.PENDING to "Reçue",
            OrderStatus.ACCEPTED to "Acceptée",
            OrderStatus.PREPARING to "Prépa",
            OrderStatus.READY_FOR_PICKUP to "Prête",
            OrderStatus.COMPLETED to "Retirée"
        )
    } else {
        listOf(
            OrderStatus.PENDING to "Reçue",
            OrderStatus.ACCEPTED to "Acceptée",
            OrderStatus.PREPARING to "Prépa",
            OrderStatus.OUT_FOR_DELIVERY to "En route",
            OrderStatus.COMPLETED to "Livrée"
        )
    }

    val currentIndex = when (status) {
        OrderStatus.PENDING -> 0
        OrderStatus.ACCEPTED -> 1
        OrderStatus.PREPARING -> 2
        OrderStatus.READY_FOR_PICKUP -> 3
        OrderStatus.OUT_FOR_DELIVERY -> 3
        OrderStatus.COMPLETED -> 4
        else -> 0
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { idx, (_, label) ->
            val active = idx <= currentIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StarRatingBar(
    rating: Int,
    onRatingChanged: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    starSize: Dp = 20.dp
) {
    val starColor = StatusWarning
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (onRatingChanged != null) 2.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..5).forEach { index ->
            val filled = index <= rating
            if (onRatingChanged != null) {
                IconButton(
                    onClick = { onRatingChanged(index) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("star_rating_$index")
                ) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "$index étoile(s) sur 5",
                        tint = if (filled) starColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(starSize)
                    )
                }
            } else {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (filled) starColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(starSize)
                )
            }
        }
    }
}

@Composable
fun ThemeToggleCard(
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onChangeThemeMode: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Apparence (Clair / Sombre)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isDarkTheme) "Mode sombre activé" else "Mode clair activé",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { checked ->
                        onChangeThemeMode(if (checked) AppThemeMode.DARK else AppThemeMode.LIGHT)
                    },
                    modifier = Modifier.testTag("theme_dark_mode_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onChangeThemeMode(mode) },
                        label = { Text(mode.labelFr) },
                        modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }
    }
}

