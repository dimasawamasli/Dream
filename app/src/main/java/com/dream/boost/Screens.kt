package com.dream.boost

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Ganti password di sini.
const val PASSWORD = "dimasnjoy"

private val Violet = Color(0xFFA855F7)
private val VioletDeep = Color(0xFF6D28D9)
private val Magenta = Color(0xFFC026D3)
private val Lav = Color(0xFFE9D5FF)
private val Amber = Color(0xFFFBBF24)
private val Mint = Color(0xFF34D399)
private val Rose = Color(0xFFF472B6)
private val CardBg = Color(0x99100020)
private val CardBorder = Color(0x55A855F7)

// ------------------------------------------------------------------
// Data fitur (key = nama saklar di skrip)
// ------------------------------------------------------------------

data class Feature(
    val key: String,
    val title: String,
    val desc: String,
    val default: Boolean,
    val group: String,
    val parent: String? = null,
    val warn: String? = null
)

private const val G1 = "Perlindungan dan OTG"
private const val G2 = "Performa dan game"
private const val G3 = "Sistem dan pembersihan"

val FEATURES = listOf(
    Feature("F_PHANTOM", "Phantom Process Killer mati", "Android tidak lagi membunuh proses anak (Shizuku, mapper, terminal).", true, G1),
    Feature("F_DOZE", "Whitelist Doze dan baterai", "App dilindungi lolos Doze, standby, dan batas baterai.", true, G1),
    Feature("F_OTG", "OTG bypass", "Jaga GG Mouse Pro, Nebula, SensiX, Red Corner, WhatsApp, dan Discord.", true, G1),
    Feature("F_KBD", "Sembunyikan keyboard layar", "Keyboard layar tidak muncul saat keyboard OTG terpasang.", true, G1, parent = "F_OTG"),
    Feature("F_FREEZER", "Anti-kill latar belakang", "Matikan freezer app cache dan naikkan batas proses cache.", true, G1),

    Feature("F_HZ", "Kunci refresh rate maksimum", "Layar dikunci ke Hz tertinggi yang didukung.", true, G2),
    Feature("F_GAME", "Mode performa game", "Free Fire, FF MAX, Mobile Legends, plus fixed performance.", true, G2),
    Feature("F_DND", "Mode jangan ganggu", "Notifikasi dibungkam selama main.", true, G2),
    Feature("F_NET", "Jaringan low-latency", "Wi-Fi low-latency dan hentikan scan di latar belakang.", true, G2),
    Feature("F_ANIM", "Animasi sistem 0,5x", "Transisi layar lebih cepat.", true, G2),

    Feature("F_DEXSUP", "Dukungan DeX dan freeform", "Jendela desktop dan paksa app bisa di-resize.", true, G3),
    Feature("F_STAYON", "Layar tetap menyala saat dicas", "Opsional.", false, G3, parent = "F_DEXSUP"),
    Feature("F_GOS", "Matikan Samsung GOS", "Anti-throttle mati agar performa tidak diturunkan.", true, G3,
        warn = "HP bisa lebih panas. Otomatis dilewati kalau suhu sudah 42 C atau lebih."),
    Feature("F_NOBG", "Blok Play Store di latar belakang", "Cegah unduhan dan update otomatis saat main.", true, G3),
    Feature("F_DEX", "Kompilasi AOT alat dan game", "Pertama kali bisa agak lama.", true, G3),
    Feature("F_RAM", "Bersihkan RAM dan cache", "Tutup proses latar belakang. App dilindungi tidak disentuh.", true, G3),
    Feature("F_KILL", "Force-stop app lain", "Paling agresif. App dilindungi dan app yang sedang dibuka aman.", true, G3, parent = "F_RAM")
)

private val DND_CHOICES = listOf("priority" to "Prioritas", "alarms" to "Alarm saja", "none" to "Senyap total")
private val GAME_CHOICES = listOf(
    "" to "Tidak",
    "com.dts.freefireth" to "Free Fire",
    "com.dts.freefiremax" to "FF MAX",
    "com.mobile.legends" to "ML"
)

// ------------------------------------------------------------------
// State aplikasi
// ------------------------------------------------------------------

