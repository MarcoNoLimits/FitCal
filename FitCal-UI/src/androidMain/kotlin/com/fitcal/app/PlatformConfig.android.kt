package com.fitcal.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.fitcal.app.ads.AndroidAdManager
import com.fitcal.app.privacy.PrivacyConsent
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

actual val gatewayUrl: String get() = BuildConfig.GATEWAY_URL
actual val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
actual val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
actual val isDebugBuild: Boolean get() = BuildConfig.DEBUG
actual val appVersionName: String get() = BuildConfig.VERSION_NAME
actual val platformName: String get() = "android"
actual val googleWebClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID
actual val isGoogleSignInAvailable: Boolean get() = googleWebClientId.isNotBlank()
actual val isAppleSignInAvailable: Boolean get() = false

actual fun createSecureStringStore(): com.fitcal.shared.auth.SecureStringStore? =
    com.fitcal.app.auth.KeystoreStringStore(appContext)

lateinit var appContext: Context

/** False in JVM unit tests, where no Activity ever sets [appContext]. */
val isAppContextReady: Boolean get() = ::appContext.isInitialized

actual fun savePreference(key: String, value: String) {
    val sharedPref = appContext.getSharedPreferences("fitcal_prefs", Context.MODE_PRIVATE)
    sharedPref.edit().putString(key, value).apply()
}

actual fun loadPreference(key: String, defaultValue: String): String {
    val sharedPref = appContext.getSharedPreferences("fitcal_prefs", Context.MODE_PRIVATE)
    if (sharedPref.contains(key)) {
        return sharedPref.getString(key, defaultValue) ?: defaultValue
    }
    val legacyFitterPref = appContext.getSharedPreferences("fitter_prefs", Context.MODE_PRIVATE)
    if (legacyFitterPref.contains(key)) {
        return legacyFitterPref.getString(key, defaultValue) ?: defaultValue
    }
    val legacyPref = appContext.getSharedPreferences("macrovision_prefs", Context.MODE_PRIVATE)
    return legacyPref.getString(key, defaultValue) ?: defaultValue
}

/**
 * Binds SharedPreferences into the shared consent store.
 * Uses the "fitcal_prefs" file so consent persists across app restarts
 * and survives a process death mid-session.
 */
actual fun initPrivacyConsentStore() {
    PrivacyConsent.bindStorage(object : PrivacyConsent.ConsentStorage {
        private fun prefs() =
            appContext.getSharedPreferences("fitcal_prefs", Context.MODE_PRIVATE)
        private fun legacyPrefs() =
            appContext.getSharedPreferences("fitter_prefs", Context.MODE_PRIVATE)

        override fun getBoolean(key: String, default: Boolean): Boolean =
            if (prefs().contains(key)) prefs().getBoolean(key, default)
            else legacyPrefs().getBoolean(key, default)

        override fun putBoolean(key: String, value: Boolean) {
            prefs().edit().putBoolean(key, value).apply()
        }

        override fun getLong(key: String, default: Long): Long =
            if (prefs().contains(key)) prefs().getLong(key, default)
            else legacyPrefs().getLong(key, default)

        override fun putLong(key: String, value: Long) {
            prefs().edit().putLong(key, value).apply()
        }
    })
}

/**
 * Builds the consent storage. Uses applicationContext when available so it works
 * from any thread with no Activity; falls back to an in-memory store before init
 * so early composition cannot crash the consent gate.
 */
actual fun createConsentStorage(): PrivacyConsent.ConsentStorage {
    val fallback = mutableMapOf<String, Boolean>()

    return object : PrivacyConsent.ConsentStorage {
        private fun ready(): Boolean = ::appContext.isInitialized

        private fun prefs() = appContext.getSharedPreferences("fitcal_prefs", Context.MODE_PRIVATE)
        private fun legacyPrefs() = appContext.getSharedPreferences("fitter_prefs", Context.MODE_PRIVATE)

        override fun getBoolean(key: String, default: Boolean): Boolean =
            if (ready()) {
                if (prefs().contains(key)) prefs().getBoolean(key, default)
                else legacyPrefs().getBoolean(key, default)
            } else fallback[key] ?: default

        override fun putBoolean(key: String, value: Boolean) {
            if (ready()) prefs().edit().putBoolean(key, value).apply() else fallback[key] = value
        }

        override fun getLong(key: String, default: Long): Long =
            if (ready()) {
                if (prefs().contains(key)) prefs().getLong(key, default)
                else legacyPrefs().getLong(key, default)
            } else default

        override fun putLong(key: String, value: Long) {
            if (ready()) prefs().edit().putLong(key, value).apply()
        }
    }
}

/**
 * Android consent: Google UMP (User Messaging Platform), whose IAB TCF signal AdMob reads
 * directly. Any failure resolves to "no personalized ads".
 *
 * IMPORTANT: the caller MUST await this before MobileAds.initialize / ad loads.
 * Initializing first and prompting later is the defect this function exists to prevent.
 */
