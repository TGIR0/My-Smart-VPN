package kittoku.osc.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manager for checking and installing app updates from GitHub Releases
 * 
 * Features:
 * - Checks GitHub Releases API for new versions
 * - Semantic version comparison
 * - APK download with progress tracking
 * - Installation via FileProvider
 */
object UpdateManager {
    private const val TAG = "UpdateManager"
    
    // GitHub repository info - UPDATE THESE FOR YOUR REPO
    private const val GITHUB_OWNER = "mahdigholamipak"
    private const val GITHUB_REPO = "My-Smart-VPN"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * Check for updates from GitHub Releases
     */
    fun checkForUpdates(context: Context, callback: (UpdateCheckResult) -> Unit) {
        val currentVersion = getCurrentVersion(context)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(RELEASES_API_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        callback(UpdateCheckResult.error(currentVersion, "HTTP ${response.code}"))
                    }
                    return@launch
                }
                
                val jsonStr = response.body?.string() ?: ""
                val json = JSONObject(jsonStr)
                
                val tagName = json.optString("tag_name", "").removePrefix("v")
                val releaseNotes = json.optString("body", "")
                val releaseDate = json.optString("published_at", "")
                
                // Find APK asset
                val assets = json.optJSONArray("assets") ?: JSONArray()
                var downloadUrl: String? = null
                var apkSize: Long = 0
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", 0)
                        break
                    }
                }
                
                val updateAvailable = isNewerVersion(tagName, currentVersion)
                
                val result = UpdateCheckResult(
                    updateAvailable = updateAvailable,
                    latestVersion = tagName,
                    currentVersion = currentVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    releaseDate = releaseDate,
                    apkSize = apkSize
                )
                
                withContext(Dispatchers.Main) {
                    callback(result)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                withContext(Dispatchers.Main) {
                    callback(UpdateCheckResult.error(currentVersion, e.message ?: "Unknown error"))
                }
            }
        }
    }
    
    /**
     * Download and install APK update
     */
    fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Downloading APK from: $downloadUrl")
                
                val request = Request.Builder()
                    .url(downloadUrl)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Download failed: HTTP ${response.code}")
                    }
                    return@launch
                }
                
                val body = response.body ?: run {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Empty response body")
                    }
                    return@launch
                }
                
                val contentLength = body.contentLength()
                val apkFile = File(context.cacheDir, "update.apk")
                
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead: Long = 0
                    val input = body.byteStream()
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
                
                Log.d(TAG, "APK downloaded successfully: ${apkFile.absolutePath}")
                
                // Install the APK
                withContext(Dispatchers.Main) {
                    try {
                        installApk(context, apkFile)
                        onComplete(true, null)
                    } catch (e: Exception) {
                        onComplete(false, "Installation failed: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, "Download error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Install APK via FileProvider (for Android 7.0+)
     */
    private fun installApk(context: Context, apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
    }
    
    /**
     * Get current app version from BuildConfig
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            // Use BuildConfig.VERSION_NAME directly for accurate version
            kittoku.osc.BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            Log.e(TAG, "Error getting version from BuildConfig, falling back to PackageInfo", e)
            try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                packageInfo.versionName ?: "0.0.0"
            } catch (e2: Exception) {
                "0.0.0"
            }
        }
    }
    
    /**
     * Check if auto-update check on startup is enabled
     */
    fun shouldAutoCheck(context: Context): Boolean {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean("AUTO_CHECK_UPDATES", true) // Default: enabled
    }
    
    /**
     * Compare semantic versions (e.g., "1.2.3" vs "1.3.0")
     * Returns true if newVersion > currentVersion
     */
    fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        try {
            val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLen = maxOf(newParts.size, currentParts.size)
            
            for (i in 0 until maxLen) {
                val newPart = newParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }
                
                if (newPart > currentPart) return true
                if (newPart < currentPart) return false
            }
            
            return false // Versions are equal
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $newVersion vs $currentVersion", e)
            return false
        }
    }
}
