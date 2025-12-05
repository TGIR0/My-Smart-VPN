package kittoku.osc.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kittoku.osc.R
import kittoku.osc.repository.SstpServer
import java.util.Locale

class ServerListAdapter(
    private var serverList: List<SstpServer>,
    private val onItemClick: (SstpServer) -> Unit
) : RecyclerView.Adapter<ServerListAdapter.ServerViewHolder>() {

    fun updateList(newList: List<SstpServer>) {
        serverList = newList
        notifyDataSetChanged()
    }

    class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtFlag: TextView = itemView.findViewById(R.id.txtFlag)
        val txtHost: TextView = itemView.findViewById(R.id.txtHost)
        val txtSpeed: TextView = itemView.findViewById(R.id.txtSpeed)
        val txtSessions: TextView = itemView.findViewById(R.id.txtSessions)
        val btnConnect: View = itemView.findViewById(R.id.btnConnect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server_card, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = serverList[position]

        // تبدیل کد کشور به پرچم (بدون ارور)
        holder.txtFlag.text = getFlagEmoji(server.country)

        holder.txtHost.text = "${server.country}\n${server.hostName}"

        // نمایش سرعت به مگابیت
        val speedMbps = server.speed / 1000000
        holder.txtSpeed.text = "$speedMbps Mbps"

        holder.txtSessions.text = "${server.sessions} Sessions"

        holder.itemView.setOnClickListener { onItemClick(server) }
        holder.btnConnect.setOnClickListener { onItemClick(server) }
    }

    override fun getItemCount() = serverList.size

    // تابع اصلاح شده و امن برای پرچم
    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "🌐"
        try {
            val upperCode = countryCode.uppercase(Locale.ROOT)
            val firstLetter = Character.codePointAt(upperCode, 0) - 0x41 + 0x1F1E6
            val secondLetter = Character.codePointAt(upperCode, 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
        } catch (e: Exception) {
            return "🌐"
        }
    }
}