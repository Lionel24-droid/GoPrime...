package com.example.ui.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.firestore.GoPrimeRepository
import com.example.data.model.DeliveryStatusCategory
import com.example.data.model.OrderStatus
import com.example.data.model.UserRole
import com.example.data.preferences.AppThemeMode
import com.example.ui.components.SpaceSwitcherBar
import com.example.ui.screens.AdminCatalogTab
import com.example.ui.screens.AdminDashboardTab
import com.example.ui.screens.AdminOrdersTab
import com.example.ui.screens.AdminTeamAndIntegrationsTab
import com.example.ui.screens.AdminZonesTab
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClientCartAndCheckoutScreen
import com.example.ui.screens.ClientCatalogScreen
import com.example.ui.screens.ClientOrdersScreen
import com.example.ui.screens.ClientProfileScreen
import com.example.ui.screens.DriverSpaceScreen
import com.example.ui.state.ActiveSpace
import com.example.ui.state.GoPrimeViewModel
import com.example.ui.state.UiState
import com.example.ui.theme.goPrimeBackgroundBrush
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class ClientTab(val label: String, val icon: ImageVector) {
    CATALOG("Catalogue", Icons.Filled.Storefront),
    CART("Panier", Icons.Filled.ShoppingCart),
    ORDERS("Commandes", Icons.AutoMirrored.Filled.ListAlt),
    PROFILE("Profil", Icons.Filled.Person)
}

