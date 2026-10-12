package com.robot.solver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun note(t: String) = layout.addView(TextView(this).apply {
            text = t
            textSize = 13f
            setPadding(0, pad / 2, 0, pad / 4)
        })
        fun button(t: String, onClick: () -> Unit) = layout.addView(Button(this).apply {
            text = t
            setOnClickListener { onClick() }
        })

        note("1. Aktifkan tombol melayang")
        button("Aktifkan di Pengaturan Aksesibilitas") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        note("2. Spotify: biar judul lagu tampil & play/pause tepat ke Spotify")
        button("Izinkan akses notifikasi") {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }

        note("3. Kecerahan: izin ubah pengaturan sistem")
        button("Izinkan ubah pengaturan sistem") { Light.openPermission(this) }

        note(
            "4. Penghemat baterai (opsional, biar tombolnya langsung on/off). " +
                "Jalankan sekali dari PC:\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n" +
                "Tanpa ini, tombolnya membuka halaman Penghemat Baterai milik sistem."
        )

        note("Lainnya")
        button("Bebaskan app dari optimasi baterai") {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        button("Buka Info Aplikasi") {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
}
