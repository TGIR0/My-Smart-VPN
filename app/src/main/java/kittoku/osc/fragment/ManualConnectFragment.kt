package kittoku.osc.fragment

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kittoku.osc.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.setStringPrefValue
import kittoku.osc.repository.ConnectionStateManager
import kittoku.osc.repository.HostnameHistoryManager
import kittoku.osc.service.ACTION_VPN_CONNECT
import kittoku.osc.service.ACTION_VPN_DISCONNECT
import kittoku.osc.service.SstpVpnService

/**
 * Fragment for manual VPN server connection
 * Features:
 * - Hostname autocomplete from history
 * - Smart Connect/Disconnect button based on connection state
 * - Global state synchronization with HomeFragment
 */
class ManualConnectFragment : Fragment(R.layout.fragment_manual_connect) {
    
    private lateinit var prefs: SharedPreferences
    private lateinit var etHostname: AutoCompleteTextView
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnConnect: MaterialButton
    
    private var historyAdapter: ArrayAdapter<String>? = null
    
    private val preparationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
            findNavController().navigateUp()
        }
    }
    
    // Listen for connection state changes
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateButtonState()
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        etHostname = view.findViewById(R.id.et_hostname)
        etUsername = view.findViewById(R.id.et_username)
        etPassword = view.findViewById(R.id.et_password)
        btnConnect = view.findViewById(R.id.btn_connect_manual)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel)
        
        // Setup hostname autocomplete
        setupHostnameAutocomplete()
        
        // Set initial button state
        updateButtonState()
        
        btnConnect.setOnClickListener {
            val currentState = ConnectionStateManager.getCurrentState()
            
            when (currentState) {
                ConnectionStateManager.ConnectionState.CONNECTED,
                ConnectionStateManager.ConnectionState.CONNECTING -> {
                    // Disconnect
                    disconnectVpn()
                }
                else -> {
                    // Connect
                    attemptConnection()
                }
            }
        }
        
        btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }
    
    private fun setupHostnameAutocomplete() {
        val history = HostnameHistoryManager.getHistory(requireContext())
        historyAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            history.toMutableList()
        )
        etHostname.setAdapter(historyAdapter)
    }
    
    private fun attemptConnection() {
        val hostname = etHostname.text?.toString()?.trim() ?: ""
        val username = etUsername.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        
        if (hostname.isEmpty()) {
            Toast.makeText(context, "Please enter a hostname", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (username.isEmpty()) {
            Toast.makeText(context, "Please enter a username", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save hostname to history
        HostnameHistoryManager.saveHostname(requireContext(), hostname)
        
        // Save to preferences
        setStringPrefValue(hostname, OscPrefKey.HOME_HOSTNAME, prefs)
        setStringPrefValue(username, OscPrefKey.HOME_USERNAME, prefs)
        setStringPrefValue(password, OscPrefKey.HOME_PASSWORD, prefs)
        
        // Update global state to "Connecting"
        ConnectionStateManager.setState(
            requireContext(),
            ConnectionStateManager.ConnectionState.CONNECTING,
            serverName = hostname,
            isManual = true
        )
        
        // Start connection
        VpnService.prepare(requireContext())?.also {
            preparationLauncher.launch(it)
        } ?: run {
            startVpnService()
            findNavController().navigateUp()
        }
    }
    
    private fun disconnectVpn() {
        ConnectionStateManager.setState(
            requireContext(),
            ConnectionStateManager.ConnectionState.DISCONNECTING
        )
        
        val intent = Intent(requireContext(), SstpVpnService::class.java)
            .setAction(ACTION_VPN_DISCONNECT)
        requireContext().startService(intent)
        
        Toast.makeText(context, "Disconnecting...", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateButtonState() {
        val currentState = ConnectionStateManager.getCurrentState()
        
        when (currentState) {
            ConnectionStateManager.ConnectionState.CONNECTED -> {
                btnConnect.text = "Disconnect"
                btnConnect.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark, null))
                btnConnect.isEnabled = true
            }
            ConnectionStateManager.ConnectionState.CONNECTING -> {
                btnConnect.text = "Connecting..."
                btnConnect.setBackgroundColor(resources.getColor(android.R.color.holo_orange_dark, null))
                btnConnect.isEnabled = false
            }
            ConnectionStateManager.ConnectionState.DISCONNECTING -> {
                btnConnect.text = "Disconnecting..."
                btnConnect.isEnabled = false
            }
            ConnectionStateManager.ConnectionState.DISCONNECTED -> {
                btnConnect.text = "Connect"
                btnConnect.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark, null))
                btnConnect.isEnabled = true
            }
        }
    }
    
    private fun startVpnService() {
        val intent = Intent(requireContext(), SstpVpnService::class.java)
            .setAction(ACTION_VPN_CONNECT)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Register for state updates
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            stateReceiver,
            IntentFilter(ConnectionStateManager.ACTION_STATE_CHANGED)
        )
        updateButtonState()
    }
    
    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stateReceiver)
    }
}