// nama mode di log: KENPOL -> MAXIMALITY
private fun brand(s: String): String =
    s.replace("K E N P O L", "M A X I M A L I T Y").replace("KENPOL", "MAXIMALITY")

class AppState(private val prefs: SharedPreferences) {
    val enabled = mutableStateMapOf<String, Boolean>().apply {
        FEATURES.forEach { put(it.key, prefs.getBoolean(it.key, it.default)) }
    }
    var dnd by mutableStateOf(prefs.getString("dnd", "priority") ?: "priority")
    var game by mutableStateOf(prefs.getString("game", "") ?: "")
    var running by mutableStateOf(false)
    var onLogScreen by mutableStateOf(false)
    var lastCode by mutableStateOf<Int?>(null)
    var lastAction by mutableStateOf("")
    val log = mutableStateListOf<String>()

    fun isOn(key: String): Boolean = enabled[key] == true

    fun set(key: String, v: Boolean) {
        enabled[key] = v
        prefs.edit().putBoolean(key, v).apply()
    }

    fun pickDnd(v: String) {
        dnd = v
        prefs.edit().putString("dnd", v).apply()
    }

    fun pickGame(v: String) {
        game = v
        prefs.edit().putString("game", v).apply()
    }

    /** Maximality = semua default aktif. Aman = tanpa force-stop, GOS, dan blok background. */
    fun preset(safe: Boolean) {
        FEATURES.forEach { set(it.key, it.default) }
        if (safe) {
            set("F_KILL", false)
            set("F_GOS", false)
            set("F_NOBG", false)
        }
    }

    /** mode: "" = boost, "status", "off". Setelah ditekan, pindah ke layar log. */
    fun run(ctx: Context, scope: CoroutineScope, mode: String) {
        if (running) return
        running = true
        lastCode = null
        lastAction = when (mode) {
            "status" -> "Status"
            "off" -> "Kembalikan"
            else -> "Boost"
        }
        log.clear()
        onLogScreen = true
        val flags = FEATURES.associate { it.key to isOn(it.key) }
        val d = dnd
        val g = game
        scope.launch {
            val code = withContext(Dispatchers.IO) {
                try {
                    ShizukuShell.pushScript(ctx)
                    ShizukuShell.runScript(mode, flags, d, g) { line ->
                        scope.launch(Dispatchers.Main) { log.add(brand(line)) }
                    }
                } catch (t: Throwable) {
                    scope.launch(Dispatchers.Main) { log.add("ERROR: ${t.javaClass.simpleName}: ${t.message}") }
                    -1
                }
            }
            log.add("-- selesai (kode $code) --")
            lastCode = code
            running = false
        }
    }
}

enum class SzState { NOT_RUNNING, NO_PERMISSION, READY }

@Composable
fun rememberShizukuState(): State<SzState> = produceState(SzState.NOT_RUNNING) {
    while (true) {
        value = withContext(Dispatchers.IO) {
            when {
                !ShizukuShell.binderUp() -> SzState.NOT_RUNNING
                ShizukuShell.granted() -> SzState.READY
                else -> SzState.NO_PERMISSION
            }
        }
        delay(900)
    }
}

// ------------------------------------------------------------------
// Root
// ------------------------------------------------------------------

