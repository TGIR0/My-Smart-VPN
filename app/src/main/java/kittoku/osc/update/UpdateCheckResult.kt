package kittoku.osc.update

/**
 * Data class representing the result of an update check
 */
data class UpdateCheckResult(
    val updateAvailable: Boolean,
    val latestVersion: String?,
    val currentVersion: String,
    val downloadUrl: String?,
    val releaseNotes: String?,
    val releaseDate: String?,
    val apkSize: Long = 0L,
    val error: String? = null
) {
    companion object {
        fun noUpdate(currentVersion: String) = UpdateCheckResult(
            updateAvailable = false,
            latestVersion = currentVersion,
            currentVersion = currentVersion,
            downloadUrl = null,
            releaseNotes = null,
            releaseDate = null
        )
        
        fun error(currentVersion: String, errorMessage: String) = UpdateCheckResult(
            updateAvailable = false,
            latestVersion = null,
            currentVersion = currentVersion,
            downloadUrl = null,
            releaseNotes = null,
            releaseDate = null,
            error = errorMessage
        )
    }
}
