package com.deli.merchant
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.json.*
import java.net.*
import java.time.*
import java.time.format.DateTimeFormatter

const val BASE_URL = "http://158.101.96.169:3000"
data class OrderItem(val id: Int, val customerName: String, val pickupTime: String, val itemsSummary: String, val status: String, val paymentStatus: String, val total: String, val timeLabel: String)
data class MenuStockItem(val id: Int, val name: String, val category: String, val price: String, val available: Boolean)
fun isTodayOrder(s: String): Boolean {
    if (s.isEmpty() || s == "null") return true
    return try {
        val inst = Instant.parse(s)
        val ny = ZoneId.of("America/New_York")
        inst.atZone(ny).toLocalDate() == LocalDate.now(ny) && Duration.between(inst, Instant.now()).toHours() < 14
    } catch (_: Exception) { true }
}
fun fmtTime(s: String): String {
    if (s.isEmpty() || s == "null") return ""
    return try {
        val inst = Instant.parse(s)
        val m = Duration.between(inst, Instant.now()).toMinutes()
        val c = inst.atZone(ZoneId.of("America/New_York")).format(DateTimeFormatter.ofPattern("h:mm a"))
        if (m < 1) "Just now (" + c + ")" else if (m < 60) m.toString() + "m ago (" + c + ")" else (m/60).toString() + "h ago (" + c + ")"
    } catch (_: Exception) { "" }
}
fun playChime() {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
    } catch (_: Exception) {}
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContent { MaterialTheme { MerchantKdsApp() } }
    }
}
@Composable
fun MerchantKdsApp() {
    var tab by remember { mutableStateOf("TICKETS") }
    var active by remember { mutableStateOf(listOf<OrderItem>()) }
    var done by remember { mutableStateOf(listOf<OrderItem>()) }
    var menu by remember { mutableStateOf(listOf<MenuStockItem>()) }
    var ings by remember { mutableStateOf(listOf<MenuStockItem>()) }
    var online by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun sync() {
        withContext(Dispatchers.IO) {
            try {
                val oj = JSONArray(URL("$BASE_URL/api/orders").readText())
                val pa = mutableListOf<OrderItem>(); val pd = mutableListOf<OrderItem>()
                for (i in 0 until oj.length()) {
                    val o = oj.getJSONObject(i); val st = o.optString("status", "Pending"); val cr = o.optString("created_at", "")
                    val itm = OrderItem(o.optInt("id"), o.optString("customer_name", "Walk-In"), o.optString("pickup_time", "ASAP"), o.optString("items", ""), st, o.optString("payment_status", "UNPAID"), o.optString("total_price", "0.00"), fmtTime(cr))
                    if (st != "Picked Up") pa.add(itm) else if (isTodayOrder(cr)) pd.add(itm)
                }
                val mj = JSONArray(URL("$BASE_URL/api/menu?all=true").readText())
                menu = (0 until mj.length()).map { val m = mj.getJSONObject(it); MenuStockItem(m.optInt("id"), m.optString("name"), m.optString("category", "Menu"), m.optString("price", "0.00"), m.optBoolean("is_available", true)) }
                val ij = JSONArray(URL("$BASE_URL/api/ingredients").readText())
                ings = (0 until ij.length()).map { val g = ij.getJSONObject(it); MenuStockItem(g.optInt("id"), g.optString("name"), g.optString("category", "Ing"), g.optString("extra_price", "0.00"), g.optBoolean("is_available", true)) }
                active = pa; done = pd; online = true
            } catch (_: Exception) { online = false }
        }
    }
    LaunchedEffect(Unit) {
        var tick = 0
        var prevPendingCount = 0
        while (true) {
            sync()
            val curPending = active.count { it.status == "Pending" }
            if (curPending > 0) {
                if (curPending > prevPendingCount || tick % 5 == 0) {
                    playChime()
                }
                tick++
            } else {
                tick = 0
            }
            prevPendingCount = curPending
            delay(3000)
        }
    }
    KdsLayout(tab, { tab = it }, active, done, menu, ings, online) { scope.launch { sync() } }
}
@Composable
fun KdsLayout(tab: String, setTab: (String)->Unit, active: List<OrderItem>, done: List<OrderItem>, menu: List<MenuStockItem>, ings: List<MenuStockItem>, online: Boolean, onSync: ()->Unit) {
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxSize().background(Color(0xFF12161F))) {
        Column(Modifier.width(225.dp).fillMaxHeight().background(Color(0xFF1A202C)).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("DELI KDS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(if (online) "● LIVE ONLINE" else "● OFFLINE", color = if (online) Color(0xFF48BB78) else Color(0xFFF56565), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { setTab("TICKETS") }, colors = ButtonDefaults.buttonColors(containerColor = if (tab == "TICKETS") Color(0xFF3182CE) else Color(0xFF2D3748)), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("ACTIVE (${active.size})", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { setTab("DONE") }, colors = ButtonDefaults.buttonColors(containerColor = if (tab == "DONE") Color(0xFF38A169) else Color(0xFF2D3748)), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("TODAY DONE (${done.size})", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { setTab("86") }, colors = ButtonDefaults.buttonColors(containerColor = if (tab == "86") Color(0xFFE53E3E) else Color(0xFF2D3748)), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("86 / STOCK", fontWeight = FontWeight.Bold) }
            }
            Button(onClick = onSync, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5568)), modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("SYNC NOW", fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            if (tab != "86") {
                val list = if (tab == "TICKETS") active else done
                val isDone = tab == "DONE"
                LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list) { o -> TicketCard(o, isDone) { st -> scope.launch(Dispatchers.IO) { val b = JSONObject().put("status", st); if (st == "Picked Up") b.put("payment_status", "PAID"); postJson("$BASE_URL/api/orders/${o.id}", b.toString()); onSync() } } }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item(span = { GridItemSpan(3) }) { Text("MENU ITEMS (${menu.size})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
                    items(menu) { m -> StockCard(m) { scope.launch(Dispatchers.IO) { postJson("$BASE_URL/api/menu/${m.id}/availability", JSONObject().put("is_available", !m.available).toString()); onSync() } } }
                    item(span = { GridItemSpan(3) }) { Text("INGREDIENTS (${ings.size})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
                    items(ings) { g -> StockCard(g) { scope.launch(Dispatchers.IO) { postJson("$BASE_URL/api/ingredients/${g.id}/availability", JSONObject().put("is_available", !g.available).toString()); onSync() } } }
                }
            }
        }
    }
}
@Composable
fun TicketCard(o: OrderItem, isDone: Boolean, onAdv: (String)->Unit) {
    val nextSt = if (isDone) "Ready" else when (o.status) { "Pending" -> "In Prep"; "In Prep" -> "Ready"; else -> "Picked Up" }
    val lbl = if (isDone) "↺ RECALL TO ACTIVE" else when (o.status) { "Pending" -> "START PREP"; "In Prep" -> "MARK READY"; else -> "PICKED UP" }
    val col = if (isDone) Color(0xFF4A5568) else when (o.status) { "Pending" -> Color(0xFFD69E2E); "In Prep" -> Color(0xFF3182CE); else -> Color(0xFF38A169) }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${o.id} • ${o.customerName}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(o.paymentStatus, color = if (o.paymentStatus == "PAID") Color(0xFF48BB78) else Color(0xFFF56565), fontWeight = FontWeight.Bold)
            }
            if (o.timeLabel.isNotEmpty()) Text("${o.timeLabel} • ${o.pickupTime}", color = Color(0xFF63B3ED), fontSize = 12.sp)
            Text("Status: ${o.status} • ${o.total}", color = Color(0xFFA0AEC0), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(o.itemsSummary, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onAdv(nextSt) }, colors = ButtonDefaults.buttonColors(containerColor = col), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(lbl, fontWeight = FontWeight.Black) }
        }
    }
}
@Composable
fun StockCard(i: MenuStockItem, onToggle: ()->Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(i.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${i.category} • ${i.price}", color = Color(0xFF94A3B8), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = if (i.available) Color(0xFF059669) else Color(0xFFDC2626)), modifier = Modifier.fillMaxWidth().height(44.dp)) { Text(if (i.available) "IN STOCK (TAP TO 86)" else "86'd / OUT OF STOCK", fontWeight = FontWeight.Bold) }
        }
    }
}
fun postJson(u: String, b: String) { try { (URL(u).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(b.toByteArray()) }; inputStream.close() } } catch (_: Exception) {} }
