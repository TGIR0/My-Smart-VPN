package kittoku.osc.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.json.JSONArray

/**
 * Manages hostname input history for autocomplete functionality
 * Stores up to MAX_HISTORY_SIZE recent hostnames
 */
object HostnameHistoryManager {
    private const val PREF_KEY = "hostname_history"
    private const val MAX_HISTORY_SIZE = 10

    /**
     * Save a hostname to history (adds to front, removes duplicates)
     */
    fun saveHostname(context: Context, hostname: String) {
        if (hostname.isBlank()) return
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val history = getHistory(context).toMutableList()
        
        // Remove if already exists (will re-add at front)
        history.remove(hostname)
        
        // Add to front
        history.add(0, hostname)
        
        // Trim to max size
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.lastIndex)
        }
        
        // Save as JSON array
        val jsonArray = JSONArray(history)
        prefs.edit().putString(PREF_KEY, jsonArray.toString()).apply()
    }

    /**
     * Get all hostnames from history
     */
    fun getHistory(context: Context): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val jsonStr = prefs.getString(PREF_KEY, null) ?: return emptyList()
        
        return try {
            val jsonArray = JSONArray(jsonStr)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clear all hostname history
     */
    fun clearHistory(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().remove(PREF_KEY).apply()
    }
}
