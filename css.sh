#!/data/data/com.termux/files/usr/bin/bash
# WifiShare — السكربت 1 من 2: إصلاحات الكود (Kotlin)
# يغطي: Path Traversal، المصادقة بتوكن، حدود الاستلام، تنظيف الملفات الجزئية،
#       التحقق من QR، إصلاح الحجم المجهول، تعقيم إعادة التسمية.
# الاستخدام: bash wsfix1_code.sh [مسار_المشروع]   (الافتراضي: ~/WifiShare)
# آمن لإعادة التشغيل (idempotent) ويحفظ نسخًا احتياطية في .wsfix_backup + commit في Git.

PROJ="${1:-$HOME/WifiShare}"
[ -d "$PROJ/app/src/main" ] || { echo "[!] ليس مشروع WifiShare: $PROJ"; exit 1; }

if ! command -v python3 >/dev/null 2>&1; then
  echo "[*] تثبيت python..."
  pkg install -y python || { echo "[!] ثبّت python يدويًا: pkg install python"; exit 1; }
fi

cd "$PROJ" || exit 1

# --- Git: نقطة رجوع قبل أي تعديل ---
GIT="git -c user.name=wsfix -c user.email=wsfix@localhost"
if command -v git >/dev/null 2>&1; then
  [ -d .git ] || { git init -q && echo "[*] git init"; }
  for l in ".wsfix_backup/" "keystore.properties" "*.jks" "local.properties" "build/" ".gradle/"; do
    grep -qxF "$l" .gitignore 2>/dev/null || echo "$l" >> .gitignore
  done
  git add -A >/dev/null 2>&1
  $GIT commit -q -m "baseline before wsfix1" >/dev/null 2>&1 && echo "[*] تم commit للحالة الأصلية"
fi

python3 - "$PROJ" <<'PYEOF'
import sys, os, shutil

ROOT = sys.argv[1]
BK = os.path.join(ROOT, ".wsfix_backup")
done, skipped, fails = [], [], []
PKG = "app/src/main/java/com/example/wifishare/"

def P(p): return os.path.join(ROOT, p)
def rd(p):
    with open(P(p), encoding="utf-8") as f: return f.read()
def wr(p, t):
    os.makedirs(os.path.dirname(P(p)), exist_ok=True)
    with open(P(p), "w", encoding="utf-8", newline="\n") as f: f.write(t)
def backup(p):
    if os.path.exists(P(p)):
        dst = os.path.join(BK, p)
        if not os.path.exists(dst):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(P(p), dst)

def rep(p, old, new, tag):
    """استبدال نص واحد بالضبط. يتخطى لو التعديل مطبّق مسبقًا."""
    if not os.path.exists(P(p)):
        fails.append(f"{tag}: الملف غير موجود {p}"); return
    t = rd(p)
    if new in t:
        skipped.append(tag); return
    n = t.count(old)
    if n != 1:
        fails.append(f"{tag}: النمط وُجد {n} مرة في {p} (المطلوب 1)"); return
    backup(p); wr(p, t.replace(old, new)); done.append(tag)

def put(p, content, tag):
    """كتابة ملف كامل."""
    if os.path.exists(P(p)) and rd(p) == content:
        skipped.append(tag); return
    backup(p); wr(p, content); done.append(tag)

# ════════════════════════════════════════════════════════════
# 1) SafeName + SessionToken (ملفات جديدة)
# ════════════════════════════════════════════════════════════
put(PKG + "core/SafeName.kt", r'''package com.example.wifishare.core

import java.io.File

/** تعقيم أسماء الملفات القادمة من الشبكة أو من المستخدم (يمنع ../ والمسارات). */
object SafeName {
    private val BAD = Regex("[\\u0000-\\u001F<>:\"|?*]")

    fun sanitize(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(BAD, "_").trim().trim('.')
        val name = if (base.isBlank()) "file_${System.currentTimeMillis()}" else base
        if (name.length <= 120) return name
        val ext = name.substringAfterLast('.', "").take(10)
        val stem = name.substringBeforeLast('.', name).take(100)
        return if (ext.isEmpty()) stem else "$stem.$ext"
    }

    /** يتأكد أن الملف داخل المجلد المسموح (canonical path). */
    fun isInside(dir: File, file: File): Boolean =
        file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
}
''', "SafeName.kt")

