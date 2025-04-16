package org.starx.spaceship.store

import android.content.Context
import android.content.SharedPreferences
import org.starx.spaceship.R
import androidx.core.content.edit

class Runtime(private val ctx: Context) {

    private val sp: SharedPreferences =
        ctx.getSharedPreferences(
            ctx.getString(R.string.preference_settings_key),
            Context.MODE_PRIVATE
        )

    var firstRun: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.settings_first_run), true)
        set(value) = sp.edit { putBoolean(ctx.getString(R.string.settings_first_run), value) }


    var resourceVersion: Int
        get() = sp.getInt(ctx.getString(R.string.settings_resource_version), 0)
        set(value) = sp.edit { putInt(ctx.getString(R.string.settings_resource_version), value) }
}