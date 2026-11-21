
package com.webcodelab.fastproject

import android.app.*
import android.os.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.Manifest

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("wcl_timer", MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var hours: EditText
    private lateinit var minutes: EditText
    private lateinit var seconds: EditText

    private val blue = Color.rgb(35, 115, 255)
    private val dark = Color.rgb(15, 23, 42)

    private val ticker = object : Runnable {
        override fun run() {
            if (!prefs.getBoolean("running", false)) return

            val end = prefs.getLong("end", 0L)
            val remaining = ((end - System.currentTimeMillis()) / 1000)
                .coerceAtLeast(0)

            showTime(remaining)

            if (remaining > 0) {
                handler.postDelayed(this, 250)
            } else {
                statusText.text = "COMPLETING..."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        requestNotificationPermission()
        createUI()
        refreshTimer()
    }

    private fun createUI() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 30, 24, 30)
            setBackgroundColor(dark)
        }

        fun text(
            value: String,
            size: Float,
            color: Int
        ): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
        }

        val logo = text("WCL", 17f, Color.CYAN)
        val heading = text("COUNTDOWN", 29f, Color.WHITE)

        statusText = text("READY TO START", 12f, Color.LTGRAY)

        timerText = text("00:00:00", 43f, Color.WHITE).apply {
            setPadding(0, 35, 0, 35)
        }

        val inputTitle = text(
            "SET YOUR TIME",
            13f,
            Color.LTGRAY
        )

        hours = makeInput("Hours")
        minutes = makeInput("Minutes")
        seconds = makeInput("Seconds")

        val inputs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf(hours, minutes, seconds).forEach {
            inputs.addView(it, LinearLayout.LayoutParams(0, 65, 1f).apply {
                setMargins(4, 10, 4, 10)
            })
        }

        val start = makeButton("START COUNTDOWN", blue)
        val pause = makeButton("PAUSE", Color.DKGRAY)
        val resume = makeButton("RESUME", Color.DKGRAY)
        val reset = makeButton("RESET", Color.DKGRAY)

        start.setOnClickListener { startTimer() }
        pause.setOnClickListener { pauseTimer() }
        resume.setOnClickListener { resumeTimer() }
        reset.setOnClickListener { resetTimer() }

        root.addView(logo)
        root.addView(heading)
        root.addView(statusText)
        root.addView(timerText)
        root.addView(inputTitle)
        root.addView(inputs)

        root.addView(start, fullWidth())
        root.addView(pause, fullWidth())
        root.addView(resume, fullWidth())
        root.addView(reset, fullWidth())

        setContentView(root)
    }

    private fun fullWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            54
        ).apply {
            setMargins(0, 7, 0, 7)
        }
    }

    private fun makeInput(label: String): EditText {
        return EditText(this).apply {
            hint = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
            inputType = 2
            imeOptions = EditorInfo.IME_ACTION_DONE
            setBackgroundColor(Color.rgb(35, 48, 70))
        }
    }

    private fun makeButton(label: String, color: Int): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color)
            )
        }
    }

    private fun readInput(field: EditText): Long {
        return field.text.toString().toLongOrNull() ?: 0L
    }

    private fun startTimer() {

        val h = readInput(hours)
        val m = readInput(minutes)
        val s = readInput(seconds)

        if (h > 8760 || m > 59 || s > 59) {
            toast("Invalid time entered")
            return
        }

        val total = h * 3600 + m * 60 + s

        if (total <= 0 || total > 31536000) {
            toast("Please enter a valid countdown")
            return
        }

        prefs.edit()
            .putLong("remaining", total)
            .putLong("end", System.currentTimeMillis() + total * 1000)
            .putBoolean("running", true)
            .apply()

        scheduleAlarm()
        refreshTimer()

        toast("Countdown started")
    }

    private fun pauseTimer() {

        if (!prefs.getBoolean("running", false)) return

        val remaining = (
            (prefs.getLong("end", 0L) - System.currentTimeMillis()) / 1000
        ).coerceAtLeast(0)

        cancelAlarm()

        prefs.edit()
            .putLong("remaining", remaining)
            .putBoolean("running", false)
            .apply()

        refreshTimer()
        statusText.text = "PAUSED"
    }

    private fun resumeTimer() {

        val remaining = prefs.getLong("remaining", 0L)

        if (remaining <= 0 ||
            prefs.getBoolean("running", false)
        ) return

        prefs.edit()
            .putLong("end", System.currentTimeMillis() + remaining * 1000)
            .putBoolean("running", true)
            .apply()

        scheduleAlarm()
        refreshTimer()

        toast("Countdown resumed")
    }

    private fun resetTimer() {

        cancelAlarm()

        prefs.edit()
            .clear()
            .apply()

        handler.removeCallbacks(ticker)

        showTime(0)
        statusText.text = "READY TO START"

        toast("Timer reset")
    }

    private fun scheduleAlarm() {

        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, TimerReceiver::class.java)

        val pending = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val end = prefs.getLong("end", 0L)

        try {
            if (Build.VERSION.SDK_INT >= 31 &&
                !alarm.canScheduleExactAlarms()
            ) {
                alarm.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    end,
                    pending
                )
            } else {
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    end,
                    pending
                )
            }
        } catch (e: SecurityException) {
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                end,
                pending
            )
        }
    }

    private fun cancelAlarm() {

        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager

        val pending = PendingIntent.getBroadcast(
            this,
            1001,
            Intent(this, TimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        alarm.cancel(pending)
        handler.removeCallbacks(ticker)
    }

    private fun refreshTimer() {

        if (prefs.getBoolean("running", false)) {

            val end = prefs.getLong("end", 0L)
            val remaining = ((end - System.currentTimeMillis()) / 1000)
                .coerceAtLeast(0)

            if (remaining == 0L) {
                showTime(0)
                statusText.text = "COMPLETING..."
                return
            }

            statusText.text = "COUNTDOWN RUNNING"
            handler.removeCallbacks(ticker)
            handler.post(ticker)

        } else {

            val remaining = prefs.getLong("remaining", 0L)

            showTime(remaining)
            statusText.text = if (remaining > 0) {
                "PAUSED"
            } else {
                "READY TO START"
            }
        }
    }

    private fun showTime(total: Long) {

        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60

        timerText.text = String.format(
            "%02d:%02d:%02d",
            h, m, s
        )
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                "wcl_countdown",
                "WCL Countdown Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

            channel.description = "Countdown completion alerts"

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}

class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {

        val prefs = context.getSharedPreferences(
            "wcl_timer",
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean("running", false)) return

        val end = prefs.getLong("end", 0L)

        if (end == 0L || System.currentTimeMillis() < end) return

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit()
                .putBoolean("running", false)
                .putLong("remaining", 0L)
                .remove("end")
                .apply()
            return
        }

        val openApp = PendingIntent.getActivity(
            context,
            1002,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, "wcl_countdown")
        } else {
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WCL Countdown Completed!")
            .setContentText("Your countdown has finished.")
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(1001, notification)

        prefs.edit()
            .putBoolean("running", false)
            .putLong("remaining", 0L)
            .remove("end")
            .apply()
    }
}
