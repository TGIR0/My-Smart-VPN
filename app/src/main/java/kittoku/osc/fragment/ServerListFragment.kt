package kittoku.osc.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kittoku.osc.R
import kittoku.osc.adapter.ServerListAdapter
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.repository.VpnRepository
import kittoku.osc.service.ACTION_VPN_STATUS_CHANGED

class ServerListFragment : Fragment(R.layout.fragment_server_list) {
    companion object {
        private const val TAG = "ServerListFragment"
    }
    
    private lateinit var serverListAdapter: ServerListAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var txtStatus: TextView
    private lateinit var prefs: SharedPreferences
    private val vpnRepository = VpnRepository()
    
    // Track current connection status
    private var currentStatus = "DISCONNECTED"

    /**
     * BroadcastReceiver for VPN status changes
     * This catches real-time status updates from SstpVpnService
     */
    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("status")?.let { status ->
                Log.d(TAG, "Received VPN status broadcast: $status")
                currentStatus = status
                updateStatusUI(status)
            }
        }
    }
    
    /**
     * SharedPreferences listener for ROOT_STATE changes
     * This is a backup mechanism in case broadcasts are missed
     */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        if (key == OscPrefKey.ROOT_STATE.name) {
            val isConnected = getBooleanPrefValue(OscPrefKey.ROOT_STATE, sharedPrefs)
            val newStatus = if (isConnected) "CONNECTED" else "DISCONNECTED"
            Log.d(TAG, "ROOT_STATE changed: $isConnected -> status: $newStatus")
            
            // Only update if broadcast hasn't already set the status
            if (currentStatus != newStatus && currentStatus != "CONNECTING") {
                currentStatus = newStatus
                activity?.runOnUiThread {
                    updateStatusUI(newStatus)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize SharedPreferences
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        // Initialize views
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        txtStatus = view.findViewById(R.id.txtStatus)
        val serversRecyclerView = view.findViewById<RecyclerView>(R.id.servers_recycler_view)
        serversRecyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize adapter
        serverListAdapter = ServerListAdapter(mutableListOf()) { server ->
            Log.d(TAG, "Server selected: ${server.hostName}")
            setFragmentResult("serverSelection", bundleOf("selectedHostname" to server.hostName))
            findNavController().navigateUp()
        }
        serversRecyclerView.adapter = serverListAdapter

        // Setup pull-to-refresh
        swipeRefreshLayout.setOnRefreshListener { 
            Log.d(TAG, "Pull to refresh triggered")
            loadServers() 
        }

        // Set initial status based on current state
        val isCurrentlyConnected = getBooleanPrefValue(OscPrefKey.ROOT_STATE, prefs)
        currentStatus = if (isCurrentlyConnected) "CONNECTED" else "DISCONNECTED"
        updateStatusUI(currentStatus)
        
        // Load servers
        loadServers()
    }

    private fun loadServers() {
        Log.d(TAG, "Loading servers...")
        swipeRefreshLayout.isRefreshing = true
        
        vpnRepository.fetchSstpServers { servers ->
            Log.d(TAG, "Received ${servers.size} servers")
            activity?.runOnUiThread {
                serverListAdapter.updateData(servers)
                swipeRefreshLayout.isRefreshing = false
                
                if (servers.isEmpty()) {
                    Log.w(TAG, "No servers loaded - check network or CSV parsing")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - registering receivers")
        
        // Register broadcast receiver for real-time VPN status updates
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            vpnStatusReceiver,
            IntentFilter(ACTION_VPN_STATUS_CHANGED)
        )
        
        // Register SharedPreferences listener as backup
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        
        // Refresh current status in case it changed while paused
        val isCurrentlyConnected = getBooleanPrefValue(OscPrefKey.ROOT_STATE, prefs)
        val status = if (isCurrentlyConnected) "CONNECTED" else "DISCONNECTED"
        if (currentStatus != "CONNECTING") {
            currentStatus = status
            updateStatusUI(status)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause - unregistering receivers")
        
        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(vpnStatusReceiver)
        
        // Unregister SharedPreferences listener
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    /**
     * Update the status TextView based on VPN connection state
     * 
     * States:
     * - CONNECTING: Yellow background, "Connecting..." text
     * - CONNECTED: Green background, "Secured" text  
     * - DISCONNECTED: Gray background, "Idle" text
     * - ERROR: Red text with error message
     */
    private fun updateStatusUI(status: String) {
        Log.d(TAG, "Updating status UI: $status")
        
        // Ensure we're on the UI thread
        if (!isAdded) return
        
        when {
            status == "CONNECTING" -> {
                txtStatus.text = "Connecting..."
                txtStatus.setTextColor(Color.parseColor("#FF8C00")) // Dark Orange for better visibility
                txtStatus.setBackgroundColor(Color.parseColor("#FFF3CD")) // Light yellow background
            }
            status == "CONNECTED" -> {
                txtStatus.text = "✓ Secured"
                txtStatus.setTextColor(Color.parseColor("#155724")) // Dark green
                txtStatus.setBackgroundColor(Color.parseColor("#D4EDDA")) // Light green background
            }
            status == "DISCONNECTED" -> {
                txtStatus.text = "Idle"
                txtStatus.setTextColor(Color.parseColor("#6C757D")) // Gray
                txtStatus.setBackgroundColor(Color.parseColor("#E9ECEF")) // Light gray background
            }
            status.startsWith("ERROR") -> {
                txtStatus.text = status
                txtStatus.setTextColor(Color.parseColor("#721C24")) // Dark red
                txtStatus.setBackgroundColor(Color.parseColor("#F8D7DA")) // Light red background
            }
            else -> {
                // Unknown state - show as-is with neutral styling
                txtStatus.text = status
                txtStatus.setTextColor(Color.GRAY)
                txtStatus.setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
        }
    }
}
