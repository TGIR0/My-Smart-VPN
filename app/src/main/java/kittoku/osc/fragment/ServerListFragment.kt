package kittoku.osc.fragment

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kittoku.osc.R
import kittoku.osc.adapter.ServerListAdapter
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.setBooleanPrefValue
import kittoku.osc.preference.accessor.setIntPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue
import kittoku.osc.repository.SstpServer
import kittoku.osc.repository.VpnRepository
import kittoku.osc.service.ACTION_VPN_CONNECT
import kittoku.osc.service.SstpVpnService

class ServerListFragment : Fragment(R.layout.fragment_server_list) {
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var recyclerViewServers: RecyclerView? = null

    private lateinit var repository: VpnRepository
    private lateinit var adapter: ServerListAdapter
    private lateinit var prefs: SharedPreferences

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = VpnRepository()
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        recyclerViewServers = view.findViewById(R.id.recyclerViewServers)

        recyclerViewServers?.layoutManager = LinearLayoutManager(requireContext())

        adapter = ServerListAdapter(emptyList()) { server ->
            connectToServer(server)
        }
        recyclerViewServers?.adapter = adapter

        swipeRefresh?.setOnRefreshListener { loadServers() }

        loadServers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up view references to avoid memory leaks
        swipeRefresh = null
        recyclerViewServers = null
    }

    private fun loadServers() {
        swipeRefresh?.isRefreshing = true
        repository.fetchSstpServers { servers ->
            activity?.runOnUiThread {
                swipeRefresh?.isRefreshing = false
                if (servers.isNotEmpty()) {
                    adapter.updateList(servers)
                } else {
                    Toast.makeText(context, "No servers found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connectToServer(server: SstpServer) {
        // 1. Save server settings to SharedPreferences
        setStringPrefValue(server.hostName, OscPrefKey.HOME_HOSTNAME, prefs)
        setStringPrefValue("vpn", OscPrefKey.HOME_USERNAME, prefs)
        setStringPrefValue("vpn", OscPrefKey.HOME_PASSWORD, prefs)
        setBooleanPrefValue(true, OscPrefKey.SSL_DO_VERIFY, prefs)
        setIntPrefValue(443, OscPrefKey.SSL_PORT, prefs)

        Toast.makeText(context, "Connecting to ${server.country}...", Toast.LENGTH_SHORT).show()

        // 2. Send connect command to the service
        val intent = Intent(requireContext(), SstpVpnService::class.java).apply {
            action = ACTION_VPN_CONNECT
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }
}
