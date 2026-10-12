package com.robot.solver

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class SolverService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var box: LinearLayout
    private lateinit var robot: RobotView
    private lateinit var musicBtn: TextView
    private lateinit var saverBtn: TextView
    private lateinit var lightBtn: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var toggle: PowerView
    private lateinit var tParams: WindowManager.LayoutParams
    private var panel: LinearLayout? = null
    private var refresh: Runnable? = null
    private var d = 1f
    private var isOn = true

    private val watchdog = object : Runnable {
        override fun run() {
            runCatching {
                if (::box.isInitialized && !box.isAttachedToWindow) wm.addView(box, params)
                if (::toggle.isInitialized && !toggle.isAttachedToWindow) wm.addView(toggle, tParams)
            }
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        d = resources.displayMetrics.density
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        closePanel()
        if (::box.isInitialized) runCatching { wm.removeView(box) }
        if (::toggle.isInitialized) runCatching { wm.removeView(toggle) }
        isOn = true
        showOverlay()
        showToggle()
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, 10_000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        closePanel()
        if (::box.isInitialized) runCatching { wm.removeView(box) }
        if (::toggle.isInitialized) runCatching { wm.removeView(toggle) }
        super.onDestroy()
    }

    // ---------- UI helpers ----------

    private fun pill(label: String, color: String, size: Float = 13f, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((18 * d).toInt(), (9 * d).toInt(), (18 * d).toInt(), (9 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 22 * d
                setColor(Color.parseColor(color))
            }
            setOnClickListener { onClick() }
        }

    private fun chip(label: String, color: String, onClick: () -> Unit) =
        pill(label, color, 13f, onClick).apply { visibility = View.GONE }

    private fun label(t: String, size: Float = 13f, color: String = "#F3F4F6", bold: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun menu(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        musicBtn.visibility = v
        saverBtn.visibility = v
        lightBtn.visibility = v
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    // ---------- Panel ----------

    private fun closePanel() {
        refresh?.let { handler.removeCallbacks(it) }
        refresh = null
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    /** Jalankan [render] sekarang lalu ulangi tiap detik selama panel terbuka. */
    private fun autoRefresh(render: () -> Unit) {
        val r = object : Runnable {
            override fun run() {
                render()
                handler.postDelayed(this, 1000)
            }
        }
        refresh = r
        r.run()
    }

    private fun openPanel(title: String, accent: String, build: (LinearLayout) -> Unit) {
        closePanel()
        val dm = resources.displayMetrics
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 20 * d
                setColor(Color.parseColor("#F21F2937"))
                setStroke((2 * d).toInt(), Color.parseColor(accent))
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            label(title, 15f, accent, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        header.addView(label("✕", 18f, "#9CA3AF", true).apply {
            setPadding((12 * d).toInt(), 0, 0, 0)
            setOnClickListener { closePanel() }
        })
        p.addView(header)
        build(p)

        val width = minOf((300 * d).toInt(), dm.widthPixels - (32 * d).toInt())
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = params.y.coerceIn(0, maxOf(0, dm.heightPixels - (280 * d).toInt()))
        }
        wm.addView(p, lp)
        panel = p
    }

    private fun space(p: LinearLayout, dp: Int) =
        p.addView(View(this), LinearLayout.LayoutParams(1, (dp * d).toInt()))

    // ---------- Fitur 1: Spotify ----------

    private fun musicPanel() = openPanel("Spotify", "#1DB954") { p ->
        space(p, 10)
        val title = label("Tidak ada lagu", 15f, "#F3F4F6", true)
        val artist = label("", 12f, "#9CA3AF")
        val hint = label("", 11f, "#FBBF24").apply { maxLines = 3; ellipsize = null }
        p.addView(title)
        p.addView(artist)
        space(p, 12)

        lateinit var play: TextView
        fun later() = handler.postDelayed({ refresh?.run() }, 350)
        val prev = pill("⏮", "#374151", 20f) { Spotify.previous(this); later() }
        play = pill("▶", "#1DB954", 22f) { Spotify.playPause(this); later() }
        val next = pill("⏭", "#374151", 20f) { Spotify.next(this); later() }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (b in listOf(prev, play, next)) {
            row.addView(b, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = (8 * d).toInt(); marginEnd = (8 * d).toInt()
            })
        }
        p.addView(row)
        p.addView(hint)

        autoRefresh {
            val s = Spotify.session(this)
            title.text = Spotify.title(s) ?: if (s == null) "Spotify belum diputar" else "—"
            artist.text = Spotify.artist(s) ?: ""
            play.text = if (Spotify.isPlaying(s)) "⏸" else "▶"
            hint.text = if (!Spotify.hasAccess(this)) {
                "Judul lagu belum bisa tampil. Aktifkan akses notifikasi di app Brave."
            } else ""
            hint.visibility = if (hint.text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    // ---------- Fitur 2: Penghemat baterai ----------

    private fun saverPanel() = openPanel("Penghemat Baterai", "#7C3AED") { p ->
        space(p, 10)
        val level = label("", 13f, "#9CA3AF")
        val state = label("", 22f, "#F3F4F6", true)
        p.addView(level)
        p.addView(state)
        space(p, 12)

        lateinit var action: TextView
        action = pill("", "#7C3AED", 14f) {
            val want = !Saver.isOn(this)
            if (!Saver.set(this, want)) {
                toast("Izin belum diberikan lewat adb (lihat petunjuk di app). Membuka pengaturan sistem...")
                Saver.openSettings(this)
                closePanel()
            } else {
                handler.postDelayed({ refresh?.run() }, 500)
            }
        }
        p.addView(action, LinearLayout.LayoutParams(-1, -2))

        autoRefresh {
            val on = Saver.isOn(this)
            level.text = "Baterai ${Saver.percent(this)}%"
            state.text = if (on) "AKTIF" else "MATI"
            state.setTextColor(Color.parseColor(if (on) "#22C55E" else "#9CA3AF"))
            action.text = if (on) "MATIKAN" else "AKTIFKAN"
        }
    }

    // ---------- Fitur 3: Cahaya ----------

    private fun lightPanel() = openPanel("Kecerahan", "#2563EB") { p ->
        space(p, 10)
        if (!Light.canWrite(this)) {
            p.addView(label("Butuh izin ubah pengaturan sistem.", 13f, "#F3F4F6").apply {
                maxLines = 3; ellipsize = null
            })
            space(p, 12)
            p.addView(pill("BERI IZIN", "#2563EB", 14f) {
                Light.openPermission(this)
                closePanel()
            }, LinearLayout.LayoutParams(-1, -2))
            return@openPanel
        }

        val pct = label("", 22f, "#F3F4F6", true)
        p.addView(pct)
        space(p, 8)

        val bar = SeekBar(this).apply { max = 254 }
        fun show(v: Int) { pct.text = "${(v * 100 / 255)}%" }
        val cur = Light.get(this).coerceIn(1, 255)
        bar.progress = cur - 1
        show(cur)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                if (!fromUser) return
                Light.set(this@SolverService, v + 1)
                show(v + 1)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        p.addView(bar, LinearLayout.LayoutParams(-1, -2))
        space(p, 10)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun preset(name: String, v: Int) = pill(name, "#374151", 12f) {
            Light.set(this, v); bar.progress = v - 1; show(v)
        }
        for ((name, v) in listOf("Redup" to 25, "Sedang" to 128, "Terang" to 255)) {
            row.addView(preset(name, v), LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = (3 * d).toInt(); marginEnd = (3 * d).toInt()
            })
        }
        p.addView(row)
        space(p, 8)
        p.addView(pill("AUTO", "#2563EB", 12f) {
            Light.auto(this)
            toast("Kecerahan otomatis aktif")
        }, LinearLayout.LayoutParams(-1, -2))
    }

    // ---------- Overlay utama ----------

    private fun showOverlay() {
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        robot = RobotView(this)
        musicBtn = chip("MUSIK", "#1DB954") { menu(false); musicPanel() }
        saverBtn = chip("HEMAT", "#7C3AED") { menu(false); saverPanel() }
        lightBtn = chip("CAHAYA", "#2563EB") { menu(false); lightPanel() }
        box.addView(robot, LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt()))
        for (b in listOf(musicBtn, saverBtn, lightBtn)) {
            box.addView(b, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (6 * d).toInt() })
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * d).toInt()
            y = (240 * d).toInt()
        }

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        robot.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = params.x; oy = params.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt()
                    val dy = (e.rawY - sy).toInt()
                    if (Math.abs(dx) > 10 * d || Math.abs(dy) > 10 * d) moved = true
                    if (moved) {
                        params.x = ox + dx
                        params.y = oy + dy
                        wm.updateViewLayout(box, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    closePanel()
                    menu(musicBtn.visibility != View.VISIBLE)
                }
            }
            true
        }
        wm.addView(box, params)
    }

    private fun showToggle() {
        toggle = PowerView(this).apply { alpha = 0.75f }
        tParams = WindowManager.LayoutParams(
            (36 * d).toInt(), (36 * d).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (120 * d).toInt()
        }

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        toggle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = tParams.x; oy = tParams.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt()
                    val dy = (e.rawY - sy).toInt()
                    if (Math.abs(dx) > 10 * d || Math.abs(dy) > 10 * d) moved = true
                    if (moved) {
                        tParams.x = ox + dx
                        tParams.y = oy + dy
                        wm.updateViewLayout(toggle, tParams)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) setPower(!isOn)
            }
            true
        }
        wm.addView(toggle, tParams)
    }

    private fun setPower(on: Boolean) {
        isOn = on
        if (!on) {
            menu(false)
            closePanel()
        }
        box.visibility = if (on) View.VISIBLE else View.GONE
        toggle.on = on
        toggle.alpha = if (on) 0.75f else 0.45f
    }
}
