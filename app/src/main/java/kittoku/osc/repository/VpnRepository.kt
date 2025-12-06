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
    val ping: Int,
    val isSstp: Boolean
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
                if (csvData != null) {
                    val servers = parseCsv(csvData)
                    onResult(servers)
                }
            } catch (e: IOException) { e.printStackTrace() }
        }.start()
    }

    private fun parseCsv(data: String): List<SstpServer> {
        val servers = mutableListOf<SstpServer>()
        val lines = data.split('\n').filter { it.isNotBlank() }

        for (line in lines) {
            if (line.startsWith("#") || line.startsWith("*")) continue

            val p = line.split(",")

            if (p.size > 13) { // Use a safer size check
                try {
                    // The original isSstp check was incorrect for the provided CSV format.
                    // For now, we assume all servers from this list are potential SSTP servers.
                    val isSstp = true
                    val countryCode = p[6]

                    if (isSstp && !countryCode.equals("IR", ignoreCase = true)) {

                        val speedVal = p[4].toLongOrNull() ?: 0L
                        val sessionsVal = p[7].toLongOrNull() ?: 0L
                        val pingVal = p[3].toIntOrNull() ?: 999

                        servers.add(SstpServer(
                            hostName = p[0],
                            ip = p[1],
                            country = p[5],
                            countryCode = countryCode,
                            speed = speedVal,
                            sessions = sessionsVal,
                            ping = pingVal,
                            isSstp = true
                        ))
                    }
                } catch (e: Exception) {
                  // silent catch
                }
            }
        }
        return servers.sortedWith(compareByDescending<SstpServer> { it.sessions }.thenBy { it.ping })
    }
}
