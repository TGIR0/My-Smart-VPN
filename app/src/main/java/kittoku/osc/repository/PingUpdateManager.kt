package kittoku.osc.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * REQUIREMENT #1: Real-Time UI Reactivity
 * 
 * Manages ping update broadcasts so ServerListFragment can receive
 * live updates when pings are measured in background (e.g., from HomeFragment)
 * 
 * Uses LocalBroadcastManager for efficient in-app communication
 */
object PingUpdateManager {
    private const val TAG = "PingUpdateManager"
    
    const val ACTION_PING_UPDATE = "kittoku.osc.PING_UPDATE"
    const val ACTION_PING_COMPLETE = "kittoku.osc.PING_COMPLETE"
    const val EXTRA_SERVER_JSON = "server_json"
    const val EXTRA_PROGRESS = "progress"
    const val EXTRA_TOTAL = "total"
    
    private val gson = com.google.gson.Gson()
    
    /**
     * Broadcast a single server ping update for live UI refresh
     */
    fun notifyServerPingUpdate(context: Context, server: SstpServer) {
        try {
            val intent = Intent(ACTION_PING_UPDATE).apply {
                putExtra(EXTRA_SERVER_JSON, gson.toJson(server))
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast ping update: ${e.message}")
        }
    }
    
    /**
     * Broadcast progress update during ping measurement
     */
    fun notifyProgress(context: Context, current: Int, total: Int) {
        try {
            val intent = Intent(ACTION_PING_UPDATE).apply {
                putExtra(EXTRA_PROGRESS, current)
                putExtra(EXTRA_TOTAL, total)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast progress: ${e.message}")
        }
    }
    
    /**
     * Broadcast when all pings are complete so UI can do final sort
     */
    fun notifyPingComplete(context: Context, servers: List<SstpServer>) {
        try {
            val intent = Intent(ACTION_PING_COMPLETE).apply {
                putExtra(EXTRA_SERVER_JSON, gson.toJson(servers))
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
            Log.d(TAG, "Ping complete broadcast sent with ${servers.size} servers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast ping complete: ${e.message}")
        }
    }
    
    /**
     * Parse server from JSON (for receiver)
     */
    fun parseServer(json: String): SstpServer? {
        return try {
            gson.fromJson(json, SstpServer::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse server list from JSON (for receiver)
     */
    fun parseServerList(json: String): List<SstpServer>? {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<SstpServer>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }
}