put(PKG + "core/SessionToken.kt", r'''package com.example.wifishare.core

import java.security.SecureRandom

/** توكن جلسة عشوائي 128-bit يُعرض داخل QR ويُطلب من المرسل. */
object SessionToken {
    private val rnd = SecureRandom()
    @Volatile private var value: String = generate()
    @Volatile private var createdAt: Long = System.currentTimeMillis()

    val current: String get() = value

    fun regenerate(): String {
        value = generate()
        createdAt = System.currentTimeMillis()
        return value
    }

    fun isExpired(): Boolean =
        System.currentTimeMillis() - createdAt > Constants.QR_SESSION_TTL_MS

    private fun generate(): String {
        val b = ByteArray(16)
        rnd.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
''', "SessionToken.kt")

# ════════════════════════════════════════════════════════════
# 2) Constants + TransferProtocol
# ════════════════════════════════════════════════════════════
rep(PKG + "core/Constants.kt",
    "    const val QR_SESSION_TTL_MS: Long = 5 * 60 * 1000L\n",
    "    const val QR_SESSION_TTL_MS: Long = 5 * 60 * 1000L\n"
    "    const val HANDSHAKE_TIMEOUT_MS: Long = 5_000L\n"
    "    const val MAX_FILE_BYTES: Long = 4L * 1024 * 1024 * 1024\n"
    "    const val MAX_FILES: Int = 500\n"
    "    const val MIN_FREE_BYTES: Long = 50L * 1024 * 1024\n"
    "    const val MAX_BAD_ATTEMPTS: Int = 5\n",
    "Constants limits")

rep(PKG + "data/transfer/TransferProtocol.kt",
    "    const val CONFIRM_FAIL: Int = 0\n",
    "    const val CONFIRM_FAIL: Int = 0\n    const val MAGIC = \"WSHARE1\"\n",
    "Protocol MAGIC")

rep(PKG + "data/transfer/TransferProtocol.kt",
    "    fun readConfirm(input: DataInputStream): Boolean = input.readByte().toInt() == CONFIRM_OK",
    r'''    fun readConfirm(input: DataInputStream): Boolean = input.readByte().toInt() == CONFIRM_OK

    fun writeHello(out: DataOutputStream, token: String) {
        out.writeUTF(MAGIC)
        out.writeUTF(token)
        out.flush()
    }

    /** يرجع التوكن، أو null لو الـ magic غير صحيح. */
    fun readHello(input: DataInputStream): String? {
        if (input.readUTF() != MAGIC) return null
        return input.readUTF()
    }''',
    "Protocol hello")

