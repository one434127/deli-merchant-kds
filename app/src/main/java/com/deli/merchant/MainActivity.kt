package com.deli.merchant

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
    val available: Boolean,
    val isIngredient: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
    var ingredientItems by remember { mutableStateOf(listOf<MenuStockItem>()) }
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
                    val avail = if (m.has("is_available")) m.optBoolean("is_available", true) else m.optBoolean("available", true)
                    parsedMenu.add(
                        MenuStockItem(
                            id = m.optInt("id"),
                            name = m.optString("name"),
                            category = m.optString("category", "Menu Item"),
                            price = m.optString("price", "0.00"),
                            available = avail,
                            isIngredient = false
                        )
                    )
                }

                val ingRaw = URL("$BASE_URL/api/ingredients").readText()
                val ingJson = JSONArray(ingRaw)
                val parsedIng = mutableListOf<MenuStockItem>()
                for (i in 0 until ingJson.length()) {
                    val ing = ingJson.getJSONObject(i)
                    val avail = if (ing.has("is_available")) ing.optBoolean("is_available", true) else ing.optBoolean("available", true)
                    parsedIng.add(
                        MenuStockItem(
                            id = ing.optInt("id"),
                            name = ing.optString("name"),
                            category = ing.optString("category", "Ingredient"),
                            price = ing.optString("extra_price", ing.optString("price", "0.00")),
                            available = avail,
                            isIngredient = true
                        )
                    )
                }

                orders = parsedOrders
                menuItems = parsedMenu
                ingredientItems = parsedIng
                isConnected = true
            } catch (e: Exception) {
                isConnected = false
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshData()
            delay(3000)
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF12161F))) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0xFF1A202C))
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("DELI KDS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isConnected) "● LIVE ONLINE" else "● RECONNECTING...",
                    color = if (isConnected) Color(0xFF48BB78) else Color(0xFFF56565),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { selectedTab = "TICKETS" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == "TICKETS") Color(0xFF3182CE) else Color(0xFF2D3748)
                    ),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ACTIVE TICKETS (${orders.size})", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { selectedTab = "86_BOARD" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == "86_BOARD") Color(0xFFE53E3E) else Color(0xFF2D3748)
                    ),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("86 / STOCK BOARD", fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { scope.launch { refreshData() } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5568)),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("SYNC NOW", fontWeight = FontWeight.Bold)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            if (selectedTab == "TICKETS") {
                if (orders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No Active Kitchen Tickets", color = Color.Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(orders) { order ->
                            TicketCard(
                                order = order,
                                onAdvance = { nextStatus ->
                                    scope.launch(Dispatchers.IO) {
                                        val body = JSONObject().apply {
                                            put("status", nextStatus)
                                            if (nextStatus == "Picked Up") {
                                                put("payment_status", "PAID")
                                            }
                                        }
                                        postJson("$BASE_URL/api/orders/${order.id}", body.toString())
                                        refreshData()
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            "MENU ITEMS (${menuItems.size})",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    items(menuItems) { item ->
                        StockCard(
                            item = item,
                            onToggle = {
                                scope.launch(Dispatchers.IO) {
                                    val body = JSONObject().put("is_available", !item.available)
                                    postJson("$BASE_URL/api/menu/${item.id}/availability", body.toString())
                                    refreshData()
                                }
                            }
                        )
                    }
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            "INGREDIENTS & ADD-ONS (${ingredientItems.size})",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                        )
                    }
                    items(ingredientItems) { ing ->
                        StockCard(
                            item = ing,
                            onToggle = {
                                scope.launch(Dispatchers.IO) {
                                    val body = JSONObject().put("is_available", !ing.available)
                                    postJson("$BASE_URL/api/ingredients/${ing.id}/availability", body.toString())
                                    refreshData()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TicketCard(order: OrderItem, onAdvance: (String) -> Unit) {
    val nextStatus = when (order.status) {
        "Pending" -> "In Prep"
        "In Prep" -> "Ready"
        else -> "Picked Up"
    }
    val buttonLabel = when (order.status) {
        "Pending" -> "START PREP"
        "In Prep" -> "MARK READY"
        else -> "PICKED UP (COMPLETE)"
    }
    val buttonColor = when (order.status) {
        "Pending" -> Color(0xFFD69E2E)
        "In Prep" -> Color(0xFF3182CE)
        else -> Color(0xFF38A169)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#${order.id} • ${order.customerName}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    order.paymentStatus,
                    color = if (order.paymentStatus == "PAID") Color(0xFF48BB78) else Color(0xFFF56565),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Status: ${order.status}", color = Color(0xFFA0AEC0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(order.itemsSummary, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onAdvance(nextStatus) },
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(buttonLabel, fontWeight = FontWeight.Black, fontSize = 16.sp)
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
