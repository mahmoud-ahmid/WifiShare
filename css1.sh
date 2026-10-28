cler
bash css.sh
#!/data/data/com.termux/files/usr/bin/bash
# WifiShare — السكربت 2 من 2: الـ Manifest والبناء والـ viewers
# يغطي: حذف MANAGE_EXTERNAL_STORAGE، تقييد Location، تضييق file_paths، START_NOT_STICKY،
#       توقيع release + R8، تخفيف الـ OOM في عارض الصور وPDF، ومفتاح التوقيع (اختياري).
# الاستخدام:  bash wsfix2_config.sh [مسار_المشروع]
# لتغيير applicationId:  NEW_APP_ID=com.yourname.wifishare bash wsfix2_config.sh
# آمن لإعادة التشغيل، ويحفظ نسخًا احتياطية في .wsfix_backup + commit في Git.

PROJ="${1:-$HOME/WifiShare}"
[ -d "$PROJ/app/src/main" ] || { echo "[!] ليس مشروع WifiShare: $PROJ"; exit 1; }

if ! command -v python3 >/dev/null 2>&1; then
  echo "[*] تثبيت python..."
  pkg install -y python || { echo "[!] ثبّت python يدويًا: pkg install python"; exit 1; }
fi

cd "$PROJ" || exit 1

GIT="git -c user.name=wsfix -c user.email=wsfix@localhost"
if command -v git >/dev/null 2>&1; then
  [ -d .git ] || git init -q
  for l in ".wsfix_backup/" "keystore.properties" "*.jks" "local.properties" "build/" ".gradle/"; do
    grep -qxF "$l" .gitignore 2>/dev/null || echo "$l" >> .gitignore
  done
  git add -A >/dev/null 2>&1
  $GIT commit -q -m "baseline before wsfix2" >/dev/null 2>&1 && echo "[*] تم commit للحالة الحالية"
fi

NEW_APP_ID="${NEW_APP_ID:-}" python3 - "$PROJ" <<'PYEOF'
import sys, os, shutil, re

ROOT = sys.argv[1]
NEW_APP_ID = os.environ.get("NEW_APP_ID", "").strip()
BK = os.path.join(ROOT, ".wsfix_backup")
done, skipped, fails = [], [], []
PKG = "app/src/main/java/com/example/wifishare/"
MAN = "app/src/main/AndroidManifest.xml"
GRADLE = "app/build.gradle.kts"

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
    if not os.path.exists(P(p)):
        fails.append(f"{tag}: الملف غير موجود {p}"); return
    t = rd(p)
    if new in t:
        skipped.append(tag); return
    n = t.count(old)
    if n != 1:
        fails.append(f"{tag}: النمط وُجد {n} مرة في {p} (المطلوب 1)"); return
    backup(p); wr(p, t.replace(old, new)); done.append(tag)

def rm(p, old, tag):
    if not os.path.exists(P(p)):
        fails.append(f"{tag}: الملف غير موجود {p}"); return
    t = rd(p)
    if old not in t:
        skipped.append(tag); return
    backup(p); wr(p, t.replace(old, "")); done.append(tag)

def put(p, content, tag):
    if os.path.exists(P(p)) and rd(p) == content:
        skipped.append(tag); return
    backup(p); wr(p, content); done.append(tag)

# ════════════════════════════════════════════════════════════
# 1) AndroidManifest.xml
# ════════════════════════════════════════════════════════════
rm(MAN,
   '    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />\n',
   "Manifest: حذف MANAGE_EXTERNAL_STORAGE")

rep(MAN,
    '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />',
    '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="32" />',
    "Manifest: FINE_LOCATION maxSdk=32")
rep(MAN,
    '<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />',
    '<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="32" />',
    "Manifest: COARSE_LOCATION maxSdk=32")

rep(MAN,
    '    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />\n',
    '    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />\n'
    '    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />\n',
    "Manifest: READ_MEDIA_VISUAL_USER_SELECTED")

rep(MAN,
    'android:authorities="com.example.wifishare.fileprovider"',
    'android:authorities="${applicationId}.fileprovider"',
    "Manifest: authority = applicationId")

# ════════════════════════════════════════════════════════════
# 2) file_paths.xml (أقل صلاحيات ممكنة)
# ════════════════════════════════════════════════════════════
put("app/src/main/res/xml/file_paths.xml", '''<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="received" path="Download/WifiShare/" />
    <files-path name="received_internal" path="Download/WifiShare/" />
    <cache-path name="selftest" path="." />
</paths>
''', "file_paths.xml (تضييق)")