@Composable
fun DreamApp() {
    val ctx = LocalContext.current.applicationContext
    val st = remember { AppState(ctx.getSharedPreferences("dream", Context.MODE_PRIVATE)) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    val sz by rememberShizukuState()

    val stage = when {
        !unlocked -> 0
        sz != SzState.READY -> 1
        st.onLogScreen -> 3
        else -> 2
    }
    val holeY = when (stage) {
        0 -> 0.27f
        1 -> 0.24f
        3 -> 0.09f
        else -> 0.13f
    }
    val holeScale = when (stage) {
        2 -> 0.7f
        3 -> 0.5f
        else -> 1f
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        SpaceBackground(holeY = holeY, holeScale = holeScale)
        when (stage) {
            0 -> LoginScreen(onOk = { unlocked = true })
            1 -> ShizukuGate(sz)
            3 -> LogScreen(st)
            else -> MainScreen(st)
        }
    }
}

// ------------------------------------------------------------------
// Komponen kecil
// ------------------------------------------------------------------

@Composable
private fun Wordmark(size: Int) {
    Text(
        "Dream",
        fontSize = size.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (size / 6).sp,
        color = Color.White,
        style = TextStyle(shadow = Shadow(color = Violet, offset = Offset.Zero, blurRadius = 36f))
    )
}

@Composable
private fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(CardBg)
            .border(1.dp, CardBorder, shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun GlowButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val fill = if (enabled) {
        Brush.horizontalGradient(listOf(VioletDeep, Magenta))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF3B2A52), Color(0xFF3B2A52)))
    }
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun OutlineButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, if (enabled) Violet else CardBorder, shape)
            .background(Color(0x22000000))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Lav else Color(0x66E9D5FF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = if (selected) Color.White else Lav,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) VioletDeep else Color(0x33FFFFFF))
            .border(1.dp, if (selected) Violet else CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun CheckRow(ok: Boolean, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Mint else Rose))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Lav, fontSize = 15.sp)
    }
}

@Composable
private fun FeatureRow(f: Feature, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (f.parent != null) 16.dp else 0.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                f.title,
                color = if (enabled) Color.White else Color(0x80FFFFFF),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                f.desc,
                color = Lav.copy(alpha = if (enabled) 0.75f else 0.4f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            if (f.warn != null) {
                Text(f.warn, color = Amber, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Violet,
                uncheckedThumbColor = Lav,
                uncheckedTrackColor = Color(0x44000000),
                uncheckedBorderColor = Color(0x66A855F7)
            )
        )
    }
}

// ------------------------------------------------------------------
// Layar 1: password
// ------------------------------------------------------------------

