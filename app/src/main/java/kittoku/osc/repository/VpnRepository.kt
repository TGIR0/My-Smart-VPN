package kittoku.osc.repository

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Data class representing an SSTP-compatible VPN server
 */
data class SstpServer(
    val hostName: String,
    val ip: String,
    val country: String,
    val countryCode: String,
    val speed: Long,
    val sessions: Long,
    val ping: Int
)

/**
 * Repository for fetching VPN server list from VPNGate mirror
 * 
 * CSV Format (15 columns, 0-indexed):
 * 0: HostName
 * 1: IP
 * 2: Score
 * 3: Ping
 * 4: Speed
 * 5: CountryLong
 * 6: CountryShort (CountryCode)
 * 7: NumVpnSessions
 * 8: Uptime
 * 9: TotalUsers
 * 10: TotalTraffic
 * 11: LogType
 * 12: Operator
 * 13: Message
 * 14: OpenVPN_ConfigData_Base64
 */
class VpnRepository {
    companion object {
        private const val TAG = "VpnRepository"
        private const val SERVER_URL = "https://raw.githubusercontent.com/mahdigholamipak/vpn-list-mirror/refs/heads/main/server_list.csv"
        private const val OPENGW_SUFFIX = ".opengw.net"
        private const val EXCLUDED_COUNTRY_CODE = "IR"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch SSTP servers from the VPNGate mirror CSV
     * @param onResult Callback with the list of parsed servers
     */
    fun fetchSstpServers(onResult: (List<SstpServer>) -> Unit) {
        val request = Request.Builder()
            .url(SERVER_URL)
            .build()

        Thread {
            try {
                Log.d(TAG, "Fetching server list from: $SERVER_URL")
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code}")
                    onResult(emptyList())
                    return@Thread
                }
                
                val csvData = response.body?.string()
                if (csvData.isNullOrBlank()) {
                    Log.e(TAG, "Empty response body")
                    onResult(emptyList())
                    return@Thread
                }
                
                Log.d(TAG, "Received CSV data, length: ${csvData.length}")
                val servers = parseCsv(csvData)
                Log.d(TAG, "Parsed ${servers.size} servers")
                onResult(servers)
                
            } catch (e: IOException) {
                Log.e(TAG, "Network error fetching servers", e)
                onResult(emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error fetching servers", e)
                onResult(emptyList())
            }
        }.start()
    }

    /**
     * Parse CSV data into list of SstpServer objects
     * 
     * Applies the following rules:
     * 1. Hostname Fix: Append ".opengw.net" if not already present
     * 2. Data Types: Parse Speed (Index 4) and Sessions (Index 7) as Long
     * 3. Filters: Exclude CountryCode "IR"
     * 4. Sorting: Sort by Sessions (Descending) + Speed (Descending)
     */
    private fun parseCsv(data: String): List<SstpServer> {
        val servers = mutableListOf<SstpServer>()
        val lines = data.lines().filter { it.isNotBlank() }
        
        Log.d(TAG, "Processing ${lines.size} lines")
        
        for ((index, line) in lines.withIndex()) {
            // Skip metadata and header lines
            if (line.startsWith("*") || 
                line.startsWith("#") || 
                line.contains("HostName", ignoreCase = true)) {
                Log.d(TAG, "Skipping header/metadata line $index: ${line.take(50)}")
                continue
            }
            
            try {
                val server = parseServerLine(line)
                if (server != null) {
                    servers.add(server)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing line $index: ${e.message}")
                // Continue to next line - don't fail on single line errors
            }
        }
        
        Log.d(TAG, "Parsed ${servers.size} valid servers before sorting")
        
        // Sort by Sessions (Descending) + Speed (Descending)
        return servers.sortedWith(
            compareByDescending<SstpServer> { it.sessions }
                .thenByDescending { it.speed }
        )
    }
    
    /**
     * Parse a single CSV line into an SstpServer object
     * @return SstpServer if valid, null if should be filtered out
     */
    private fun parseServerLine(line: String): SstpServer? {
        // Split by comma - need at least 8 columns (0-7 for required fields)
        val parts = line.split(",")
        
        if (parts.size < 8) {
            Log.w(TAG, "Insufficient columns (${parts.size}), need at least 8")
            return null
        }
        
        // Parse CountryCode (Index 6) - filter out excluded countries
        val countryCode = parts.getOrNull(6)?.trim().orEmpty()
        if (countryCode.equals(EXCLUDED_COUNTRY_CODE, ignoreCase = true)) {
            return null // Exclude this country
        }
        
        // Parse Hostname (Index 0) with CRITICAL fix:
        // Append ".opengw.net" if not already present
        var hostName = parts[0].trim()
        if (hostName.isEmpty()) {
            Log.w(TAG, "Empty hostname")
            return null
        }
        
        if (!hostName.endsWith(OPENGW_SUFFIX, ignoreCase = true)) {
            hostName += OPENGW_SUFFIX
        }
        
        // Parse other required fields
        val ip = parts.getOrNull(1)?.trim().orEmpty()
        if (ip.isEmpty()) {
            Log.w(TAG, "Empty IP address for host: $hostName")
            return null
        }
        
        // Parse Ping (Index 3) - default to 0 if invalid
        val ping = parts.getOrNull(3)?.trim()?.toIntOrNull() ?: 0
        
        // Parse Speed (Index 4) as Long - default to 0 if invalid
        val speed = parts.getOrNull(4)?.trim()?.toLongOrNull() ?: 0L
        
        // Parse Country name (Index 5)
        val country = parts.getOrNull(5)?.trim().orEmpty()
        
        // Parse NumVpnSessions (Index 7) as Long - default to 0 if invalid
        val sessions = parts.getOrNull(7)?.trim()?.toLongOrNull() ?: 0L
        
        return SstpServer(
            hostName = hostName,
            ip = ip,
            country = country,
            countryCode = countryCode,
            speed = speed,
            sessions = sessions,
            ping = ping
        )
    }
}
