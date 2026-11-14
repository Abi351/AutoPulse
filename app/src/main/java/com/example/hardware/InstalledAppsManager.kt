package com.example.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)

class InstalledAppsManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pm = context.packageManager

    // Cache LRU d'icônes 64x64 (~2 Mo max pour 128 icônes) pour éviter tout lag lors du défilement
    private val iconCache = LruCache<String, ImageBitmap>(128)

    private val _apps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val apps: StateFlow<List<InstalledAppInfo>> = _apps.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            if (pkg != null) {
                iconCache.remove(pkg)
            }
            scope.launch { loadInstalledApps() }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(packageReceiver, filter)
        }
    }

    /**
     * Charge uniquement les noms & packages (quasi instantané <15 ms) sans décoder les 200+ icônes d'un coup.
     */
    suspend fun loadInstalledApps() = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val ownPackage = context.packageName
        val seenPackages = HashSet<String>(resolveInfos.size)
        val result = ArrayList<InstalledAppInfo>(resolveInfos.size)

        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == ownPackage || !seenPackages.add(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            result.add(InstalledAppInfo(packageName = pkg, label = label))
        }

        result.sortBy { it.label.lowercase() }
        _apps.value = result
    }

    fun getCachedIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    /**
     * Charge et met en cache l'icône d'une seule app à la demande (appelé uniquement pour les tuiles visibles).
     */
    suspend fun getOrLoadIcon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            iconCache.get(packageName)?.let { return@withContext it }
            val loaded = runCatching {
                val drawable = pm.getApplicationIcon(packageName)
                drawableToImageBitmap(drawable)
            }.getOrNull()
            if (loaded != null) {
                iconCache.put(packageName, loaded)
            }
            loaded
        }
    }

    fun launchApp(packageName: String): Boolean {
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun isDefaultHomeLauncher(): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun openHomeLauncherSettings() {
        val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(homeSettingsIntent)
            true
        }.getOrDefault(false)

        if (!launched) {
            val chooser = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(Intent.createChooser(chooser, "Choisir le Launcher d'accueil")) }
        }
    }

    fun release() {
        runCatching { context.unregisterReceiver(packageReceiver) }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        val targetSize = 96
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            val scaled = if (bmp.width > targetSize || bmp.height > targetSize) {
                Bitmap.createScaledBitmap(bmp, targetSize, targetSize, true)
            } else {
                bmp
            }
            return scaled.asImageBitmap()
        }
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