# ════════════════════════════════════════════════════════════
# 3) TransferService
# ════════════════════════════════════════════════════════════
rep(PKG + "service/TransferService.kt",
    "return START_STICKY", "return START_NOT_STICKY",
    "TransferService: START_NOT_STICKY")

# ════════════════════════════════════════════════════════════
# 4) Gradle: توقيع release + R8 (+ applicationId اختياري)
# ════════════════════════════════════════════════════════════
if os.path.exists(P(GRADLE)):
    t = rd(GRADLE)
    if "import java.util.Properties" not in t:
        backup(GRADLE); t = "import java.util.Properties\n\n" + t; wr(GRADLE, t); done.append("Gradle: import Properties")
    else:
        skipped.append("Gradle: import Properties")

rep(GRADLE,
    'plugins {\n    id("com.android.application")\n    id("org.jetbrains.kotlin.android")\n}\n',
    'plugins {\n    id("com.android.application")\n    id("org.jetbrains.kotlin.android")\n}\n\n'
    '// بيانات التوقيع من keystore.properties (خارج Git)\n'
    'val ksProps = Properties().apply {\n'
    '    val f = rootProject.file("keystore.properties")\n'
    '    if (f.exists()) f.inputStream().use { load(it) }\n'
    '}\n',
    "Gradle: قراءة keystore.properties")

rep(GRADLE,
    '''    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }''',
    '''    signingConfigs {
        create("release") {
            if (ksProps.containsKey("storeFile")) {
                storeFile = file(ksProps["storeFile"] as String)
                storePassword = ksProps["storePassword"] as String
                keyAlias = ksProps["keyAlias"] as String
                keyPassword = ksProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (ksProps.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }''',
    "Gradle: توقيع release + R8")

if NEW_APP_ID:
    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", NEW_APP_ID):
        fails.append(f"NEW_APP_ID غير صالح: {NEW_APP_ID}")
    else:
        rep(GRADLE,
            'applicationId = "com.example.wifishare"',
            f'applicationId = "{NEW_APP_ID}"',
            f"Gradle: applicationId = {NEW_APP_ID}")
        rep(PKG + "core/Constants.kt",
            'const val PROVIDER_AUTHORITY: String = "com.example.wifishare.fileprovider"',
            f'const val PROVIDER_AUTHORITY: String = "{NEW_APP_ID}.fileprovider"',
            "Constants: PROVIDER_AUTHORITY")

# ProGuard
PG = "app/proguard-rules.pro"
if os.path.exists(P(PG)):
    t = rd(PG)
    if "com.journeyapps.barcodescanner" not in t:
        backup(PG)
        wr(PG, t.rstrip("\n") + "\n\n# zxing / QR scanner\n"
                "-keep class com.journeyapps.barcodescanner.** { *; }\n"
                "-keep class com.google.zxing.** { *; }\n"
                "-dontwarn com.google.zxing.**\n")
        done.append("proguard-rules.pro")
    else:
        skipped.append("proguard-rules.pro")
else:
    put(PG, "# zxing / QR scanner\n-keep class com.journeyapps.barcodescanner.** { *; }\n"
            "-keep class com.google.zxing.** { *; }\n-dontwarn com.google.zxing.**\n", "proguard-rules.pro")

# ════════════════════════════════════════════════════════════
# 5) ImageViewerActivity: فك مصغّر في الخلفية
# ════════════════════════════════════════════════════════════
IV = PKG + "ui/viewer/ImageViewerActivity.kt"
rep(IV,
    "import android.graphics.Matrix\n",
    "import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\nimport android.graphics.Matrix\n",
    "ImageViewer: imports 1")
rep(IV,
    "import androidx.appcompat.app.AppCompatActivity\n",
    "import androidx.appcompat.app.AppCompatActivity\nimport androidx.lifecycle.lifecycleScope\n"
    "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n",
    "ImageViewer: imports 2")
rep(IV,
    "        binding.ivImage.setImageURI(uri)\n",
    '''        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { decodeScaled(uri, 2048) }.getOrNull() }
            if (bmp != null) binding.ivImage.setImageBitmap(bmp) else finish()
        }
''',
    "ImageViewer: فك غير متزامن")
rep(IV,
    "    private fun applyMatrix() {",
    '''    /** فك الصورة بحجم أقصى maxPx لتفادي OOM. */
    private fun decodeScaled(uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun applyMatrix() {''',
    "ImageViewer: decodeScaled")

# ════════════════════════════════════════════════════════════
# 6) PdfViewerActivity: رسم في الخلفية بعرض الشاشة
# ════════════════════════════════════════════════════════════
PV = PKG + "ui/viewer/PdfViewerActivity.kt"
rep(PV,
    "import androidx.appcompat.app.AppCompatActivity\n",
    "import androidx.appcompat.app.AppCompatActivity\nimport androidx.lifecycle.lifecycleScope\n"
    "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n",
    "PdfViewer: imports")