actual suspend fun requestPlatformAdConsent(): Boolean = suspendCancellableCoroutine { cont ->
    try {
        val activity = AndroidAdManager.currentActivityRef?.get()
        if (activity == null) {
            android.util.Log.w("FitCal_Privacy", "No activity; treating consent as denied")
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        android.util.Log.w("FitCal_Privacy", "UMP consent form error: ${formError.message}")
                    }
                    val canRequest = consentInformation.canRequestAds()
                    android.util.Log.i("FitCal_Privacy", "UMP consent resolved. canRequestAds=$canRequest")
                    cont.resume(canRequest)
                }
            },
            { requestError ->
                android.util.Log.w("FitCal_Privacy", "UMP consent request error: ${requestError.message}")
                cont.resume(consentInformation.canRequestAds())
            }
        )
    } catch (t: Throwable) {
        // Fail closed: an unexpected CMP failure must never silently become consent.
        android.util.Log.w("FitCal_Privacy", "CMP exception: ${t.message}")
        cont.resume(false)
    }
}

actual fun isAdPrivacyOptionsRequired(): Boolean {
    val activity = AndroidAdManager.currentActivityRef?.get() ?: return false
    return UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
}

actual fun showAdPrivacyOptions(onDone: () -> Unit) {
    val activity = AndroidAdManager.currentActivityRef?.get()
    if (activity == null) {
        onDone()
        return
    }
    UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
        if (formError != null) {
            android.util.Log.w("FitCal_Privacy", "UMP privacy options error: ${formError.message}")
        }
        onDone()
    }
}

actual fun getCurrentTimeString(): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date())
}

actual fun getCurrentHourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

actual fun getCurrentDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
}

actual fun getCurrentEpochMillis(): Long = System.currentTimeMillis()

actual fun compressImage(imageBytes: ByteArray): ByteArray {
    try {
        // Parse EXIF orientation to detect vertical/portrait rotation
        val inputStream = ByteArrayInputStream(imageBytes)
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return imageBytes

        // Rotate the bitmap if orientation is sideways
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = rotatedBitmap
            }
        }

        val width = bitmap.width
        val height = bitmap.height
        val maxDimension = 768
        
        val (targetWidth, targetHeight) = if (width > maxDimension || height > maxDimension) {
            if (width > height) {
                val ratio = height.toFloat() / width
                Pair(maxDimension, (maxDimension * ratio).toInt())
            } else {
                val ratio = width.toFloat() / height
                Pair((maxDimension * ratio).toInt(), maxDimension)
            }
        } else {
            Pair(width, height)
        }
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val compressedBytes = outputStream.toByteArray()
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        bitmap.recycle()
        
        return compressedBytes
    } catch (e: Exception) {
        return imageBytes
    }
}

private val androidAdManager by lazy {
    com.fitcal.app.ads.AndroidAdManager { appContext }
}

actual fun getPlatformAdManager(): com.fitcal.app.ads.AdManager = androidAdManager

actual fun syncPlatformMealReminders(
    enabled: Boolean,
    reminders: List<com.fitcal.app.notifications.MealReminder>
) {
    if (::appContext.isInitialized) {
        com.fitcal.app.notifications.MealReminderReceiver.syncAlarms(appContext, enabled, reminders)
    }
}

private fun mealPhotoDir(): java.io.File =
    java.io.File(appContext.filesDir, "meal_photos").apply { mkdirs() }

actual fun saveMealPhoto(mealId: String, jpegBytes: ByteArray): String? = try {
    if (!isAppContextReady || jpegBytes.isEmpty()) null
    else {
        // Ids are UUIDs; anything else is reduced to safe file-name characters.
        val safeId = mealId.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { return null }
        java.io.File(mealPhotoDir(), "$safeId.jpg").apply { writeBytes(jpegBytes) }.absolutePath
    }
} catch (_: Exception) {
    null
}

actual fun pruneMealPhotos(keepMealIds: Set<String>) {
    if (!isAppContextReady) return
    try {
        mealPhotoDir().listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in keepMealIds) file.delete()
        }
    } catch (_: Exception) {
        // Best effort: a leftover file is retried on the next launch.
    }
}

actual fun mealPhotoModel(path: String?): Any? =
    path?.let { java.io.File(it) }?.takeIf { it.isFile }

actual fun updateHomeWidget(summary: WidgetSummary) {
    if (!isAppContextReady) return
    try {
        com.fitcal.app.widget.TodayWidgetProvider.save(appContext, summary)
    } catch (_: Exception) {
        // The widget is a convenience; never let it break the app.
    }
}

actual fun syncWeeklySummaryNotification(enabled: Boolean) {
    if (isAppContextReady) com.fitcal.app.notifications.WeeklySummaryReceiver.sync(appContext, enabled)
}

actual fun shareTextFile(fileName: String, mimeType: String, content: String, chooserTitle: String): Boolean {
    if (!isAppContextReady) return false
    return try {
        val dir = java.io.File(appContext.cacheDir, "exports").apply { mkdirs() }
        // Only the latest export is kept; older files are removed.
        dir.listFiles()?.forEach { it.delete() }
        val safeName = fileName.filter { it.isLetterOrDigit() || it in "-_." }.ifEmpty { "fitcal-export.csv" }
        val file = java.io.File(dir, safeName).apply { writeText(content, Charsets.UTF_8) }
        val uri = androidx.core.content.FileProvider.getUriForFile(appContext, "${appContext.packageName}.exports", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, safeName)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(send, chooserTitle)
        val activity = com.fitcal.app.ads.AndroidAdManager.currentActivityRef?.get()
        if (activity != null) {
            activity.startActivity(chooser)
        } else {
            appContext.startActivity(chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (_: Exception) {
        false
    }
}
