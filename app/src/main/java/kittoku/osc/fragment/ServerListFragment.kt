package kittoku.osc.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import kittoku.osc.repository.VpnRepository

class ServerListFragment : Fragment() {
    private lateinit var repository: VpnRepository
    private lateinit var adapter: ServerListAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_server_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = VpnRepository()
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // تنظیم لیست
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewServers)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ServerListAdapter(emptyList()) { server ->
            // وقتی روی سرور کلیک شد:
            saveAndConnect(server)
        }
        recyclerView.adapter = adapter

        // تنظیم رفرش
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            loadServers()
        }

        // لود اولیه
        loadServers()
    }

    private fun loadServers() {
        swipeRefresh.isRefreshing = true
        repository.fetchSstpServers { servers ->
            activity?.runOnUiThread {
                swipeRefresh.isRefreshing = false
                if (servers.isNotEmpty()) {
                    adapter.updateList(servers)
                } else {
                    Toast.makeText(context, "No servers found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveAndConnect(server: kittoku.osc.repository.SstpServer) {
        // ذخیره اطلاعات سرور در تنظیمات اصلی برنامه
        // از کلیدهای استاندارد پروژه استفاده می‌کنیم
        setStringPrefValue(server.hostName, OscPrefKey.HOME_HOSTNAME, prefs)
        setStringPrefValue("vpn", OscPrefKey.HOME_USERNAME, prefs)
        setStringPrefValue("vpn", OscPrefKey.HOME_PASSWORD, prefs)
        setBooleanPrefValue(true, OscPrefKey.SSL_DO_VERIFY, prefs) // فعال سازی وریفای (یا فالس اگر ارور داد)
        setIntPrefValue(443, OscPrefKey.SSL_PORT, prefs)

        Toast.makeText(context, "Selected: ${server.hostName}", Toast.LENGTH_SHORT).show()

        // بازگشت به صفحه اصلی برای اتصال
        parentFragmentManager.popBackStack()
    }
}