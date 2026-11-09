package com.example.juicecheck

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JuiceCheck : détecte si le port USB passe en mode "données"
 * pendant que le téléphone est en charge.
 * Aucune permission, aucune dépendance, pas de service en arrière-plan.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var log: TextView

    private var plugged = 0
    private var usbConnected = false
    private var usbConfigured = false
    private var dataFunctions: List<String> = emptyList()
    private var lastVerdict = ""

    private val dataKeys = listOf("mtp", "ptp", "adb", "rndis", "midi", "accessory", "audio_source")

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED ->
                    plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

                "android.hardware.usb.action.USB_STATE" -> {
                    val e = i.extras
                    usbConnected = e?.getBoolean("connected", false) ?: false
                    usbConfigured = e?.getBoolean("configured", false) ?: false
                    dataFunctions = dataKeys.filter { e?.getBoolean(it, false) == true }
                }
            }
            evaluate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(40, 120, 40, 120)
        }
        log = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(ScrollView(this@MainActivity).apply { addView(log) })
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction("android.hardware.usb.action.USB_STATE")
        }
        // Les deux broadcasts sont "sticky" : on récupère l'état actuel immédiatement
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        evaluate()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun evaluate() {
        val adbOn = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        val viaUsb = plugged == BatteryManager.BATTERY_PLUGGED_USB

        val (color, text) = when {
            plugged == 0 ->
                Color.DKGRAY to "Non branché"

            viaUsb && dataFunctions.isNotEmpty() ->
                Color.rgb(200, 30, 30) to
                    "⚠ DANGER\nMode données actif : ${dataFunctions.joinToString()}\nDébranchez !"

            viaUsb && usbConnected && usbConfigured ->
                Color.rgb(230, 130, 0) to
                    "⚠ SUSPECT\nUn hôte USB a établi une connexion de données"

            adbOn && viaUsb ->
                Color.rgb(230, 130, 0) to
                    "⚠ Le débogage USB est activé.\nDésactivez-le dans les options développeur"

            else ->
                Color.rgb(30, 140, 60) to
                    "✔ Charge seule\nAucune connexion de données détectée"
        }

        status.setBackgroundColor(color)
        status.text = text

        if (text != lastVerdict) {
            lastVerdict = text
            val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            log.text = "$t  ${text.replace("\n", " | ")}\n" + log.text
        }
    }
}
