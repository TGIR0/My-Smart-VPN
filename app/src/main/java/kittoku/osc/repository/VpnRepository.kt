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

        // Skip header line which starts with #
        for (line in lines.drop(1)) {
            if (line.startsWith("#") || line.startsWith("*")) continue

            try {
                val p = line.split(",")
                if (p.size < 8) continue // Basic check for enough columns

                val countryCode = p[6].trim()

                // CRITICAL FIX: The SSTP port is in column 12, not a simple 0/1 flag.
                // We will check if the SSTP port is available and not 0.
                val sstpPort = p.getOrNull(12)?.trim()?.toIntOrNull() ?: 0

                if (sstpPort > 0 && countryCode.equals("IR", ignoreCase = true).not()) {
                    var hostName = p[0].trim()
                    if (!hostName.endsWith(".opengw.net")) {
                        hostName += ".opengw.net"
                    }

                    val server = SstpServer(
                        hostName = hostName,
                        ip = p[1].trim(),
                        ping = p[3].trim().toIntOrNull() ?: 0,
                        speed = p[4].trim().toLongOrNull() ?: 0L,
                        country = p[5].trim(),
                        countryCode = countryCode,
                        sessions = p[7].trim().toLongOrNull() ?: 0L
                    )
                    servers.add(server)
                }
            } catch (e: Exception) {
                // Ignore malformed lines and continue parsing other valid lines
                println("Skipping malformed line: $line")
            }
        }

        return servers.sortedByDescending { it.sessions }
    }
}