rep(PV,
    "        runCatching { renderer?.close() }\n",
    "        runCatching { renderer?.let { r -> synchronized(r) { r.close() } } }\n",
    "PdfViewer: إغلاق آمن")
rep(PV,
    '''            fun bind(index: Int) {
                val page = renderer.openPage(index)
                val scale = 2
                val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                b.ivPage.setImageBitmap(bmp)
                page.close()
            }''',
    '''            fun bind(index: Int) {
                val ctx = b.root.context
                val w = ctx.resources.displayMetrics.widthPixels
                b.ivPage.setImageBitmap(null)
                val scope = (ctx as? AppCompatActivity)?.lifecycleScope ?: return
                scope.launch {
                    val bmp = withContext(Dispatchers.Default) {
                        try {
                            // PdfRenderer يسمح بصفحة مفتوحة واحدة فقط في وقت واحد
                            synchronized(renderer) {
                                val page = renderer.openPage(index)
                                try {
                                    val ratio = w.toFloat() / page.width
                                    val h = (page.height * ratio).toInt().coerceIn(1, 4096)
                                    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    out.eraseColor(Color.WHITE)
                                    page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    out
                                } finally {
                                    page.close()
                                }
                            }
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    if (bmp != null && bindingAdapterPosition == index) b.ivPage.setImageBitmap(bmp)
                }
            }''',
    "PdfViewer: رسم غير متزامن")

# ════════════════════════════════════════════════════════════
# فحص أولي
# ════════════════════════════════════════════════════════════
warn = []
for rel in [PKG + "ui/viewer/ImageViewerActivity.kt", PKG + "ui/viewer/PdfViewerActivity.kt", GRADLE]:
    if os.path.exists(P(rel)):
        t = rd(rel)
        for a, b in ("{}", "()"):
            if t.count(a) != t.count(b):
                warn.append(f"{rel}: عدم توازن {a}{b} ({t.count(a)} vs {t.count(b)})")
if os.path.exists(P(MAN)):
    import xml.dom.minidom
    try:
        xml.dom.minidom.parse(P(MAN))
    except Exception as e:
        warn.append(f"Manifest غير صالح XML: {e}")

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
  echo "[!] بعض التعديلات لم تُطبَّق. لم يتم commit. أرسل لي هذا الإخراج."
  exit $RC
fi

# --- مفتاح التوقيع (اختياري) ---
if [ ! -f keystore.properties ]; then
  if command -v keytool >/dev/null 2>&1 && [ -t 0 ]; then
    read -r -p "إنشاء مفتاح توقيع release الآن؟ (y/N) " ans
    if [ "$ans" = "y" ] || [ "$ans" = "Y" ]; then
      mkdir -p "$HOME/keys"
      KS="$HOME/keys/wifishare.jks"
      PASS="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
      if [ -f "$KS" ]; then
        echo "[!] $KS موجود بالفعل. لن أستبدله. أنشئ keystore.properties يدويًا."
      else
        keytool -genkeypair -v -keystore "$KS" -alias wifishare -keyalg RSA -keysize 2048 \
          -validity 10000 -storepass "$PASS" -keypass "$PASS" \
          -dname "CN=WifiShare, O=WifiShare, C=EG" >/dev/null 2>&1 \
          && {
            printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=wifishare\nkeyPassword=%s\n' "$KS" "$PASS" "$PASS" > keystore.properties
            chmod 600 keystore.properties "$KS"
            echo "[✓] المفتاح: $KS"
            echo "[!] خذ نسخة احتياطية من $KS و keystore.properties في مكان آمن."
            echo "    لو ضاع المفتاح لن تستطيع تحديث التطبيق على Play."
          } || echo "[!] فشل إنشاء المفتاح"
      fi
    fi
  else
    echo "[i] لإنشاء مفتاح التوقيع لاحقًا: شغّل السكربت من Termux مباشرة (تفاعليًا)."
  fi
fi

if command -v git >/dev/null 2>&1; then
  git add -A >/dev/null 2>&1
  $GIT commit -q -m "wsfix2: manifest, build, viewers" >/dev/null 2>&1 && echo "[✓] commit: wsfix2"
fi

echo
echo "[✓] انتهى السكربت 2. جرّب البناء:"
echo "    cd $PROJ && ./gradlew assembleDebug"
echo "    إصدار موقّع:  ./gradlew assembleRelease"
echo "    للتراجع: git reset --hard HEAD~1"