# ════════════════════════════════════════════════════════════
# 3) TransferServer (استبدال كامل)
# ════════════════════════════════════════════════════════════
put(PKG + "data/transfer/TransferServer.kt", r'''package com.example.wifishare.data.transfer

import android.content.Context
import android.os.Environment
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger
import com.example.wifishare.core.SafeName
import com.example.wifishare.core.SessionToken
import com.example.wifishare.domain.model.TransferDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class TransferServer(private val context: Context) {

    private val _progress = MutableStateFlow(TransferProgress(direction = TransferDirection.RECEIVE))
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    fun receiveDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        return File(base, Constants.DOWNLOAD_SUBDIR).apply { mkdirs() }
    }

    suspend fun receiveOnce(token: String = SessionToken.current): Result<List<File>> =
        withContext(Dispatchers.IO) {
            var server: ServerSocket? = null
            var socket: Socket? = null
            var partial: File? = null
            val saved = mutableListOf<File>()
            try {
                val srv = ServerSocket(Constants.TRANSFER_PORT).apply { reuseAddress = true }
                server = srv
                val deadline = System.currentTimeMillis() + Constants.SERVER_ACCEPT_TIMEOUT_MS
                Logger.d("TransferServer listening on ${Constants.TRANSFER_PORT}")

                var streams: Pair<DataInputStream, DataOutputStream>? = null
                var bad = 0
                while (streams == null) {
                    coroutineContext.ensureActive()
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) throw IOException("Timed out waiting for sender")
                    srv.soTimeout = left.toInt()
                    val s = srv.accept()
                    val hs = handshake(s, token)
                    if (hs == null) {
                        try { s.close() } catch (_: IOException) {}
                        bad++
                        if (bad >= Constants.MAX_BAD_ATTEMPTS) throw IOException("Too many rejected connections")
                    } else {
                        s.tcpNoDelay = true
                        s.receiveBufferSize = 256 * 1024
                        socket = s
                        streams = hs
                    }
                }
                val ready = streams ?: throw IOException("No connection")
                val input = ready.first
                val out = ready.second

                val saveDir = receiveDir()
                val startMs = System.currentTimeMillis()
                var recvBytes = 0L
                var fileCount = 0
                var totalBytes = 0L

                while (true) {
                    coroutineContext.ensureActive()
                    val meta = TransferProtocol.readHeader(input)
                    if (TransferProtocol.isEnd(meta)) break
                    if (fileCount >= Constants.MAX_FILES) throw IOException("Too many files")
                    if (meta.size < 0 || meta.size > Constants.MAX_FILE_BYTES) throw IOException("Invalid file size")
                    if (saveDir.usableSpace < meta.size + Constants.MIN_FREE_BYTES) throw IOException("Not enough storage")

                    fileCount++
                    totalBytes += meta.size
                    _progress.value = _progress.value.copy(
                        direction = TransferDirection.RECEIVE,
                        currentFileName = SafeName.sanitize(meta.name),
                        fileIndex = fileCount,
                        totalFiles = fileCount,
                        totalBytes = totalBytes,
                        isRunning = true
                    )

                    val outFile = uniqueFile(saveDir, meta.name)
                    partial = outFile
                    FileOutputStream(outFile).buffered().use { fileOut ->
                        var remaining = meta.size
                        val buffer = ByteArray(Constants.CHUNK_SIZE)
                        while (remaining > 0) {
                            coroutineContext.ensureActive()
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read <= 0) throw IOException("Connection closed mid-file")
                            fileOut.write(buffer, 0, read)
                            remaining -= read
                            recvBytes += read
                            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                            _progress.value = _progress.value.copy(
                                bytesTransferred = recvBytes,
                                bytesPerSecond = (recvBytes * 1000L) / elapsed
                            )
                        }
                    }
                    partial = null
                    saved.add(outFile)
                    TransferProtocol.writeConfirm(out, ok = true)
                }
                _progress.value = _progress.value.copy(isRunning = false, isDone = true)
                Logger.i("TransferServer done: ${saved.size} file(s), $recvBytes bytes")
                Result.success(saved)
            } catch (e: Exception) {
                Logger.e("TransferServer failed", e)
                partial?.delete()   // لا تترك ملفًا ناقصًا
                _progress.value = _progress.value.copy(
                    isRunning = false,
                    errorMessage = e.message ?: "Unknown error"
                )
                Result.failure(e)
            } finally {
                try { socket?.close() } catch (_: IOException) {}
                try { server?.close() } catch (_: IOException) {}
            }
        }

    /** هل وصل الاتصال عبر واجهة Wi-Fi Direct (p2p)؟ الرابط نفسه مصادَق عليه بقبول الطرفين. */
    private fun isP2pLink(s: Socket): Boolean = try {
        NetworkInterface.getByInetAddress(s.localAddress)?.name?.contains("p2p", ignoreCase = true) == true
    } catch (_: Exception) { false }

    /**
     * يقبل فقط عناوين الشبكة المحلية. ثم: إمّا واجهة p2p، أو توكن صحيح وغير منتهي.
     * يرجع null عند الرفض.
     */
    private fun handshake(s: Socket, token: String): Pair<DataInputStream, DataOutputStream>? = try {
        val a = s.inetAddress
        if (!(a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress)) {
            null
        } else {
            s.soTimeout = Constants.HANDSHAKE_TIMEOUT_MS.toInt()
            val input = TransferProtocol.wrapIn(s.getInputStream())
            val out = TransferProtocol.wrapOut(s.getOutputStream())
            val got = TransferProtocol.readHello(input)
            val fresh = token != SessionToken.current || !SessionToken.isExpired()
            val tokenOk = got != null && got.isNotEmpty() && fresh &&
                MessageDigest.isEqual(got.toByteArray(), token.toByteArray())
            val ok = got != null && (tokenOk || isP2pLink(s))
            out.writeByte(if (ok) 1 else 0)
            out.flush()
            if (ok) {
                s.soTimeout = Constants.SOCKET_TIMEOUT_MS.toInt()
                Pair(input, out)
            } else null
        }
    } catch (_: Exception) { null }

    private fun uniqueFile(dir: File, rawName: String): File {
        val name = SafeName.sanitize(rawName)
        var f = File(dir, name)
        if (f.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
            var i = 1
            while (true) {
                f = File(dir, "$base ($i)$ext")
                if (!f.exists()) break
                i++
            }
        }
        if (!SafeName.isInside(dir, f)) throw IOException("Unsafe file name")
        return f
    }
}
''', "TransferServer.kt (full)")

