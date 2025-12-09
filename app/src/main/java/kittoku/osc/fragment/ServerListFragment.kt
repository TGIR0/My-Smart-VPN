package kittoku.osc.fragment

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kittoku.osc.R
import kittoku.osc.adapter.ServerListAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kittoku.osc.repository.VpnRepository

class ServerListFragment : Fragment(R.layout.fragment_server_list) {
    private lateinit var serverListAdapter: ServerListAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var txtStatus: TextView
    private val vpnRepository = VpnRepository()

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("status")?.let { status ->
                updateStatusUI(status)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        txtStatus = view.findViewById(R.id.txtStatus)
        val serversRecyclerView = view.findViewById<RecyclerView>(R.id.servers_recycler_view)
        serversRecyclerView.layoutManager = LinearLayoutManager(context)

        serverListAdapter = ServerListAdapter(mutableListOf()) { server ->
            setFragmentResult("serverSelection", bundleOf("selectedHostname" to server.hostName))
            findNavController().navigateUp()
        }
        serversRecyclerView.adapter = serverListAdapter

        swipeRefreshLayout.setOnRefreshListener { loadServers() }

        loadServers()
    }

    private fun loadServers() {
        swipeRefreshLayout.isRefreshing = true
        vpnRepository.fetchSstpServers { servers ->
            activity?.runOnUiThread {
                serverListAdapter.updateData(servers)
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }


    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            vpnStatusReceiver,
            IntentFilter("kittoku.osc.action.VPN_STATUS_CHANGED")
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(vpnStatusReceiver)
    }

    private fun updateStatusUI(status: String) {
        when (status) {
            "CONNECTING" -> {
                txtStatus.text = "Connecting..."
                txtStatus.setTextColor(Color.YELLOW)
            }
            "CONNECTED" -> {
                txtStatus.text = "Secured"
                txtStatus.setTextColor(Color.GREEN)
            }
            "DISCONNECTED" -> {
                txtStatus.text = "Idle"
                txtStatus.setTextColor(Color.GRAY)
            }
            else -> {
                txtStatus.text = status // Show error or other states
                txtStatus.setTextColor(Color.GRAY)
            }
        }
    }
}
