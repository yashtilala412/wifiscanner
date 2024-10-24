package com.example.wifisignalstrength

import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.recyclerview.widget.RecyclerView

class WifiNetworkAdapter(private val wifiList: List<ScanResult>) :
    RecyclerView.Adapter<WifiNetworkAdapter.WifiViewHolder>() {

    class WifiViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val ssidText: android.widget.TextView = view.findViewById(R.id.ssidText)
        val strengthText: android.widget.TextView = view.findViewById(R.id.strengthText)
        val signalIcon: android.widget.ImageView = view.findViewById(R.id.signalIcon)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): WifiViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.wifi_network_item, parent, false)
        return WifiViewHolder(view)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        val network = wifiList[position]

        // Get SSID (network name)
        val ssid = if (network.SSID.isEmpty()) "<Hidden Network>" else network.SSID
        holder.ssidText.text = ssid

        // Calculate signal strength
        val signalLevel = WifiManager.calculateSignalLevel(network.level, 5)
        val signalStrength = when (signalLevel) {
            0 -> "Very Poor"
            1 -> "Poor"
            2 -> "Fair"
            3 -> "Good"
            4 -> "Excellent"
            else -> "Unknown"
        }

        holder.strengthText.text = "Signal: $signalStrength (${network.level} dBm)"

        // Set signal icon
        val iconResource = when (signalLevel) {
            0 -> R.drawable.ic_signal_wifi_0
            1 -> R.drawable.ic_signal_wifi_1
            2 -> R.drawable.ic_signal_wifi_2
            3 -> R.drawable.ic_signal_wifi_3
            4 -> R.drawable.ic_signal_wifi_4
            else -> R.drawable.ic_signal_wifi_off
        }
        holder.signalIcon.setImageResource(iconResource)
    }

    override fun getItemCount() = wifiList.size
}