# ════════════════════════════════════════════════════════════
# 4) TransferClient
# ════════════════════════════════════════════════════════════
CL = PKG + "data/transfer/TransferClient.kt"
rep(CL,
    "    suspend fun send(hostIp: String, files: List<Uri>): Result<Unit> = withContext(Dispatchers.IO) {",
    "    suspend fun send(hostIp: String, files: List<Uri>, token: String = \"\"): Result<Unit> = withContext(Dispatchers.IO) {",
    "Client signature")

rep(CL,
    r'''            val input = TransferProtocol.wrapIn(socket.getInputStream())

            val metas = files.map { uri ->
                FileMeta(
                    name = queryName(uri) ?: "file_${System.currentTimeMillis()}",
                    size = querySize(uri)
                )
            }''',
    r'''            val input = TransferProtocol.wrapIn(socket.getInputStream())

            // مصافحة + توكن الجلسة
            TransferProtocol.writeHello(out, token)
            if (input.readByte().toInt() != 1) {
                throw IOException("Receiver refused the connection (invalid or expired code)")
            }

            val metas = files.map { uri ->
                var size = querySize(uri)
                if (size <= 0L) size = countBytes(uri)   // الحجم مجهول: احسبه فعليًا
                FileMeta(
                    name = queryName(uri) ?: "file_${System.currentTimeMillis()}",
                    size = size
                )
            }''',
    "Client handshake + size")

rep(CL,
    "                        if (read <= 0) break\n",
    "                        if (read <= 0) throw IOException(\"Source shorter than declared: ${meta.name}\")\n",
    "Client no silent break")

rep(CL,
    "    private fun queryName(uri: Uri): String? {",
    r'''    private fun countBytes(uri: Uri): Long =
        contentResolver.openInputStream(uri)?.use { s ->
            var n = 0L
            val b = ByteArray(64 * 1024)
            while (true) {
                val r = s.read(b)
                if (r < 0) break
                n += r
            }
            n
        } ?: 0L

    private fun queryName(uri: Uri): String? {''',
    "Client countBytes")

