package org.starx.spaceship.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import org.starx.spaceship.R
import org.starx.spaceship.model.Configuration
import org.starx.spaceship.store.Settings
import org.starx.spaceship.util.EditTextUtil
import org.starx.spaceship.util.JsonFactory

class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        const val TAG = "SettingsFragment"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        applyConstrains()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.configuration_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_export -> {
                        handleExport()
                        true
                    }
                    R.id.action_import -> {
                        handleImport()
                        refresh()
                        true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private fun handleImport() {
        val ctx = requireContext()
        val clipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboardManager.hasPrimaryClip()) {
            Toast.makeText(ctx, "Copy configuration to clipboard first!", Toast.LENGTH_SHORT).show()
            return
        }

        val clip = clipboardManager.primaryClip!!.getItemAt(0).text.toString()
        try {
            val cfg = JsonFactory.processor.decodeFromString(Configuration.serializer(), clip)
            Settings(ctx).saveConfiguration(cfg)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "clip: $clip error: $e")
            Toast.makeText(ctx, "parse configuration failed: $e", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(ctx, "Configuration imported", Toast.LENGTH_SHORT).show()
    }

    private fun handleExport() {
        val ctx = requireContext()
        val clipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("config", Settings(ctx).toJson())
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(ctx, "Configuration copied", Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        val navController: NavController =
            requireActivity().findNavController(R.id.nav_host_fragment_activity_main)
        navController.run {
            popBackStack()
            navigate(R.id.navigation_dashboard)
        }
    }

    private fun applyConstrains() {
        val portOnly = setOf<EditTextPreference>(
            findPreference(getString(R.string.server_port_key))!!,
            findPreference(getString(R.string.inbound_socks_port_key))!!,
            findPreference(getString(R.string.inbound_http_port_key))!!,
        )
        portOnly.forEach {
            EditTextUtil(it).setNumberOnly(1, 65535)
        }
        EditTextUtil(findPreference(getString(R.string.server_mux_key))!!).setNumberOnly(0, 255)
        EditTextUtil(findPreference(getString(R.string.server_buffer_key))!!).setNumberOnly(
            1,
            65535
        ).setSuffix("KB")
        EditTextUtil(findPreference(getString(R.string.user_id_key))!!).setPasswordWithMask()
    }
}