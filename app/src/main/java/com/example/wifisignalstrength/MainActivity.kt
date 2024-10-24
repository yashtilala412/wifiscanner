// MainActivity.kt
package com.example.wifisignalstrength

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout


class MainActivity : AppCompatActivity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var wifiAdapter: WifiNetworkAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val wifiList = mutableListOf<ScanResult>()
    private var isReceiverRegistered = false

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            Log.d(TAG, "Scan results received. Success: $success")

            if (success) {
                scanSuccess()
            } else {
                Log.d(TAG, "Scan failed, attempting retry after delay")
                recyclerView.postDelayed({
                    startWifiScan()
                }, 1000)
            }
            swipeRefreshLayout.isRefreshing = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        setupRecyclerView()
        setupSwipeRefresh()
        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // Location permissions are required for WiFi scanning
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Add NEARBY_WIFI_DEVICES permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        // Add WiFi specific permissions
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE)

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $missingPermissions")
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            initializeWifiScanning()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasWifiPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return hasLocationPermission && hasWifiPermission
    }

    private fun initializeWifiScanning() {
        if (!wifiManager.isWifiEnabled) {
            Log.d(TAG, "WiFi is disabled")
            Toast.makeText(this, "Please enable WiFi in settings", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            return
        }

        registerWifiReceiver()
        startWifiScan()
    }

    private fun registerWifiReceiver() {
        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            }
            registerReceiver(wifiScanReceiver, intentFilter)
            isReceiverRegistered = true
            Log.d(TAG, "WiFi receiver registered")
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        wifiAdapter = WifiNetworkAdapter(wifiList)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wifiAdapter
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            if (hasRequiredPermissions()) {
                startWifiScan()
            } else {
                requestPermissions()
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun startWifiScan() {
        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Missing required permissions")
            Toast.makeText(this, "Location permission is required for WiFi scanning", Toast.LENGTH_LONG).show()
            requestPermissions()
            return
        }

        try {
            val success = wifiManager.startScan()
            Log.d(TAG, "StartScan called. Success: $success")

            if (!success) {
                Log.d(TAG, "Start scan failed, attempting to get cached results")
                scanFailure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting WiFi scan", e)
            Toast.makeText(this, "Error starting WiFi scan: ${e.message}", Toast.LENGTH_LONG).show()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun scanSuccess() {
        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Cannot access scan results - missing permissions")
            Toast.makeText(this, "Location permission required to show WiFi networks", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val results = wifiManager.scanResults
            Log.d(TAG, "Scan successful. Found ${results.size} networks")
            results.forEach { result ->
                Log.d(TAG, "Network found: ${result.SSID}, Signal: ${result.level} dBm")
            }

            wifiList.clear()
            wifiList.addAll(results)
            wifiAdapter.notifyDataSetChanged()

            if (wifiList.isEmpty()) {
                Log.d(TAG, "No networks found in scan results")
                Toast.makeText(this, "No WiFi networks found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while accessing scan results", e)
            Toast.makeText(this, "Permission required to access WiFi scan results", Toast.LENGTH_LONG).show()
            requestPermissions()
        }
    }

    private fun scanFailure() {
        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Cannot access cached results - missing permissions")
            Toast.makeText(this, "Location permission required to show WiFi networks", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val results = wifiManager.scanResults
            Log.d(TAG, "Using cached results. Found ${results.size} networks")

            wifiList.clear()
            wifiList.addAll(results)
            wifiAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Showing cached results.", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while accessing cached results", e)
            Toast.makeText(this, "Permission required to access WiFi scan results", Toast.LENGTH_LONG).show()
            requestPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "All permissions granted")
                initializeWifiScanning()
            } else {
                Log.d(TAG, "Some permissions were denied")
                Toast.makeText(
                    this,
                    "Location permission is required for WiFi scanning",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(wifiScanReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "WiFi receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
    }

    companion object {
        private const val TAG = "WiFiScanner"
        private const val PERMISSION_REQUEST_CODE = 100
    }
}