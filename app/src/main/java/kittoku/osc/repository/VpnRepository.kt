package kittoku.osc.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class SstpServer(
    val hostName: String,
    val ip: String,
    val country: String,
    val countryCode: String,
    val speed: Long,
    val sessions: Long,
    val ping: Int
)

class VpnRepository {
    private val client = OkHttpClient()
    private val SERVER_URL = "https://raw.githubusercontent.com/mahdigholamipak/vpn-list-mirror/refs/heads/main/server_list.csv"

    fun fetchSstpServers(onResult: (List<SstpServer>) -> Unit) {
        val request = Request.Builder().url(SERVER_URL).build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                val csvData = response.body?.string()
                val servers = if (csvData != null) {
                    parseCsv(csvData)
                } else {
                    emptyList()
                }
                onResult(servers)
            } catch (e: IOException) {
                e.printStackTrace()
                onResult(emptyList()) // Return empty list on network error
            }
        }.start()
    }

    private fun parseCsv(data: String): List<SstpServer> {
        val servers = mutableListOf<SstpServer>()
        val lines = data.split('\n').filter { it.isNotBlank() }

        // Skip header line which starts with # or * usually, but strictly speaking we drop the first one if it's header-like
        // The prompt implies robust parsing. CSV usually has a header. The previous code dropped 1.
        // We will inspect lines.
        for (line in lines) {
            if (line.startsWith("#") || line.startsWith("Hostname") || line.contains("Quality")) continue

            try {
                val p = line.split(",")
                // We need at least up to index 12 (SSTP)
                if (p.size <= 12) continue

                // 4. Filters:
                // Exclude CountryCode (Index 6) if it equals "IR"
                val countryCode = p[6].trim()
                if (countryCode.equals("IR", ignoreCase = true)) continue

                // Include ONLY if SSTP (Index 12) equals "1"
                val sstpSupport = p[12].trim()
                if (sstpSupport != "1") continue

                // 2. Hostname Fix (CRITICAL)
                // Parse Index 0. Check if it ends with .opengw.net. If NOT, append it.
                var hostName = p[0].trim()
                if (!hostName.endsWith(".opengw.net")) {
                    hostName += ".opengw.net"
                }

                // 3. Data Types: Parse Speed (Index 4) and Sessions (Index 7) as Long
                // Handling empty/null strings safely
                
                // Index 1: IP
                val ip = p[1].trim()
                
                // Index 3: Ping
                val ping = p[3].trim().toIntOrNull() ?: 0
                
                // Index 4: Speed
                val speed = p[4].trim().toLongOrNull() ?: 0L
                
                // Index 5: Country Name
                val country = p[5].trim()
                
                // Index 7: Sessions
                val sessions = p[7].trim().toLongOrNull() ?: 0L

                val server = SstpServer(
                    hostName = hostName,
                    ip = ip,
                    country = country,
                    countryCode = countryCode,
                    speed = speed,
                    sessions = sessions,
                    ping = ping
                )
                servers.add(server)

            } catch (e: Exception) {
                // Ignore malformed lines
            }
        }

        // 5. Sorting: Sort by Sessions (Descending) + Speed (Descending)
        return servers.sortedWith(compareByDescending<SstpServer> { it.sessions }.thenByDescending { it.speed })
    }
}
