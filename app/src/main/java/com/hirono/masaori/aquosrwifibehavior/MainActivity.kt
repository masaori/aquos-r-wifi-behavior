package com.hirono.masaori.aquosrwifibehavior

import android.Manifest
import android.os.Bundle

import kotlinx.android.synthetic.main.activity_main.*
import android.content.IntentFilter
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.net.*
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import permissions.dispatcher.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList

@RuntimePermissions
class MainActivity : AppCompatActivity() {
    var _logText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BroadcastReceiverを登録
        val connectivityActionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                checkCurrentConnectionInfoWithPermissionCheck()
            }
        }
        registerReceiver(connectivityActionReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        setContentView(R.layout.activity_main)
        logText.text = ""

        buttonSwitchWifi.text = "Wifiを切り替える"
        buttonSwitchWifi.setOnClickListener listener@ { view ->
            Snackbar.make(view, "Start trying to connect with ConnectionManager", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()

            log("Start switching to SSID ${editTextSSID.text}")
            clearLog()

            if (editTextSSID.text.isEmpty()) {
                log("SSIDを入力してください")
                return@listener
            }
            if (editTextHost.text.isEmpty()) {
                log("URLを入力してください")
                return@listener
            }

            checkCurrentConnectionInfoWithPermissionCheck()
            switchWifi {
                log("Wifi connected")

                val urlText = editTextHost.text.toString()
                log("Start http request to $urlText")

                val url = URL(urlText)
                val urlConnection = url.openConnection() as HttpURLConnection
                val responseStringBuilder = StringBuilder()

                try {
                    urlConnection.requestMethod = "GET"
                    urlConnection.connect()

                    val br = BufferedReader(InputStreamReader(urlConnection.inputStream))
                    for (line in br.readLines()) {
                        line.let { responseStringBuilder.append(line) }
                    }
                    br.close()
                } catch(e: Exception) {
                    log("Fail:")
                    log("$e")
                } finally {
                    urlConnection.disconnect()
                }

                log("Finished http request")
                log("Response:")
                log(responseStringBuilder.toString())
            }
        }

        buttonCopy.setOnClickListener { view ->
            editTextSSID.setText(textViewCurrentSSID.text)
        }
    }

    override fun onResume() {
        super.onResume()
        checkCurrentConnectionInfoWithPermissionCheck()
    }

    fun switchWifi(onConnect: () -> Unit) {
        val ssidToConnect = editTextSSID.text.toString()
        val wifiManager = getWifiManager()
        val targetConf = findWifiConfigurationWithSSID(ssidToConnect)
        if (targetConf == null) {
            log("$ssidToConnect はまだ接続されたことがありません")
            return
        }

        wifiManager.disconnect()

        if (wifiManager.enableNetwork(targetConf!!.networkId, true)) {
            wifiManager.reconnect()
        } else {
            log("$ssidToConnect を有効にできませんでした")
            return
        }

        val cm = getConnectivityManager()
        cm.requestNetwork(
                NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val succeeded = cm.bindProcessToNetwork(network)
                        log("onAvailable succeeded: $succeeded $network")
                        onConnect()
                    }

                    override fun onLost(network: Network) {
                        log("onLost")
                    }
                }
        )
    }


    fun log(text: CharSequence) {
        runOnUiThread {
            _logText = _logText + text + "\n"
            logText.text = _logText
        }
    }

    fun clearLog() {
        runOnUiThread {
            _logText = ""
            logText.text = _logText
        }
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun checkCurrentConnectionInfo() {
        val wifiManager = getWifiManager()
        val connectionInfo = wifiManager.connectionInfo ?: return
        val state = WifiInfo.getDetailedStateOf(connectionInfo.supplicantState)
        val ssid = when(state) {
            null -> "?"
            NetworkInfo.DetailedState.CONNECTED, NetworkInfo.DetailedState.OBTAINING_IPADDR -> connectionInfo.ssid
            NetworkInfo.DetailedState.DISCONNECTED -> ""
            else -> "?"
        }
        log("WIFI: $ssid $state")
        textViewCurrentSSID.text = if (connectionInfo.hiddenSSID) "<hidden>" else ssid
    }

    private fun getConnectivityManager(): ConnectivityManager {
        return applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun getWifiManager(): WifiManager {
        return applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun findWifiConfigurationWithSSID(ssid: String): WifiConfiguration? {
        for (each in getConfiguredNetworks()) {
            if (IsSSIDEquals(each, ssid))
                return each
        }
        return null
    }

    internal fun IsSSIDEquals(conf: WifiConfiguration, ssid: String): Boolean {
        var ssid = ssid
        if (!ssid.startsWith("\""))
            ssid = '"' + ssid + '"'
        return conf.SSID == ssid
    }

    private fun getConfiguredNetworks(): List<WifiConfiguration> {
        val configuredNetworks = getWifiManager().configuredNetworks
        return configuredNetworks ?: ArrayList()
    }
}
