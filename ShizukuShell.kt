package com.dream.boost

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/** Semua hal yang menyentuh Shizuku ada di sini. */
object ShizukuShell {

    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val REMOTE_SCRIPT = "/data/local/tmp/dream_nova.sh"
    private const val ASSET_NAME = "nova_boost_v1.sh"

    @Volatile
    private var current: Process? = null

    /** Layanan Shizuku sedang berjalan? */
    fun binderUp(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** Aplikasi ini sudah diberi izin oleh Shizuku? */
    fun granted(): Boolean = try {
        binderUp() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** True kalau pengguna pernah menolak permanen ("jangan tanya lagi"). */
    fun permanentlyDenied(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (_: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(1001)
        } catch (_: Throwable) {
        }
    }

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (_: Exception) {
        false
    }

    // Shizuku 13.1+ menyembunyikan newProcess(), jadi dipanggil lewat refleksi.
    private fun newProcess(cmd: Array<String>): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, cmd, null, null) as Process
    }

    /** Salin skrip dari assets ke /data/local/tmp (bisa dibaca & ditulis uid shell). */
    fun pushScript(ctx: Context) {
        val bytes = ctx.assets.open(ASSET_NAME).use { it.readBytes() }
        val p = newProcess(arrayOf("sh", "-c", "cat > $REMOTE_SCRIPT && chmod 755 $REMOTE_SCRIPT"))
        p.outputStream.use { it.write(bytes) }
        p.waitFor()
    }

    /**
     * Jalankan skrip. [mode]: "" = boost, "status", atau "off".
     * Setiap baris output dikirim ke [onLine]. Mengembalikan exit code.
     */
    fun runScript(
        mode: String,
        flags: Map<String, Boolean>,
        dnd: String,
        launchPkg: String,
        onLine: (String) -> Unit
    ): Int {
        val env = buildString {
            flags.forEach { (k, v) -> append(k).append('=').append(if (v) 1 else 0).append(' ') }
            append("DND_MODE=").append(dnd).append(' ')
            append("KEEP_EXTRA=com.dream.boost ")
            if (launchPkg.isNotEmpty()) append("AUTO_LAUNCH=").append(launchPkg).append(' ')
        }
        val cmd = "${env}sh $REMOTE_SCRIPT $mode </dev/null 2>&1"
        val p = newProcess(arrayOf("sh", "-c", cmd))
        current = p
        try {
            try {
                p.outputStream.close()
            } catch (_: Exception) {
            }
            BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    onLine(line)
                }
            }
            return p.waitFor()
        } finally {
            current = null
        }
    }

    fun stop() {
        try {
            current?.destroy()
        } catch (_: Throwable) {
        }
    }
}
