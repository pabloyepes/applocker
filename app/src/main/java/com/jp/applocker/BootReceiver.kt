package com.jp.applocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Store.enabled(c)) c.startForegroundService(Intent(c, MonitorService::class.java))
    }
}