@Composable
private fun LoginScreen(onOk: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    fun submit() {
        if (pass.trim() == PASSWORD) onOk() else wrong = true
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Wordmark(46)
        Spacer(Modifier.height(6.dp))
        Text("Masukkan password untuk membuka Dream.", color = Lav.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Glass(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it; wrong = false },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    Text(
                        if (show) "Sembunyikan" else "Lihat",
                        color = Violet,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { show = !show }.padding(10.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Violet,
                    focusedBorderColor = Violet,
                    unfocusedBorderColor = Color(0x66A855F7),
                    focusedLabelColor = Violet,
                    unfocusedLabelColor = Lav
                )
            )
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text("Password salah. Coba lagi.", color = Rose, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            GlowButton("Masuk", Modifier.fillMaxWidth()) { submit() }
        }
    }
}

// ------------------------------------------------------------------
// Layar 2: gerbang Shizuku
// ------------------------------------------------------------------

@Composable
private fun ShizukuGate(sz: SzState) {
    val ctx = LocalContext.current
    var installed by remember { mutableStateOf(ShizukuShell.installed(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            installed = ShizukuShell.installed(ctx)
            delay(1500)
        }
    }
    val running = sz != SzState.NOT_RUNNING
    val denied = remember(sz) { sz == SzState.NO_PERMISSION && ShizukuShell.permanentlyDenied() }

    fun openShizuku() {
        ctx.packageManager.getLaunchIntentForPackage(ShizukuShell.SHIZUKU_PKG)?.let { ctx.startActivity(it) }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Wordmark(46)
        Spacer(Modifier.height(6.dp))
        Text("Aktifkan Shizuku untuk masuk.", color = Lav.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Glass(Modifier.fillMaxWidth()) {
            CheckRow(installed, "Shizuku terpasang")
            CheckRow(running, "Shizuku sedang berjalan")
            CheckRow(false, "Dream punya izin Shizuku")
            Spacer(Modifier.height(10.dp))

            val hint = when {
                !installed -> "Pasang Shizuku dulu, lalu jalankan lewat Debugging nirkabel atau ADB."
                !running -> "Buka Shizuku lalu tekan Mulai. Dream lanjut otomatis begitu Shizuku aktif."
                denied -> "Izin pernah ditolak. Buka Shizuku, pilih aplikasi yang diotorisasi, lalu izinkan Dream."
                else -> "Tekan Beri izin lalu setujui di dialog Shizuku."
            }
            Text(hint, color = Lav.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
            if (!running) {
                Spacer(Modifier.height(8.dp))
                Text
                    "Lewat ADB: adb shell sh /sdcard/Android/data/moe.shizuku.privilage.api/start.sh",
                color = Violet,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(16.dp))

            when {
                !installed -> GlowButton("Unduh Shizuku", Modifier.fillMaxWidth()) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")))
                }
                !running -> GlowButton("Buka Shizuku", Modifier.fillMaxWidth()) { openShizuku() }
                else -> {
                    GlowButton("Beri izin", Modifier.fillMaxWidth()) { ShizukuShell.requestPermission() }
                    Spacer(Modifier.height(8.dp))
                    OutlineButton("Buka Shizuku", Modifier.fillMaxWidth()) { openShizuku() }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Layar 3: utama
// ------------------------------------------------------------------

@Composable
private fun MainScreen(st: AppState) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(150.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(30)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x3334D399))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Mint))
                Spacer(Modifier.width(8.dp))
                Text("Shizuku aktif", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Maximality", Modifier.weight(1f), !st.running) { st.preset(false) }
            OutlineButton("Mode aman", Modifier.weight(1f), !st.running) { st.preset(true) }
        }

        FEATURES.groupBy { it.group }.forEach { (group, items) ->
            Text(
                group,
                color = Lav,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            Glass(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, f ->
                    if (i > 0 && f.parent == null) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
                    }
                    val parentOn = f.parent == null || st.isOn(f.parent)
                    FeatureRow(f, st.isOn(f.key) && parentOn, parentOn) { st.set(f.key, it) }
                    if (f.key == "F_DND" && st.isOn("F_DND")) {
                        Row(
                            Modifier.padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DND_CHOICES.forEach { (v, label) -> Chip(label, st.dnd == v) { st.pickDnd(v) } }
                        }
                    }
                }
            }
        }

        Text(
            "Buka game setelah boost",
            color = Lav,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )
        Glass(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GAME_CHOICES.forEach { (v, label) -> Chip(label, st.game == v) { st.pickGame(v) } }
            }
        }

        Spacer(Modifier.height(4.dp))
        GlowButton(
            if (st.running) "Sedang berjalan..." else "Jalankan boost",
            Modifier.fillMaxWidth(),
            enabled = !st.running
        ) { st.run(ctx, scope, "") }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Status", Modifier.weight(1f), !st.running) { st.run(ctx, scope, "status") }
            OutlineButton("Kembalikan", Modifier.weight(1f), !st.running) { st.run(ctx, scope, "off") }
        }
        if (st.running || st.log.isNotEmpty()) {
            OutlineButton("Lihat log", Modifier.fillMaxWidth()) { st.onLogScreen = true }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------
// Layar 4: log (muncul setelah menekan Jalankan boost, Status, atau Kembalikan)
// ------------------------------------------------------------------

@Composable
private fun StatusPill(running: Boolean, code: Int?) {
    val (label, col) = when {
        running -> "Berjalan" to Amber
        code == null -> "Siap" to Lav
        code == 0 -> "Selesai" to Mint
        code == -1 -> "Gagal" to Rose
        else -> "Selesai (kode $code)" to Amber
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(col.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(8.dp))
        Text(label, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LogScreen(st: AppState) {
    BackHandler { st.onLogScreen = false }
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(st.log.size) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "< Kembali",
                color = Lav,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { st.onLogScreen = false }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            StatusPill(st.running, st.lastCode)
        }

        Text(
            if (st.lastAction.isEmpty()) "Log" else "Log: ${st.lastAction}",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )

        Glass(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Text(
                    if (st.log.isEmpty()) "Belum ada output." else st.log.joinToString("\n"),
                    color = Color(0xFFD8B4FE),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (st.running) {
                OutlineButton("Hentikan", Modifier.weight(1f)) { ShizukuShell.stop() }
            } else {
                OutlineButton("Salin log", Modifier.weight(1f), st.log.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(st.log.joinToString("\n")))
                }
                OutlineButton("Bersihkan", Modifier.weight(1f), st.log.isNotEmpty()) {
                    st.log.clear()
                    st.lastCode = null
                }
            }
        }
    }
}
