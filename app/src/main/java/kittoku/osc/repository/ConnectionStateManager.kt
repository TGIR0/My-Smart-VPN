package kittoku.osc.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Singleton manager for global VPN connection state
 * Observable by all fragments for UI synchronization
 */
object ConnectionStateManager {
    private const val TAG = "ConnectionStateManager"
    
    const val ACTION_STATE_CHANGED = "kittoku.osc.CONNECTION_STATE_CHANGED"
    const val EXTRA_STATE = "state"
    const val EXTRA_SERVER_NAME = "server_name"
    
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    private val _state = MutableLiveData(ConnectionState.DISCONNECTED)
    val state: LiveData<ConnectionState> = _state
    
    private var _currentServerName: String? = null
    val currentServerName: String? get() = _currentServerName
    
    private var _isManualConnection: Boolean = false
    val isManualConnection: Boolean get() = _isManualConnection

    /**
     * Update connection state and broadcast to all listeners
     */
    fun setState(context: Context, newState: ConnectionState, serverName: String? = null, isManual: Boolean = false) {
        Log.d(TAG, "State changed: ${_state.value} -> $newState (server: $serverName, manual: $isManual)")
        
        _state.postValue(newState)
        _currentServerName = serverName ?: _currentServerName
        _isManualConnection = if (newState == ConnectionState.CONNECTING || newState == ConnectionState.CONNECTED) {
            isManual
        } else {
            false
        }
        
        // Broadcast for fragments that prefer BroadcastReceiver pattern
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, newState.name)
            putExtra(EXTRA_SERVER_NAME, _currentServerName)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    /**
     * Get current state synchronously
     */
    fun getCurrentState(): ConnectionState {
        return _state.value ?: ConnectionState.DISCONNECTED
    }

    /**
     * Reset to disconnected state
     */
    fun reset(context: Context) {
        setState(context, ConnectionState.DISCONNECTED, null, false)
    }
}