private enum class AdminTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Tableau", Icons.Filled.Dashboard),
    CATALOG("Catalogue", Icons.Filled.Inventory2),
    ZONES("Zones", Icons.Filled.Map),
    ORDERS("Commandes", Icons.AutoMirrored.Filled.ListAlt),
    INTEGRATIONS("Équipe & WA", Icons.Filled.Notifications)
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isDarkTheme: Boolean = false,
    onChangeThemeMode: (AppThemeMode) -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            GoPrimeMainShell(
                currentUserId = user.uid,
                themeMode = themeMode,
                isDarkTheme = isDarkTheme,
                onChangeThemeMode = onChangeThemeMode
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoPrimeMainShell(
    currentUserId: String,
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onChangeThemeMode: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: GoPrimeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                GoPrimeViewModel(GoPrimeRepository(db), currentUserId)
            }
        }
    )

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val activeSpace by viewModel.activeSpace.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val categoriesState by viewModel.activeCategoriesState.collectAsStateWithLifecycle()
    val productsState by viewModel.activeProductsState.collectAsStateWithLifecycle()
    val zonesState by viewModel.activeZonesState.collectAsStateWithLifecycle()
    val cartState by viewModel.cartState.collectAsStateWithLifecycle()
    val customerOrdersState by viewModel.customerOrdersState.collectAsStateWithLifecycle()
    val reviewsState by viewModel.reviewsState.collectAsStateWithLifecycle()
    val driverOrdersState by viewModel.driverOrdersState.collectAsStateWithLifecycle()

    val adminCategoriesState by viewModel.adminCategoriesState.collectAsStateWithLifecycle()
    val adminProductsState by viewModel.adminProductsState.collectAsStateWithLifecycle()
    val adminZonesState by viewModel.adminZonesState.collectAsStateWithLifecycle()
    val adminOrdersState by viewModel.adminOrdersState.collectAsStateWithLifecycle()
    val adminUsersState by viewModel.adminUsersState.collectAsStateWithLifecycle()
    val whatsappSettingsState by viewModel.whatsappSettingsState.collectAsStateWithLifecycle()
    val notificationLogsState by viewModel.notificationLogsState.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val userRole = userProfile?.parsedRole ?: UserRole.CLIENT
    val effectiveSpace = GoPrimeViewModel.clampSpaceForRole(userRole, activeSpace)
    val cartCount by viewModel.cartItemCount.collectAsStateWithLifecycle()
    val activeClientOrdersCount = ((customerOrdersState as? UiState.Success)?.data.orEmpty())
        .count { it.deliveryStatusCategory == DeliveryStatusCategory.IN_PROGRESS }
    val pendingAdminOrdersCount = ((adminOrdersState as? UiState.Success)?.data.orEmpty())
        .count { it.parsedStatus == OrderStatus.PENDING }
    val activeDriverMissionsCount = ((driverOrdersState as? UiState.Success)?.data.orEmpty())
        .count { !it.parsedStatus.isTerminal }

    var clientTab by rememberSaveable { mutableStateOf(ClientTab.CATALOG) }
    var adminTab by rememberSaveable { mutableStateOf(AdminTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    LaunchedEffect(userRole, activeSpace) {
        val clamped = GoPrimeViewModel.clampSpaceForRole(userRole, activeSpace)
        if (activeSpace != clamped) {
            viewModel.switchSpace(clamped)
        }
    }

    val density = LocalDensity.current
    var containerWidthDp by remember { mutableStateOf(0.dp) }
    val useRail = containerWidthDp >= 600.dp

    val brandBackground = goPrimeBackgroundBrush()

    if (userProfile != null && !userProfile.active) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brandBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 460.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = "Compte désactivé",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Compte désactivé",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Votre compte client (${userProfile.email}) a été suspendu par l'administrateur GoPrime.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { signOutUser(credentialManager, scope) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("suspended_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Se déconnecter")
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandBackground)
            .onSizeChanged { size ->
                containerWidthDp = with(density) { size.width.toDp() }
            }
    ) {

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = R.drawable.img_goprime_icon,
                                contentDescription = "Logo GoPrime",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "GoPrime",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Espace ${effectiveSpace.labelFr}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (effectiveSpace == ActiveSpace.CLIENT) {
                            androidx.compose.material3.IconButton(
                                onClick = { clientTab = ClientTab.CART },
                                modifier = Modifier.testTag("topbar_cart_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = if (cartCount > 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            contentColor = if (cartCount > 0) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.testTag("topbar_cart_badge")
                                        ) {
                                            Text("$cartCount")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ShoppingCart,
                                        contentDescription = "Panier : $cartCount article(s)",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    GoPrimeBottomBar(
                        activeSpace = effectiveSpace,
                        clientTab = clientTab,
                        adminTab = adminTab,
                        cartCount = cartCount,
                        activeClientOrdersCount = activeClientOrdersCount,
                        pendingAdminOrdersCount = pendingAdminOrdersCount,
                        activeDriverMissionsCount = activeDriverMissionsCount,
                        onSelectClientTab = { clientTab = it },
                        onSelectAdminTab = { adminTab = it }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    GoPrimeNavRail(
                        activeSpace = effectiveSpace,
                        clientTab = clientTab,
                        adminTab = adminTab,
                        cartCount = cartCount,
                        activeClientOrdersCount = activeClientOrdersCount,
                        pendingAdminOrdersCount = pendingAdminOrdersCount,
                        activeDriverMissionsCount = activeDriverMissionsCount,
                        onSelectClientTab = { clientTab = it },
                        onSelectAdminTab = { adminTab = it }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        SpaceSwitcherBar(
                            role = userRole,
                            activeSpace = effectiveSpace,
                            onSelectSpace = { viewModel.switchSpace(it) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (effectiveSpace) {
                                ActiveSpace.CLIENT -> when (clientTab) {
                                    ClientTab.CATALOG -> ClientCatalogScreen(
                                        categoriesState = categoriesState,
                                        productsState = productsState,
                                        cartState = cartState,
                                        reviewsState = reviewsState,
                                        userProfile = userProfile,
                                        viewModel = viewModel,
                                        onOpenCart = { clientTab = ClientTab.CART }
                                    )
                                    ClientTab.CART -> ClientCartAndCheckoutScreen(
                                        cartState = cartState,
                                        productsState = productsState,
                                        zonesState = zonesState,
                                        userProfile = userProfile,
                                        isBusy = isBusy,
                                        viewModel = viewModel,
                                        onOrderPlacedNavigate = { clientTab = ClientTab.ORDERS }
                                    )
                                    ClientTab.ORDERS -> ClientOrdersScreen(
                                        ordersState = customerOrdersState,
                                        reviewsState = reviewsState,
                                        isBusy = isBusy,
                                        viewModel = viewModel,
                                        onBrowseCatalog = { clientTab = ClientTab.CATALOG }
                                    )
                                    ClientTab.PROFILE -> ClientProfileScreen(
                                        userProfile = userProfile,
                                        zonesState = zonesState,
                                        isBusy = isBusy,
                                        viewModel = viewModel,
                                        themeMode = themeMode,
                                        isDarkTheme = isDarkTheme,
                                        onChangeThemeMode = onChangeThemeMode,
                                        onSignOut = {
                                            signOutUser(credentialManager, scope)
                                        }
                                    )
                                }
                                ActiveSpace.DRIVER -> DriverSpaceScreen(
                                    driverOrdersState = driverOrdersState,
                                    isBusy = isBusy,
                                    viewModel = viewModel
                                )
                                ActiveSpace.ADMIN -> when (adminTab) {
                                    AdminTab.DASHBOARD -> AdminDashboardTab(
                                        ordersState = adminOrdersState,
                                        productsState = adminProductsState,
                                        usersState = adminUsersState,
                                        zonesState = adminZonesState,
                                        onNavigateOrders = { adminTab = AdminTab.ORDERS },
                                        onNavigateCatalog = { adminTab = AdminTab.CATALOG },
                                        onNavigateIntegrations = { adminTab = AdminTab.INTEGRATIONS }
                                    )
                                    AdminTab.CATALOG -> AdminCatalogTab(
                                        categoriesState = adminCategoriesState,
                                        productsState = adminProductsState,
                                        isBusy = isBusy,
                                        viewModel = viewModel
                                    )
                                    AdminTab.ZONES -> AdminZonesTab(
                                        zonesState = adminZonesState,
                                        isBusy = isBusy,
                                        viewModel = viewModel
                                    )
                                    AdminTab.ORDERS -> AdminOrdersTab(
                                        ordersState = adminOrdersState,
                                        usersState = adminUsersState,
                                        reviewsState = reviewsState,
                                        isBusy = isBusy,
                                        viewModel = viewModel
                                    )
                                    AdminTab.INTEGRATIONS -> AdminTeamAndIntegrationsTab(
                                        usersState = adminUsersState,
                                        ordersState = adminOrdersState,
                                        whatsappSettingsState = whatsappSettingsState,
                                        logsState = notificationLogsState,
                                        isBusy = isBusy,
                                        viewModel = viewModel,
                                        themeMode = themeMode,
                                        isDarkTheme = isDarkTheme,
                                        onChangeThemeMode = onChangeThemeMode
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

@Composable
private fun GoPrimeBottomBar(
    activeSpace: ActiveSpace,
    clientTab: ClientTab,
    adminTab: AdminTab,
    cartCount: Int,
    activeClientOrdersCount: Int,
    pendingAdminOrdersCount: Int,
    activeDriverMissionsCount: Int,
    onSelectClientTab: (ClientTab) -> Unit,
    onSelectAdminTab: (AdminTab) -> Unit
) {
    val barSurfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    when (activeSpace) {
        ActiveSpace.CLIENT -> NavigationBar(containerColor = barSurfaceColor) {
            ClientTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = clientTab == tab,
                    onClick = { onSelectClientTab(tab) },
                    icon = {
                        val badgeCount = when (tab) {
                            ClientTab.CART -> cartCount
                            ClientTab.ORDERS -> activeClientOrdersCount
                            else -> 0
                        }
                        if (badgeCount > 0) {
                            BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        } else {
                            Icon(tab.icon, contentDescription = tab.label)
                        }
                    },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("nav_client_${tab.name.lowercase()}")
                )
            }
        }
        ActiveSpace.ADMIN -> NavigationBar(containerColor = barSurfaceColor) {
            AdminTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = adminTab == tab,
                    onClick = { onSelectAdminTab(tab) },
                    icon = {
                        if (tab == AdminTab.ORDERS && pendingAdminOrdersCount > 0) {
                            BadgedBox(badge = { Badge { Text("$pendingAdminOrdersCount") } }) {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        } else {
                            Icon(tab.icon, contentDescription = tab.label)
                        }
                    },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("nav_admin_${tab.name.lowercase()}")
                )
            }
        }
        ActiveSpace.DRIVER -> NavigationBar(containerColor = barSurfaceColor) {
            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = {
                    if (activeDriverMissionsCount > 0) {
                        BadgedBox(badge = { Badge { Text("$activeDriverMissionsCount") } }) {
                            Icon(Icons.Filled.DeliveryDining, contentDescription = "Missions")
                        }
                    } else {
                        Icon(Icons.Filled.DeliveryDining, contentDescription = "Missions")
                    }
                },
                label = { Text("Missions") }
            )
        }
    }
}

@Composable
private fun GoPrimeNavRail(
    activeSpace: ActiveSpace,
    clientTab: ClientTab,
    adminTab: AdminTab,
    cartCount: Int,
    activeClientOrdersCount: Int,
    pendingAdminOrdersCount: Int,
    activeDriverMissionsCount: Int,
    onSelectClientTab: (ClientTab) -> Unit,
    onSelectAdminTab: (AdminTab) -> Unit
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
        when (activeSpace) {
            ActiveSpace.CLIENT -> ClientTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = clientTab == tab,
                    onClick = { onSelectClientTab(tab) },
                    icon = {
                        val badgeCount = when (tab) {
                            ClientTab.CART -> cartCount
                            ClientTab.ORDERS -> activeClientOrdersCount
                            else -> 0
                        }
                        if (badgeCount > 0) {
                            BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        } else {
                            Icon(tab.icon, contentDescription = tab.label)
                        }
                    },
                    label = { Text(tab.label) }
                )
            }
            ActiveSpace.ADMIN -> AdminTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = adminTab == tab,
                    onClick = { onSelectAdminTab(tab) },
                    icon = {
                        if (tab == AdminTab.ORDERS && pendingAdminOrdersCount > 0) {
                            BadgedBox(badge = { Badge { Text("$pendingAdminOrdersCount") } }) {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        } else {
                            Icon(tab.icon, contentDescription = tab.label)
                        }
                    },
                    label = { Text(tab.label) }
                )
            }
            ActiveSpace.DRIVER -> NavigationRailItem(
                selected = true,
                onClick = {},
                icon = {
                    if (activeDriverMissionsCount > 0) {
                        BadgedBox(badge = { Badge { Text("$activeDriverMissionsCount") } }) {
                            Icon(Icons.Filled.DeliveryDining, contentDescription = "Missions")
                        }
                    } else {
                        Icon(Icons.Filled.DeliveryDining, contentDescription = "Missions")
                    }
                },
                label = { Text("Missions") }
            )
        }
    }
}
