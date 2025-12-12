package kittoku.osc.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import kittoku.osc.R

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        
        // Automatic update check on startup (if enabled)
        checkForUpdatesOnStartup()
    }
    
    /**
     * Check for updates silently in background on startup
     * Only shows dialog if update is available
     */
    private fun checkForUpdatesOnStartup() {
        if (!kittoku.osc.update.UpdateManager.shouldAutoCheck(this)) {
            return
        }
        
        kittoku.osc.update.UpdateManager.checkForUpdates(this) { result ->
            if (result.updateAvailable && result.downloadUrl != null) {
                // Show custom update dialog
                kittoku.osc.update.UpdateDialog(
                    context = this,
                    result = result,
                    onUpdateClick = {
                        downloadAndInstallUpdate(result.downloadUrl)
                    },
                    onDismiss = {
                        // User chose "Later"
                    }
                ).show()
            }
        }
    }
    
    /**
     * Download and install the update APK
     */
    @Suppress("DEPRECATION")
    private fun downloadAndInstallUpdate(url: String) {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Downloading update...")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }
        
        kittoku.osc.update.UpdateManager.downloadAndInstall(
            this,
            url,
            onProgress = { progress ->
                progressDialog.progress = progress
            },
            onComplete = { success, error ->
                progressDialog.dismiss()
                if (!success) {
                    android.widget.Toast.makeText(
                        this,
                        "Download failed: $error",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
