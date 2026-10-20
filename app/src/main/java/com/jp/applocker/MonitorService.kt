package com.jp.applocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        lastQuery = System.currentTimeMillis() - 600000
        h.post(loop)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Store.rules(this).size
        return Notification.Builder(this, "lock")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("App Locker is active")
            .setContentText("Watching $n ${if (n == 1) "app" else "apps"}")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

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
        val inWin = rule.windowActive()
        val over = rule.hasLimit && u >= rule.limitMin * 60
        if ((inWin || over) && !LockActivity.showing) {
            val reason = if (inWin) "Blocked until ${fmtTime(rule.to)}" else "Daily limit reached · ${fmtDuration(rule.limitMin)}"
            startActivity(
                Intent(this, LockActivity::class.java).putExtra("pkg", pkg).putExtra("reason", reason)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}
