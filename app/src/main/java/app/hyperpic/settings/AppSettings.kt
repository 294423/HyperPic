package app.hyperpic.settings

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var hideZeroDimensionMedia: Boolean
        get() = prefs.getBoolean("hide_zero_dimension_media", true)
        set(value) = prefs.edit().putBoolean("hide_zero_dimension_media", value).apply()
}