# ════════════════════════════════════════════════════════════
# 5) QrPayload (استبدال كامل) + QrDisplay + QrScanner
# ════════════════════════════════════════════════════════════
put(PKG + "data/qr/QrPayload.kt", r'''package com.example.wifishare.data.qr

import com.example.wifishare.core.Constants
import java.net.InetAddress

/**
 * البيانات المُشفّرة في QR:
 * WSQR:<name>|<ip>|<port>|<mac?>|<token>
 * يُرفض أي QR بدون توكن أو بعنوان IP غير محلي أو بمنفذ مختلف.
 */
data class QrPayload(
    val name: String,
    val ip: String,
    val port: Int,
    val mac: String? = null,
    val token: String
) {
    fun encode(): String = buildString {
        append(PREFIX)
        append(name.replace("|", "_").replace("\n", " "))
        append('|'); append(ip)
        append('|'); append(port)
        append('|'); append(mac ?: "")
        append('|'); append(token)
    }

    companion object {
        const val PREFIX = "WSQR:"
        private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        private val TOKEN = Regex("^[0-9a-f]{32}$")

        fun decode(raw: String): QrPayload? {
            if (!raw.startsWith(PREFIX)) return null
            val p = raw.removePrefix(PREFIX).split('|')
            if (p.size < 5) return null
            return try {
                val ip = p[1].trim()
                val port = p[2].trim().toInt()
                val token = p[4].trim()
                if (!IPV4.matches(ip) || !TOKEN.matches(token)) return null
                if (port != Constants.TRANSFER_PORT) return null
                val a = InetAddress.getByName(ip)   // IP حرفي، لا DNS
                if (!(a.isSiteLocalAddress || a.isLinkLocalAddress)) return null
                QrPayload(
                    name = p[0].trim().ifBlank { "Android" }.take(40),
                    ip = ip,
                    port = port,
                    mac = p[3].trim().takeIf { it.isNotBlank() },
                    token = token
                )
            } catch (_: Exception) { null }
        }
    }
}
''', "QrPayload.kt (full)")

QD = PKG + "ui/qr/QrDisplayFragment.kt"
rep(QD,
    "import com.example.wifishare.core.Constants\n",
    "import com.example.wifishare.core.Constants\nimport com.example.wifishare.core.SessionToken\n",
    "QrDisplay import")
rep(QD,
    "        val text = QrPayload(name = deviceName, ip = ip, port = Constants.TRANSFER_PORT).encode()",
    "        val token = SessionToken.regenerate()\n"
    "        val text = QrPayload(name = deviceName, ip = ip, port = Constants.TRANSFER_PORT, token = token).encode()",
    "QrDisplay token")
rep(QD,
    "        binding.tvPayload.text = text\n",
    "        binding.tvPayload.visibility = View.GONE   // النص الخام يحتوي التوكن\n",
    "QrDisplay hide payload")

rep(PKG + "ui/qr/QrScannerFragment.kt",
    "TransferFragment.ARG_HOST_IP to p.ip",
    "TransferFragment.ARG_HOST_IP to p.ip,\n                    TransferFragment.ARG_TOKEN to p.token",
    "QrScanner token arg")

# ════════════════════════════════════════════════════════════
# 6) TransferFragment + TransferViewModel (تمرير التوكن)
# ════════════════════════════════════════════════════════════
TF = PKG + "ui/transfer/TransferFragment.kt"
rep(TF,
    "    private val hostIp: String by lazy {\n        requireArguments().getString(ARG_HOST_IP).orEmpty()\n    }\n",
    "    private val hostIp: String by lazy {\n        requireArguments().getString(ARG_HOST_IP).orEmpty()\n    }\n"
    "    private val token: String by lazy {\n        requireArguments().getString(ARG_TOKEN).orEmpty()\n    }\n",
    "TransferFragment token val")
rep(TF,
    "            hostIp = hostIp\n        )",
    "            hostIp = hostIp,\n            token = token\n        )",
    "TransferFragment factory")
rep(TF,
    "        const val ARG_HOST_IP = \"hostIp\"\n",
    "        const val ARG_HOST_IP = \"hostIp\"\n        const val ARG_TOKEN = \"token\"\n",
    "TransferFragment ARG_TOKEN")

VM = PKG + "ui/transfer/TransferViewModel.kt"
rep(VM,
    "    private val hostIp: String\n) : AndroidViewModel(app) {",
    "    private val hostIp: String,\n    private val token: String = \"\"\n) : AndroidViewModel(app) {",
    "ViewModel ctor")
rep(VM,
    "container.transferClient.send(hostIp, uris)",
    "container.transferClient.send(hostIp, uris, token)",
    "ViewModel send")
rep(VM,
    "        private val hostIp: String\n    ) : ViewModelProvider.Factory {",
    "        private val hostIp: String,\n        private val token: String = \"\"\n    ) : ViewModelProvider.Factory {",
    "ViewModel Factory ctor")
