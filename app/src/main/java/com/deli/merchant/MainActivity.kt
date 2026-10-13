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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.json.*
import java.net.*
import java.time.*
import java.time.format.DateTimeFormatter

data class StoreInfo(val id: Int, val name: String)
var curStoreId by mutableStateOf(1)
var curStoreName by mutableStateOf("Neighborhood Deli")
var storesList by mutableStateOf(listOf<StoreInfo>())
var masterItems by mutableStateOf(listOf<MenuStockItem>())
var masterIngs by mutableStateOf(listOf<MenuStockItem>())
var adminUnlocked by mutableStateOf(false)
var showAdminDialog by mutableStateOf(false)
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
        if (m < 1) "Just now (" + c + ")" else if (m < 60) m.toString() + "m ago (" + c + ")" else (m/60).toString() + "h " + (m%60).toString() + "m ago (" + c + ")"
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
    var allowReg by remember { mutableStateOf(true) }
    var allowStripe by remember { mutableStateOf(true) }
    var savedReg by remember { mutableStateOf(true) }
    var savedStripe by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    suspend fun sync() {
        withContext(Dispatchers.IO) {
            try {
                val stj=JSONArray(URL("$BASE_URL/api/stores").readText())
storesList=(0 until stj.length()).map{val x=stj.getJSONObject(it);StoreInfo(x.optInt("id"),x.optString("store_name"))}
storesList.find{it.id==curStoreId}?.let{curStoreName=it.name}
val cj=JSONObject(URL("$BASE_URL/api/catalog").readText())
val cm=cj.getJSONArray("master_items");masterItems=(0 until cm.length()).map{val m=cm.getJSONObject(it);MenuStockItem(m.optInt("id"),m.optString("name"),"Menu",m.optString("price","8.99"),true)}
val ci=cj.getJSONArray("master_ingredients");masterIngs=(0 until ci.length()).map{val g=ci.getJSONObject(it);MenuStockItem(g.optInt("id"),g.optString("name"),g.optString("category","Ing"),g.optString("add_on_price","0.00"),true)}
val oj = JSONArray(URL("$BASE_URL/api/orders?store_id=$curStoreId").readText())
                val pa = mutableListOf<OrderItem>(); val pd = mutableListOf<OrderItem>()
                for (i in 0 until oj.length()) {
                    val o = oj.getJSONObject(i); val st = o.optString("status", "Pending"); val cr = o.optString("created_at", "")
                    val itm = OrderItem(o.optInt("id"), o.optString("customer_name", "Walk-In"), o.optString("pickup_time", "ASAP"), o.optString("items", ""), st, o.optString("payment_status", "UNPAID"), o.optString("total_price", "0.00"), fmtTime(cr))
                    if (st != "Picked Up") pa.add(itm) else if (isTodayOrder(cr)) pd.add(itm)
                }
                val mj = JSONArray(URL("$BASE_URL/api/menu?store_id=$curStoreId").readText())
                menu = (0 until mj.length()).map { val m = mj.getJSONObject(it); MenuStockItem(m.optInt("id"), m.optString("name"), m.optString("category", "Menu"), m.optString("price", "0.00"), m.optBoolean("is_available", true)) }
                val ij = JSONArray(URL("$BASE_URL/api/ingredients?store_id=$curStoreId").readText())
                ings = (0 until ij.length()).map { val g = ij.getJSONObject(it); MenuStockItem(g.optInt("id"), g.optString("name"), g.optString("category", "Ing"), g.optString("extra_price", "0.00"), g.optBoolean("is_available", true)) }
                val sj = JSONObject(URL("$BASE_URL/api/settings?store_id=$curStoreId").readText())
                val r = sj.optBoolean("allow_pay_at_register", true)
                val s = sj.optBoolean("allow_prepay_stripe", true)
                allowReg = r; allowStripe = s
                if (r || s) { savedReg = r; savedStripe = s }
                active = pa.sortedBy { it.id }; done = pd; online = true
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
    KdsLayout(tab, { tab = it }, active, done, menu, ings, online, allowReg, allowStripe, savedReg, savedStripe) { scope.launch { sync() } }
}
@Composable
fun KdsLayout(tab: String, setTab: (String)->Unit, active: List<OrderItem>, done: List<OrderItem>, menu: List<MenuStockItem>, ings: List<MenuStockItem>, online: Boolean, allowReg: Boolean, allowStripe: Boolean, savedReg: Boolean, savedStripe: Boolean, onSync: ()->Unit) {
    val scope = rememberCoroutineScope()
    val isPaused = !allowReg && !allowStripe
    Row(Modifier.fillMaxSize().background(Color(0xFF12161F))) {
        Column(Modifier.width(225.dp).fillMaxHeight().background(Color(0xFF1A202C)).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("DELI KDS",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Black)
Text("🏪 "+curStoreName,color=Color(0xFF38BDF8),fontSize=14.sp,fontWeight=FontWeight.Bold,modifier=Modifier.pointerInput(Unit){awaitEachGesture{awaitFirstDown();if(withTimeoutOrNull(3000){waitForUpOrCancellation()}==null)showAdminDialog=true}})
AdminPinDialog()
                Text(if (online) "● LIVE ONLINE" else "● OFFLINE", color = if (online) Color(0xFF48BB78) else Color(0xFFF56565), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (isPaused) {
                    Spacer(Modifier.height(6.dp))
                    Text("⚠️ ORDERS PAUSED", color = Color(0xFFF56565), fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { setTab("TICKETS") }, colors = ButtonDefaults.buttonColors(containerColor = if (tab == "TICKETS") Color(0xFF3182CE) else Color(0xFF2D3748)), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("ACTIVE (${active.size})", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { setTab("DONE") }, colors = ButtonDefaults.buttonColors(containerColor = if (tab == "DONE") Color(0xFF38A169) else Color(0xFF2D3748)), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("TODAY DONE (${done.size})", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { setTab("86") }, colors = ButtonDefaults.buttonColors(containerColor = if (tab == "86") Color(0xFFE53E3E) else Color(0xFF2D3748)), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("86 / STOCK", fontWeight = FontWeight.Bold) }
if(adminUnlocked){
  Spacer(Modifier.height(10.dp))
  Button(onClick={setTab("ADMIN")},colors=ButtonDefaults.buttonColors(containerColor=if(tab=="ADMIN")Color(0xFFA855F7)else Color(0xFF2D3748)),modifier=Modifier.fillMaxWidth().height(50.dp)){Text("➕ ADMIN BUILDER",fontWeight=FontWeight.Bold)}
  TextButton(onClick={adminUnlocked=false;if(tab=="ADMIN")setTab("TICKETS")},modifier=Modifier.fillMaxWidth()){Text("🔓 LOCK & EXIT ADMIN",color=Color(0xFFFBBF24),fontWeight=FontWeight.Bold)}
}
            }
            Button(onClick = onSync, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5568)), modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("SYNC NOW", fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            if (tab == "ADMIN") {
AdminBuilderScreen(onSync)
} else if (tab != "86") {
                val list = if (tab == "TICKETS") active else done
                val isDone = tab == "DONE"
                LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list) { o -> TicketCard(o, isDone) { st -> scope.launch(Dispatchers.IO) { val b = JSONObject().put("status", st); if (st == "Picked Up") b.put("payment_status", "PAID"); postJson("$BASE_URL/api/orders/${o.id}", b.toString()); onSync() } } }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item(span = { GridItemSpan(3) }) {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isPaused) "🔴 ORDERING IS PAUSED" else "🟢 ACCEPTING NEW ORDERS", color = if (isPaused) Color(0xFFF87171) else Color(0xFF4ADE80), fontSize = 18.sp, fontWeight = FontWeight.Black)
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val body = if (isPaused) {
                                                    JSONObject().put("allow_pay_at_register", if (!savedReg && !savedStripe) true else savedReg).put("allow_prepay_stripe", if (!savedReg && !savedStripe) true else savedStripe)
                                                } else {
                                                    JSONObject().put("allow_pay_at_register", false).put("allow_prepay_stripe", false)
                                                }
                                                putJson("$BASE_URL/api/settings?store_id=$curStoreId", body.toString())
                                                onSync()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isPaused) Color(0xFF16A34A) else Color(0xFFDC2626)),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(if (isPaused) "▶️ RESUME ORDERS" else "⏸️ PAUSE ALL ORDERS", fontSize = 15.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { scope.launch(Dispatchers.IO) { putJson("$BASE_URL/api/settings?store_id=$curStoreId", JSONObject().put("allow_pay_at_register", !allowReg).put("allow_prepay_stripe", allowStripe).toString()); onSync() } },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (allowReg) Color(0xFF2563EB) else Color(0xFF475569)),
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) { Text(if (allowReg) "💵 Pay at Register: ON" else "💵 Pay at Register: OFF", fontWeight = FontWeight.Bold) }
                                    Button(
                                        onClick = { scope.launch(Dispatchers.IO) { putJson("$BASE_URL/api/settings?store_id=$curStoreId", JSONObject().put("allow_pay_at_register", allowReg).put("allow_prepay_stripe", !allowStripe).toString()); onSync() } },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (allowStripe) Color(0xFF2563EB) else Color(0xFF475569)),
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) { Text(if (allowStripe) "💳 Prepay Stripe: ON" else "💳 Prepay Stripe: OFF", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(3) }) { Text("MENU ITEMS (${menu.size})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
                    items(menu) { m -> StockCard(m) { scope.launch(Dispatchers.IO) { postJson("$BASE_URL/api/menu/${m.id}/availability?store_id=$curStoreId", JSONObject().put("is_available", !m.available).toString()); onSync() } } }
                    item(span = { GridItemSpan(3) }) { Text("INGREDIENTS (${ings.size})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
                    items(ings) { g -> StockCard(g) { scope.launch(Dispatchers.IO) { postJson("$BASE_URL/api/ingredients/${g.id}/availability?store_id=$curStoreId", JSONObject().put("is_available", !g.available).toString()); onSync() } } }
                }
            }
        }
    }
}
@Composable
fun TicketCard(o: OrderItem, isDone: Boolean, onAdv: (String)->Unit) {
    val nextSt = if (isDone) "Ready" else when (o.status) { "Pending" -> "In Prep"; "In Prep" -> "Ready"; else -> "Picked Up" }
    val lbl = if (isDone) "↩ RECALL TO ACTIVE" else when (o.status) { "Pending" -> "START PREP"; "In Prep" -> "MARK READY"; else -> "PICKED UP" }
    val col = if (isDone) Color(0xFF4A5568) else when (o.status) { "Pending" -> Color(0xFFD69E2E); "In Prep" -> Color(0xFF3182CE); else -> Color(0xFF38A169) }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${o.id} • ${o.customerName}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(o.paymentStatus, color = if (o.paymentStatus == "PAID") Color(0xFF48BB78) else Color(0xFFF56565), fontWeight = FontWeight.Bold)
            }
            if (o.timeLabel.isNotEmpty()) Text("${o.timeLabel} • ${o.pickupTime}", color = Color(0xFF63B3ED), fontSize = 12.sp)
            Text("Status: ${o.status} • $${o.total}", color = Color(0xFFA0AEC0), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(o.itemsSummary, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onAdv(nextSt) }, colors = ButtonDefaults.buttonColors(containerColor = col), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(lbl, fontWeight = FontWeight.Black) }
        }
    }
}
@Composable
fun StockCard(i: MenuStockItem, onToggle: ()->Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(i.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${i.category} • $${i.price}", color = Color(0xFF94A3B8), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = if (i.available) Color(0xFF059669) else Color(0xFFDC2626)), modifier = Modifier.fillMaxWidth().height(44.dp)) { Text(if (i.available) "IN STOCK (TAP TO 86)" else "86'd / OUT OF STOCK", fontWeight = FontWeight.Bold) }
        }
    }
}
fun postJson(u: String, b: String) { try { (URL(u).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(b.toByteArray()) }; inputStream.close() } } catch (_: Exception) {} }
fun putJson(u: String, b: String) { try { (URL(u).openConnection() as HttpURLConnection).apply { requestMethod = "PUT"; doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(b.toByteArray()) }; inputStream.close() } } catch (_: Exception) {} }
@Composable
fun AdminPinDialog() {
  var pw by remember { mutableStateOf("") }
  if (showAdminDialog) AlertDialog(onDismissRequest={showAdminDialog=false}, title={Text("🔒 Super Admin Unlock")}, text={OutlinedTextField(value=pw,onValueChange={pw=it},label={Text("Admin Password")},singleLine=true)}, confirmButton={Button(onClick={if(pw=="admin123"){adminUnlocked=true;showAdminDialog=false;pw=""}}){Text("Unlock")}}, dismissButton={TextButton(onClick={showAdminDialog=false}){Text("Cancel")}})
}
@Composable
fun AdminBuilderScreen(onSync: ()->Unit) {
  val scope = rememberCoroutineScope()
  var q by remember { mutableStateOf("") }
  var pr by remember { mutableStateOf("8.99") }
  var nm by remember { mutableStateOf("") }
  var np by remember { mutableStateOf("9.99") }
  var inNm by remember { mutableStateOf("") }
  var inCat by remember { mutableStateOf("Topping") }
  LazyVerticalGrid(columns=GridCells.Fixed(2), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
    item { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF1E293B))) { Column(Modifier.padding(12.dp)) {
      Text("🏪 1. Switch Tablet Deli", color=Color.White, fontWeight=FontWeight.Black)
      storesList.forEach { s -> Button(onClick={curStoreId=s.id;curStoreName=s.name;onSync()}, modifier=Modifier.fillMaxWidth().padding(top=4.dp)) { Text("Assign -> "+s.name) } }
    } } }
    item { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF1E293B))) { Column(Modifier.padding(12.dp)) {
      Text("🍔 2. Add Master Item (Type 'cho')", color=Color.White, fontWeight=FontWeight.Black)
      OutlinedTextField(value=q, onValueChange={q=it}, label={Text("🔍 Search Master Item")}, singleLine=true)
      OutlinedTextField(value=pr, onValueChange={pr=it}, label={Text("Store Price")}, singleLine=true)
      masterItems.filter{it.name.contains(q,true)}.take(3).forEach { m -> Button(onClick={scope.launch(Dispatchers.IO){postJson("$BASE_URL/api/admin/store-item?store_id=$curStoreId",JSONObject().put("menu_item_id",m.id).put("price",pr).toString());onSync()}}, modifier=Modifier.fillMaxWidth().padding(top=4.dp)){Text("➕ Add "+m.name+" ($"+pr+")")} }
    } } }
    item { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF1E293B))) { Column(Modifier.padding(12.dp)) {
      Text("✨ 3. Create Brand-New Menu Item", color=Color.White, fontWeight=FontWeight.Black)
      OutlinedTextField(value=nm, onValueChange={nm=it}, label={Text("New Item Name")}, singleLine=true)
      OutlinedTextField(value=np, onValueChange={np=it}, label={Text("Price")}, singleLine=true)
      Button(onClick={if(nm.isNotEmpty())scope.launch(Dispatchers.IO){postJson("$BASE_URL/api/admin/new-item?store_id=$curStoreId",JSONObject().put("name",nm).put("price",np).put("description","Freshly made").toString());nm="";onSync()}}, modifier=Modifier.fillMaxWidth().padding(top=6.dp)){Text("Create & Add to Store")}
    } } }
    item { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF1E293B))) { Column(Modifier.padding(12.dp)) {
      Text("🧀 4. Add/Create Store Ingredient", color=Color.White, fontWeight=FontWeight.Black)
      OutlinedTextField(value=inNm, onValueChange={inNm=it}, label={Text("New Ingredient Name")}, singleLine=true)
      OutlinedTextField(value=inCat, onValueChange={inCat=it}, label={Text("Category (Meat, Cheese, Topping)")}, singleLine=true)
      Button(onClick={if(inNm.isNotEmpty())scope.launch(Dispatchers.IO){postJson("$BASE_URL/api/admin/ing?store_id=$curStoreId",JSONObject().put("name",inNm).put("category",inCat).put("add_on_price",0).toString());inNm="";onSync()}}, modifier=Modifier.fillMaxWidth().padding(top=6.dp)){Text("Save Ingredient to Store")}
    } } }
  }
}
