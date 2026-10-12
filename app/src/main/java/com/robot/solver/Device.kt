package com.robot.solver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat

/** Kontrol Spotify. Pakai sesi Spotify langsung kalau akses notifikasi aktif, kalau tidak pakai media key. */
object Spotify {
    private const val PKG = "com.spotify.music"

    fun hasAccess(c: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun session(c: Context): MediaController? = runCatching {
        val msm = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        msm.getActiveSessions(ComponentName(c, MediaListener::class.java))
            .firstOrNull { it.packageName == PKG }
    }.getOrNull()

    fun isPlaying(s: MediaController?) = s?.playbackState?.state == PlaybackState.STATE_PLAYING
    fun title(s: MediaController?): String? = s?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    fun artist(s: MediaController?): String? = s?.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

    private fun key(c: Context, code: Int) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun playPause(c: Context) {
        val s = session(c)
        if (s == null) key(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        else if (isPlaying(s)) s.transportControls.pause() else s.transportControls.play()
    }

    fun next(c: Context) {
        val s = session(c)
        if (s == null) key(c, KeyEvent.KEYCODE_MEDIA_NEXT) else s.transportControls.skipToNext()
    }

    fun previous(c: Context) {
        val s = session(c)
        if (s == null) key(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS) else s.transportControls.skipToPrevious()
    }
}

/** Penghemat baterai. Toggle langsung butuh WRITE_SECURE_SETTINGS (via adb), kalau tidak, buka halaman pengaturannya. */
object Saver {
    fun isOn(c: Context) = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode

    fun percent(c: Context): Int =
        (c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun set(c: Context, on: Boolean): Boolean = runCatching {
        Settings.Global.putInt(c.contentResolver, "low_power", if (on) 1 else 0)
    }.getOrDefault(false)

    fun openSettings(c: Context) = runCatching {
        c.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Kecerahan layar (system brightness). Butuh izin "Ubah pengaturan sistem". */
object Light {
    fun canWrite(c: Context) = Settings.System.canWrite(c)

    fun get(c: Context): Int = runCatching {
        Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)

    fun set(c: Context, v: Int) = runCatching {
        Settings.System.putInt(
            c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(1, 255))
    }

    fun auto(c: Context) = runCatching {
        Settings.System.putInt(
            c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        )
    }

    fun openPermission(c: Context) = runCatching {
        c.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
