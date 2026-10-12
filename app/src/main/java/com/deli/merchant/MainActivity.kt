package com.deli.merchant

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val BASE_URL = "http://158.101.96.169:3000"

data class OrderItem(
    val id: Int,
    val customerName: String,
    val itemsSummary: String,
    val status: String,
    val paymentStatus: String,
    val total: String
)

data class MenuStockItem(
    val id: Int,
    val name: String,
    val category: String,
    val price: String,
    val available: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep kitchen tablet screen awake at all times
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                MerchantKdsApp()
            }
        }
    }
}

@Composable
fun MerchantKdsApp() {
    var selectedTab by remember { mutableStateOf("TICKETS") }
    var orders by remember { mutableStateOf(listOf<OrderItem>()) }
    var menuItems by remember { mutableStateOf(listOf<MenuStockItem>()) }
    var isConnected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refreshData() {
        withContext(Dispatchers.IO) {
            try {
                val ordersRaw = URL("$BASE_URL/api/orders").readText()
                val ordersJson = JSONArray(ordersRaw)
                val parsedOrders = mutableListOf<OrderItem>()
                for (i in 0 until ordersJson.length()) {
                    val o = ordersJson.getJSONObject(i)
                    val st = o.optString("status", "Pending")
                    if (st != "Picked Up") {
                        parsedOrders.add(
                            OrderItem(
                                id = o.optInt("id"),
                                customerName = o.optString("customer_name", "Walk-In"),
                                itemsSummary = o.optString("items", ""),
                                status = st,
                                paymentStatus = o.optString("payment_status", "UNPAID"),
                                total = o.optString("total_price", "0.00")
                            )
                        )
                    }
                }

                val menuRaw = URL("$BASE_URL/api/menu").readText()
                val menuJson = JSONArray(menuRaw)
                val parsedMenu = mutableListOf<MenuStockItem>()
                for (i in 0 until menuJson.length()) {
                    val m = menuJson.getJSONObject(i)
                    parsedMenu.add(
                        MenuStockItem(
                            id = m.optInt("id"),
                            name = m.optString("name"),
                            category = m.optString("category", "Deli"),
                            price = m.optString("price", "0.00"),
                            available = m.optBoolean("available", true)
                        )
                    )
                }

                orders = parsedOrders
                menuItems = parsedMenu
                isConnected = true
            } catch (e: Exception) {
                isConnected = false
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshData()
            delay(4000)
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF12161F))) {
        NavigationRail(
            containerColor = Color(0xFF0B0E14),
            contentColor = Color.White,
            modifier = Modifier.fillMaxHeight().width(100.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Text("DELI KDS", color = Color(0xFFFFB300), fontWeight = FontWeight.Black, fontSize = 15.sp)
            Spacer(Modifier.height(32.dp))
            NavigationRailItem(
                selected = selectedTab == "TICKETS",
                onClick = { selectedTab = "TICKETS" },
                icon = { Text("🧾", fontSize = 24.sp) },
                label = { Text("Tickets (${orders.size})", color = Color.White) }
            )
            Spacer(Modifier.height(16.dp))
            NavigationRailItem(
                selected = selectedTab == "86_BOARD",
                onClick = { selectedTab = "86_BOARD" },
                icon = { Text("🚫", fontSize = 24.sp) },
                label = { Text("86 Board", color = Color.White) }
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedTab == "TICKETS") "Live Kitchen Order Tickets" else "86 Inventory Stock Board",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (isConnected) Color(0xFF1B3B2B) else Color(0xFF451A1A),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isConnected) "● LIVE: 158.101.96.169:3000" else "● RECONNECTING...",
                        color = if (isConnected) Color(0xFF4ADE80) else Color(0xFFF87171),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (selectedTab == "TICKETS") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(orders) { order ->
                        OrderCard(order) { nextStatus ->
                            scope.launch(Dispatchers.IO) {
                                postJson("$BASE_URL/api/orders/${order.id}/status", JSONObject().put("status", nextStatus).toString())
                                refreshData()
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(menuItems) { item ->
                        StockCard(item) {
                            scope.launch(Dispatchers.IO) {
                                postJson("$BASE_URL/api/menu/${item.id}/toggle", "{}")
                                refreshData()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: OrderItem, onAdvance: (String) -> Unit) {
    val headerColor = when (order.status) {
        "In Prep" -> Color(0xFF2563EB)
        "Ready" -> Color(0xFF059669)
        else -> Color(0xFFD97706)
    }
    val nextAction = when (order.status) {
        "In Prep" -> "Ready"
        "Ready" -> "Picked Up"
        else -> "In Prep"
    }
    val buttonLabel = when (order.status) {
        "In Prep" -> "MARK READY"
        "Ready" -> "PICKED UP & PAID"
        else -> "START PREP"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(headerColor).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("#${order.id} • ${order.customerName}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(order.status.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (order.paymentStatus == "PAID") "✅ PAID ($${order.total})" else "💵 PAY AT REGISTER ($${order.total})",
                    color = if (order.paymentStatus == "PAID") Color(0xFF4ADE80) else Color(0xFFFBBF24),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(order.itemsSummary, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onAdvance(nextAction) },
                    colors = ButtonDefaults.buttonColors(containerColor = headerColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun StockCard(item: MenuStockItem, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("${item.category} • $${item.price}", color = Color(0xFF94A3B8), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.available) Color(0xFF059669) else Color(0xFFDC2626)
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (item.available) "IN STOCK (TAP TO 86)" else "86'd / OUT OF STOCK", fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun postJson(urlString: String, jsonBody: String) {
    try {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(jsonBody.toByteArray()) }
        conn.inputStream.close()
    } catch (_: Exception) {}
}
