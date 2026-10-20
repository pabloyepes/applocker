package com.jp.applocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.util.Calendar

class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastQuery = 0L
    private val used = HashMap<String, Int>()
    private var curDay = Store.day()
    private var tick = 0

    private val loop = object : Runnable {
        override fun run() {
            try { step() } catch (e: Exception) { }
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("lock", "App Locker", NotificationManager.IMPORTANCE_LOW))
        val n = Notification_(this)
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        lastQuery = System.currentTimeMillis() - 600000
        h.post(loop)
    }

    @Suppress("FunctionName")
    private fun Notification_(c: android.content.Context) =
        android.app.Notification.Builder(c, "lock")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("App Locker is running")
            .build()

    override fun onStartCommand(i: Intent?, f: Int, s: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { h.removeCallbacks(loop); flush(); super.onDestroy() }

    private fun flush() { used.forEach { (k, v) -> Store.setUsed(this, k, v) } }

    private fun step() {
        val now = System.currentTimeMillis()
        val um = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = um.queryEvents(lastQuery, now)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) current = e.packageName
            else if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED && e.packageName == current) current = null
        }
        lastQuery = now - 2000

        val pkg = current ?: return
        if (pkg == packageName) return
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return
        val rule = Store.rules(this).firstOrNull { it.pkg == pkg } ?: return

        val d = Store.day()
        if (d != curDay) { flush(); used.clear(); curDay = d }
        val u = (used[pkg] ?: Store.usedSec(this, pkg)) + 1
        used[pkg] = u
        if (++tick % 15 == 0) flush()

        if (Store.grantUntil(this, pkg) > now) return
        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val inWin = rule.from >= 0 && rule.to >= 0 && rule.from != rule.to &&
            (if (rule.from < rule.to) m >= rule.from && m < rule.to else m >= rule.from || m < rule.to)
        val over = rule.limitMin > 0 && u >= rule.limitMin * 60
        if ((inWin || over) && !LockActivity.showing) {
            startActivity(
                Intent(this, LockActivity::class.java).putExtra("pkg", pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}
