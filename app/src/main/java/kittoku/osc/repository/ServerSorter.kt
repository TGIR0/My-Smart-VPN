package kittoku.osc.repository

import android.util.Log

/**
 * ===============================================
 * QoS ALGORITHM DOCUMENTATION
 * ===============================================
 * 
 * FORMULA:
 * ---------
 * QualityScore = (0.6 × NormalizedSpeed) + (0.4 × NormalizedPing)
 * 
 * WHERE:
 * ------
 * 1. Effective Speed = Speed / (Sessions + 1)
 *    - The "+1" simulates the user's own connection being added
 *    - Prevents division by zero when sessions = 0
 *    - Example: 100 Mbps with 49 sessions = 100/(49+1) = 2.0 Mbps effective per user
 * 
 * 2. NormalizedSpeed (0.0 to 1.0):
 *    - Higher effective speed = Higher score
 *    - Formula: (EffectiveSpeed - MinSpeed) / (MaxSpeed - MinSpeed)
 * 
 * 3. NormalizedPing (0.0 to 1.0):
 *    - Lower ping = Higher score (inverted normalization)
 *    - Formula: 1.0 - ((Ping - MinPing) / (MaxPing - MinPing))
 * 
 * WEIGHTS:
 * --------
 * - Speed Weight: 60% (0.6) - More important for throughput
 * - Ping Weight:  40% (0.4) - Important for responsiveness
 * 
 * STRICT FILTER:
 * --------------
 * - Servers with Ping = -1 (timeout) are EXCLUDED completely
 * - They are never cached, displayed, or selected for connection
 * 
 * TRIGGER POINTS (Just-in-Time Sorting):
 * --------------------------------------
 * 1. On Connect Button Click → Fresh sort → Pick Index 0
 * 2. On Server List Open     → Fresh sort → Display sorted list
 * 
 * ===============================================
 */
object ServerSorter {
    private const val TAG = "ServerSorter"
    
    // WEIGHTS: Speed more important than ping for VPN throughput
    private const val WEIGHT_SPEED = 0.6
    private const val WEIGHT_PING = 0.4
    
    /**
     * Data class to hold normalized scores for a server
     */
    data class ServerScore(
        val server: SstpServer,
        val effectiveSpeed: Double,
        val normalizedSpeed: Double,
        val normalizedPing: Double,
        val qualityScore: Double
    )
    
    /**
     * Calculate Quality Score for all servers with proper min/max normalization
     * 
     * @param servers List of servers (should already be filtered for timeouts)
     * @return List of ServerScore with calculated quality scores
     */
    fun calculateScores(servers: List<SstpServer>): List<ServerScore> {
        if (servers.isEmpty()) return emptyList()
        
        // Filter out timeout servers first
        val validServers = servers.filter { it.realPing > 0 }
        if (validServers.isEmpty()) return emptyList()
        
        // Calculate effective speed for all servers
        val effectiveSpeeds = validServers.map { getEffectiveSpeed(it) }
        val pings = validServers.map { it.realPing.toDouble() }
        
        // Get min/max for normalization
        val minSpeed = effectiveSpeeds.minOrNull() ?: 0.0
        val maxSpeed = effectiveSpeeds.maxOrNull() ?: 1.0
        val minPing = pings.minOrNull() ?: 0.0
        val maxPing = pings.maxOrNull() ?: 1000.0
        
        val speedRange = maxSpeed - minSpeed
        val pingRange = maxPing - minPing
        
        return validServers.map { server ->
            val effSpeed = getEffectiveSpeed(server)
            
            // Normalize speed (0-1, higher is better)
            val normSpeed = if (speedRange > 0) {
                (effSpeed - minSpeed) / speedRange
            } else {
                1.0  // All same speed
            }
            
            // Normalize ping (0-1, lower ping = higher score)
            val normPing = if (pingRange > 0) {
                1.0 - ((server.realPing.toDouble() - minPing) / pingRange)
            } else {
                1.0  // All same ping
            }
            
            // Final weighted score
            val qualityScore = (WEIGHT_SPEED * normSpeed) + (WEIGHT_PING * normPing)
            
            ServerScore(
                server = server,
                effectiveSpeed = effSpeed,
                normalizedSpeed = normSpeed,
                normalizedPing = normPing,
                qualityScore = qualityScore
            )
        }
    }
    
    /**
     * Calculate Effective Speed = Speed / (Sessions + 1)
     */
    private fun getEffectiveSpeed(server: SstpServer): Double {
        val sessions = server.sessions + 1  // +1 to simulate our connection
        return server.speed.toDouble() / sessions.toDouble()
    }
    
    /**
     * Calculate simple score for a single server (for logging/display)
     * Uses absolute scale, not relative normalization
     */
    fun calculateScore(server: SstpServer): Double {
        if (server.realPing <= 0) return -1.0  // Timeout = discard
        return getEffectiveSpeed(server)
    }
    
    /**
     * Sort servers by Quality Score (highest first)
     * STRICT FILTER: Excludes all timeout servers
     * 
     * @param servers List to sort
     * @return Sorted list (best servers first), timeouts excluded
     */
    fun sortByScore(servers: List<SstpServer>): List<SstpServer> {
        if (servers.isEmpty()) return emptyList()
        
        val scores = calculateScores(servers)
        val sorted = scores.sortedByDescending { it.qualityScore }
        
        // Log top 3 for debugging
        sorted.take(3).forEachIndexed { index, score ->
            Log.d(TAG, "Top ${index + 1}: ${score.server.hostName} | " +
                    "Score=${String.format("%.3f", score.qualityScore)} | " +
                    "EffSpeed=${String.format("%.1f", score.effectiveSpeed / 1_000_000)}Mbps | " +
                    "Ping=${score.server.realPing}ms")
        }
        
        val result = sorted.map { it.server }
        Log.d(TAG, "Sorted ${result.size} servers by QualityScore (${servers.size - result.size} timeouts filtered)")
        
        return result
    }
    
    /**
     * Get top N servers by Quality Score
     */
    fun getTopServers(servers: List<SstpServer>, count: Int = 3): List<SstpServer> {
        return sortByScore(servers).take(count)
    }
    
    /**
     * Get the BEST server (highest Quality Score)
     * Returns null if all servers timed out
     */
    fun getBestServer(servers: List<SstpServer>): SstpServer? {
        return sortByScore(servers).firstOrNull()
    }
    
    /**
     * Filter servers - removes all timeout/unreachable servers
     * Use BEFORE caching to ensure only valid servers are stored
     */
    fun filterReachable(servers: List<SstpServer>): List<SstpServer> {
        val reachable = servers.filter { it.realPing > 0 }
        Log.d(TAG, "Filtered: ${reachable.size} reachable / ${servers.size} total")
        return reachable
    }
    
    /**
     * Get quality score for display in UI
     * Returns score as percentage (0-100)
     */
    fun getDisplayScore(server: SstpServer, allServers: List<SstpServer>): Int {
        val scores = calculateScores(allServers)
        val score = scores.find { it.server.hostName == server.hostName }
        return ((score?.qualityScore ?: 0.0) * 100).toInt()
    }
}