rep(VM,
    "TransferViewModel(application, container, direction, hostIp) as T",
    "TransferViewModel(application, container, direction, hostIp, token) as T",
    "ViewModel Factory create")

# ════════════════════════════════════════════════════════════
# 7) SettingsFragment (self-test) + MediaListFragment (rename)
# ════════════════════════════════════════════════════════════
SF = PKG + "ui/settings/SettingsFragment.kt"
rep(SF,
    "import com.example.wifishare.core.toast\n",
    "import com.example.wifishare.core.SessionToken\nimport com.example.wifishare.core.toast\n",
    "Settings import")
rep(SF,
    "                val receiveDeferred = async {",
    "                SessionToken.regenerate()\n                val receiveDeferred = async {",
    "Settings regenerate")
rep(SF,
    "container.transferClient.send(\"127.0.0.1\", listOf(uri))",
    "container.transferClient.send(\"127.0.0.1\", listOf(uri), SessionToken.current)",
    "Settings selftest send")

ML = PKG + "ui/files/MediaListFragment.kt"
rep(ML,
    "import com.example.wifishare.core.toast\n",
    "import com.example.wifishare.core.SafeName\nimport com.example.wifishare.core.toast\n",
    "MediaList import")
rep(ML,
    "    private fun performRename(item: MediaItem, newName: String) {\n",
    "    private fun performRename(item: MediaItem, rawName: String) {\n"
    "        val newName = SafeName.sanitize(rawName)\n",
    "MediaList sanitize")
rep(ML,
    "            val target = File(oldFile.parentFile, newName)\n",
    "            val target = File(oldFile.parentFile, newName)\n"
    "            if (!SafeName.isInside(oldFile.parentFile!!, target)) {\n"
    "                requireContext().toast(getString(R.string.files_rename_failed))\n"
    "                return\n"
    "            }\n",
    "MediaList inside check")

# ════════════════════════════════════════════════════════════
# فحص أولي: توازن الأقواس في الملفات المعدّلة
# ════════════════════════════════════════════════════════════
warn = []
for rel in ["core/SafeName.kt", "core/SessionToken.kt", "data/transfer/TransferServer.kt",
            "data/transfer/TransferClient.kt", "data/transfer/TransferProtocol.kt",
            "data/qr/QrPayload.kt", "ui/transfer/TransferViewModel.kt",
            "ui/transfer/TransferFragment.kt", "ui/files/MediaListFragment.kt",
            "ui/settings/SettingsFragment.kt", "ui/qr/QrDisplayFragment.kt",
            "ui/qr/QrScannerFragment.kt"]:
    if os.path.exists(P(PKG + rel)):
        t = rd(PKG + rel)
        for a, b in ("{}", "()"):
            if t.count(a) != t.count(b):
                warn.append(f"{rel}: عدم توازن {a}{b} ({t.count(a)} vs {t.count(b)})")

print()
print("════════ النتيجة ════════")
print(f"طُبّق:   {len(done)}")
for x in done: print("  ✓", x)
print(f"تُخطّي (مطبّق مسبقًا): {len(skipped)}")
for x in skipped: print("  =", x)
if warn:
    print("تحذيرات:")
    for x in warn: print("  !", x)
if fails:
    print(f"فشل:     {len(fails)}")
    for x in fails: print("  ✗", x)
    sys.exit(2)
PYEOF
RC=$?

if [ $RC -ne 0 ]; then
  echo
  echo "[!] بعض التعديلات لم تُطبَّق (الكود يختلف عن المتوقع). لم يتم commit."
  echo "    الأصل محفوظ في .wsfix_backup و git. أرسل لي هذا الإخراج."
  exit $RC
fi

if command -v git >/dev/null 2>&1; then
  git add -A >/dev/null 2>&1
  $GIT commit -q -m "wsfix1: security fixes (code)" >/dev/null 2>&1 && echo "[✓] commit: wsfix1"
fi

echo
echo "[✓] انتهى السكربت 1. الآن شغّل: bash wsfix2_config.sh"
echo "    للتراجع: git reset --hard HEAD~1   (أو انسخ من .wsfix_backup)"

