package com.example

import com.example.data.model.CartItem
import com.example.data.model.DEFAULT_ADMIN_WHATSAPP_PHONE
import com.example.data.model.DeliveryStatusCategory
import com.example.data.model.FulfillmentMode
import com.example.data.model.Order
import com.example.data.model.OrderReview
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.UserRole
import com.example.data.model.WhatsappIntegrationSettings
import com.example.data.preferences.AppThemeMode
import com.example.ui.components.formatCdf
import com.example.ui.state.ActiveSpace
import com.example.ui.state.CatalogSortOption
import com.example.ui.state.GoPrimeViewModel
import com.example.util.WhatsAppIntentService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoPrimeModelsTest {

    @Test
    fun `safe enum deserialization falls back gracefully on unknown values`() {
        assertEquals(UserRole.CLIENT, UserRole.fromRaw("UNKNOWN_ROLE"))
        assertEquals(UserRole.ADMIN, UserRole.fromRaw("ADMIN"))
        assertEquals(OrderStatus.PENDING, OrderStatus.fromRaw(null))
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.fromRaw("OUT_FOR_DELIVERY"))
        assertEquals(PaymentMethod.CASH, PaymentMethod.fromRaw("BITCOIN"))
        assertEquals(PaymentStatus.PENDING, PaymentStatus.fromRaw("INVALID"))
        assertEquals(FulfillmentMode.DELIVERY, FulfillmentMode.fromRaw("UNKNOWN"))
    }

    @Test
    fun `order status terminal check matches workflow`() {
        assertTrue(OrderStatus.COMPLETED.isTerminal)
        assertTrue(OrderStatus.REJECTED.isTerminal)
        assertTrue(OrderStatus.CANCELLED.isTerminal)
        assertFalse(OrderStatus.PENDING.isTerminal)
        assertFalse(OrderStatus.ACCEPTED.isTerminal)
        assertFalse(OrderStatus.OUT_FOR_DELIVERY.isTerminal)
    }

    @Test
    fun `order parsedItems handles Int Long and Double numeric types safely`() {
        val order = Order(
            id = "ord_1",
            orderNumber = "GP-123456",
            items = listOf(
                mapOf(
                    "productId" to "prod_1",
                    "productName" to "Riz 5kg",
                    "unitPriceCdf" to 28000,
                    "quantity" to 2L
                ),
                mapOf(
                    "productId" to "prod_2",
                    "productName" to "Huile 1L",
                    "unitPriceCdf" to 9500.0,
                    "quantity" to 3
                )
            )
        )
        val parsed = order.parsedItems()
        assertEquals(2, parsed.size)
        assertEquals(28000L, parsed[0].unitPriceCdf)
        assertEquals(2L, parsed[0].quantity)
        assertEquals(9500L, parsed[1].unitPriceCdf)
        assertEquals(3L, parsed[1].quantity)
    }

    @Test
    fun `cart item lineTotalCdf and CDF formatting are accurate`() {
        val item = CartItem(
            productId = "p1",
            productName = "Sucre",
            unitPriceCdf = 4500L,
            quantity = 3L
        )
        assertEquals(13500L, item.lineTotalCdf)
        assertTrue(formatCdf(13500L).endsWith("CDF"))
    }

    @Test
    fun `client role is strictly isolated from admin and driver spaces`() {
        assertEquals(listOf(ActiveSpace.CLIENT), GoPrimeViewModel.allowedSpacesFor(UserRole.CLIENT))
        assertEquals(ActiveSpace.CLIENT, GoPrimeViewModel.clampSpaceForRole(UserRole.CLIENT, ActiveSpace.ADMIN))
        assertEquals(ActiveSpace.CLIENT, GoPrimeViewModel.clampSpaceForRole(UserRole.CLIENT, ActiveSpace.DRIVER))
        assertEquals(ActiveSpace.CLIENT, GoPrimeViewModel.clampSpaceForRole(UserRole.CLIENT, ActiveSpace.CLIENT))
    }

    @Test
    fun `driver cannot access admin space while admin can access all spaces`() {
        assertEquals(
            listOf(ActiveSpace.CLIENT, ActiveSpace.DRIVER),
            GoPrimeViewModel.allowedSpacesFor(UserRole.DRIVER)
        )
        assertEquals(ActiveSpace.CLIENT, GoPrimeViewModel.clampSpaceForRole(UserRole.DRIVER, ActiveSpace.ADMIN))
        assertEquals(ActiveSpace.DRIVER, GoPrimeViewModel.clampSpaceForRole(UserRole.DRIVER, ActiveSpace.DRIVER))
        assertEquals(ActiveSpace.ADMIN, GoPrimeViewModel.clampSpaceForRole(UserRole.ADMIN, ActiveSpace.ADMIN))
    }

    @Test
    fun `catalog dynamic category filter and sorting work accurately`() {
        val products = listOf(
            Product(id = "p1", name = "Riz 5kg", categoryId = "cat_food", categoryName = "Alimentation", priceCdf = 28000L, featured = false),
            Product(id = "p2", name = "Eau Minérale", categoryId = "cat_drinks", categoryName = "Boissons", priceCdf = 2500L, featured = true),
            Product(id = "p3", name = "Huile 1L", categoryId = "cat_food", categoryName = "Alimentation", priceCdf = 9500L, featured = true)
        )

        val foodAsc = GoPrimeViewModel.filterAndSortProducts(
            products = products,
            selectedCategoryId = "cat_food",
            featuredOnly = false,
            searchQuery = "",
            sortOption = CatalogSortOption.PRICE_ASC
        )
        assertEquals(listOf("p3", "p1"), foodAsc.map { it.id })

        val featuredDesc = GoPrimeViewModel.filterAndSortProducts(
            products = products,
            selectedCategoryId = null,
            featuredOnly = true,
            searchQuery = "",
            sortOption = CatalogSortOption.PRICE_DESC
        )
        assertEquals(listOf("p3", "p2"), featuredDesc.map { it.id })

        val searched = GoPrimeViewModel.filterAndSortProducts(
            products = products,
            selectedCategoryId = null,
            featuredOnly = false,
            searchQuery = "boissons",
            sortOption = CatalogSortOption.NAME_ASC
        )
        assertEquals(listOf("p2"), searched.map { it.id })
    }

    @Test
    fun `delivery status category maps all order statuses to En cours Livre Annule`() {
        assertEquals(DeliveryStatusCategory.IN_PROGRESS, DeliveryStatusCategory.fromOrderStatus(OrderStatus.PENDING))
        assertEquals(DeliveryStatusCategory.IN_PROGRESS, DeliveryStatusCategory.fromOrderStatus(OrderStatus.ACCEPTED))
        assertEquals(DeliveryStatusCategory.IN_PROGRESS, DeliveryStatusCategory.fromOrderStatus(OrderStatus.PREPARING))
        assertEquals(DeliveryStatusCategory.IN_PROGRESS, DeliveryStatusCategory.fromOrderStatus(OrderStatus.READY_FOR_PICKUP))
        assertEquals(DeliveryStatusCategory.IN_PROGRESS, DeliveryStatusCategory.fromOrderStatus(OrderStatus.OUT_FOR_DELIVERY))
        assertEquals(DeliveryStatusCategory.DELIVERED, DeliveryStatusCategory.fromOrderStatus(OrderStatus.COMPLETED))
        assertEquals(DeliveryStatusCategory.CANCELLED, DeliveryStatusCategory.fromOrderStatus(OrderStatus.CANCELLED))
        assertEquals(DeliveryStatusCategory.CANCELLED, DeliveryStatusCategory.fromOrderStatus(OrderStatus.REJECTED))
    }

    @Test
    fun `client order filter filters by delivery status category and search query`() {
        val orders = listOf(
            Order(
                id = "o1",
                orderNumber = "GP-100001",
                districtName = "Gombe",
                status = OrderStatus.OUT_FOR_DELIVERY.name,
                items = listOf(mapOf("productName" to "Eau Minérale", "unitPriceCdf" to 2500, "quantity" to 2))
            ),
            Order(
                id = "o2",
                orderNumber = "GP-100002",
                districtName = "Ngaliema",
                status = OrderStatus.COMPLETED.name,
                items = listOf(mapOf("productName" to "Riz 5kg", "unitPriceCdf" to 28000, "quantity" to 1))
            ),
            Order(
                id = "o3",
                orderNumber = "GP-100003",
                districtName = "Limete",
                status = OrderStatus.CANCELLED.name,
                items = listOf(mapOf("productName" to "Huile 1L", "unitPriceCdf" to 9500, "quantity" to 1))
            )
        )

        assertEquals(
            listOf("o1"),
            GoPrimeViewModel.filterOrders(orders, DeliveryStatusCategory.IN_PROGRESS).map { it.id }
        )
        assertEquals(
            listOf("o2"),
            GoPrimeViewModel.filterOrders(orders, DeliveryStatusCategory.DELIVERED).map { it.id }
        )
        assertEquals(
            listOf("o3"),
            GoPrimeViewModel.filterOrders(orders, DeliveryStatusCategory.CANCELLED).map { it.id }
        )
        assertEquals(
            listOf("o2"),
            GoPrimeViewModel.filterOrders(orders, null, "Riz").map { it.id }
        )
        assertEquals(
            listOf("o1"),
            GoPrimeViewModel.filterOrders(orders, null, "GP-100001").map { it.id }
        )
    }

    @Test
    fun `mergeLocalCartOverlay updates adds and removes items optimistically`() {
        val remote = listOf(
            CartItem(id = "p1", productId = "p1", productName = "Eau Minérale", unitPriceCdf = 2500L, quantity = 1L),
            CartItem(id = "p2", productId = "p2", productName = "Riz 5kg", unitPriceCdf = 28000L, quantity = 2L)
        )
        assertEquals(remote, GoPrimeViewModel.mergeLocalCartOverlay(remote, emptyMap()))

        val overlay = mapOf(
            "p1" to CartItem(id = "p1", productId = "p1", productName = "Eau Minérale", unitPriceCdf = 2500L, quantity = 4L),
            "p2" to null,
            "p3" to CartItem(id = "p3", productId = "p3", productName = "Huile 1L", unitPriceCdf = 9500L, quantity = 1L)
        )
        val merged = GoPrimeViewModel.mergeLocalCartOverlay(remote, overlay)
        assertEquals(2, merged.size)
        assertEquals(4L, merged.first { it.productId == "p1" }.quantity)
        assertEquals(1L, merged.first { it.productId == "p3" }.quantity)
        assertFalse(merged.any { it.productId == "p2" })
    }

    @Test
    fun `summarizeProductReviews and summarizeDeliveryReviews compute averages cleanly`() {
        val reviews = listOf(
            OrderReview(
                id = "o1",
                orderId = "o1",
                productIds = listOf("p1", "p2"),
                productRating = 5,
                deliveryRating = 4,
                comment = "Très rapide"
            ),
            OrderReview(
                id = "o2",
                orderId = "o2",
                productIds = listOf("p1"),
                productRating = 3,
                deliveryRating = 5,
                comment = "Bien emballé"
            )
        )

        val (p1Avg, p1Count) = GoPrimeViewModel.summarizeProductReviews("p1", reviews)
        assertEquals(4.0, p1Avg, 0.001)
        assertEquals(2, p1Count)

        val (p3Avg, p3Count) = GoPrimeViewModel.summarizeProductReviews("p3", reviews)
        assertEquals(0.0, p3Avg, 0.001)
        assertEquals(0, p3Count)

        val (delAvg, delCount) = GoPrimeViewModel.summarizeDeliveryReviews(reviews)
        assertEquals(4.5, delAvg, 0.001)
        assertEquals(2, delCount)
    }

    @Test
    fun `appThemeMode fromRaw parses known values and falls back to SYSTEM`() {
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromRaw("DARK"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromRaw("LIGHT"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromRaw("SYSTEM"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromRaw("INVALID_THEME"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromRaw(null))
    }

    @Test
    fun `default whatsapp integration settings include admin phone 243974685125`() {
        val settings = WhatsappIntegrationSettings()
        assertEquals("+243974685125", DEFAULT_ADMIN_WHATSAPP_PHONE)
        assertTrue(settings.enabled)
        assertTrue(settings.notifyCustomerOnOrder)
        assertEquals(listOf("+243974685125"), settings.adminWhatsappPhones)
    }

    @Test
    fun `whatsapp intent service normalizes phones and builds encoded wa me url`() {
        assertEquals("243974685125", WhatsAppIntentService.normalizePhoneForWhatsApp("243974685125"))
        assertEquals("243974685125", WhatsAppIntentService.normalizePhoneForWhatsApp("+243 974 685 125"))
        assertEquals("243974685125", WhatsAppIntentService.normalizePhoneForWhatsApp("0974685125"))
        assertEquals("243974685125", WhatsAppIntentService.normalizePhoneForWhatsApp(""))
        assertEquals("243974685125", WhatsAppIntentService.normalizePhoneForWhatsApp("123"))

        val url = WhatsAppIntentService.buildWhatsAppUrl(
            targetPhone = DEFAULT_ADMIN_WHATSAPP_PHONE,
            message = "Bonjour GoPrime test"
        )
        assertEquals("https://wa.me/243974685125?text=Bonjour%20GoPrime%20test", url)
    }

    @Test
    fun `whatsapp intent service builds complete order and checkout messages`() {
        val order = Order(
            id = "ord_42",
            orderNumber = "GP-778899",
            customerName = "Patrick",
            customerPhone = "+243810001122",
            districtName = "Gombe",
            deliveryAddress = "Av. Tombalbaye 12",
            fulfillmentMode = FulfillmentMode.DELIVERY.name,
            paymentMethod = PaymentMethod.MOBILE_MONEY.name,
            paymentProvider = "M-Pesa",
            paymentReference = "TX9988",
            paymentStatus = PaymentStatus.PAID.name,
            status = OrderStatus.OUT_FOR_DELIVERY.name,
            subtotalCdf = 28000L,
            deliveryFeeCdf = 5000L,
            totalCdf = 33000L,
            notes = "Sonner au portail",
            items = listOf(
                mapOf(
                    "productId" to "p1",
                    "productName" to "Riz 5kg",
                    "unitPriceCdf" to 28000L,
                    "quantity" to 1L
                )
            )
        )

        val orderMsg = WhatsAppIntentService.buildOrderDetailsMessage(order)
        assertTrue(orderMsg.contains("GP-778899"))
        assertTrue(orderMsg.contains("Patrick"))
        assertTrue(orderMsg.contains("+243810001122"))
        assertTrue(orderMsg.contains("Gombe"))
        assertTrue(orderMsg.contains("Av. Tombalbaye 12"))
        assertTrue(orderMsg.contains("1× Riz 5kg"))
        assertTrue(orderMsg.contains("M-Pesa"))
        assertTrue(orderMsg.contains("TX9988"))
        assertTrue(orderMsg.contains("Sonner au portail"))

        val checkoutMsg = WhatsAppIntentService.buildCheckoutDetailsMessage(
            customerName = "Sarah",
            customerPhone = "0974685125",
            fulfillmentMode = FulfillmentMode.PICKUP,
            districtName = "Ngaliema",
            deliveryAddress = "",
            paymentMethod = PaymentMethod.CASH,
            paymentProvider = "",
            paymentPhone = "",
            paymentReference = "",
            notes = "",
            cartItems = listOf(
                CartItem(productId = "p2", productName = "Huile 1L", unitPriceCdf = 9500L, quantity = 2L)
            ),
            subtotalCdf = 19000L,
            deliveryFeeCdf = 0L,
            totalCdf = 19000L
        )
        assertTrue(checkoutMsg.contains("Nouvelle commande GoPrime"))
        assertTrue(checkoutMsg.contains("Sarah"))
        assertTrue(checkoutMsg.contains(FulfillmentMode.PICKUP.labelFr))
        assertTrue(checkoutMsg.contains("2× Huile 1L"))
        assertTrue(checkoutMsg.contains("Espèces"))
    }
}
