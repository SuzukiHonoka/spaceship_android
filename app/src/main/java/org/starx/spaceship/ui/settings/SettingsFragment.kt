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

        val clipItem = clipboardManager.primaryClip?.getItemAt(0)
        if (clipItem?.text == null) {
            Toast.makeText(ctx, "Clipboard contains no text!", Toast.LENGTH_SHORT).show()
            return
        }

        val clip = clipItem.text.toString().trim()
        if (clip.isBlank()) {
            Toast.makeText(ctx, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cfg = JsonFactory.processor.decodeFromString(Configuration.serializer(), clip)
            
            // Validate the configuration before saving
            if (cfg.serverAddress.isBlank() || cfg.uuid.isBlank()) {
                Toast.makeText(ctx, "Invalid configuration: missing required fields", Toast.LENGTH_SHORT).show()
                return
            }
            
            Settings(ctx).saveConfiguration(cfg)
            Toast.makeText(ctx, "Configuration imported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Import failed - clip: $clip, error: $e")
            Toast.makeText(ctx, "Failed to parse configuration: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleExport() {
        val ctx = requireContext()
        
        try {
            val settings = Settings(ctx)
            if (!settings.validate()) {
                Toast.makeText(ctx, "Cannot export: configuration is invalid", Toast.LENGTH_SHORT).show()
                return
            }
            
            val configJson = settings.toJson()
            val clipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("config", configJson)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(ctx, "Configuration copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: $e")
            Toast.makeText(ctx, "Failed to export configuration: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        val portPreferences = listOf(
            getString(R.string.server_port_key),
            getString(R.string.inbound_socks_port_key),
            getString(R.string.inbound_http_port_key)
        ).mapNotNull { key -> 
            findPreference<EditTextPreference>(key)
        }
        
        portPreferences.forEach { pref ->
            EditTextUtil(pref).setNumberOnly(1, 65535)
        }
        
        findPreference<EditTextPreference>(getString(R.string.server_mux_key))?.let { pref ->
            EditTextUtil(pref).setNumberOnly(0, 255)
        }
        
        findPreference<EditTextPreference>(getString(R.string.server_buffer_key))?.let { pref ->
            EditTextUtil(pref).setNumberOnly(1, 65535).setSuffix("KB")
        }
        
        findPreference<EditTextPreference>(getString(R.string.user_id_key))?.let { pref ->
            EditTextUtil(pref).setPasswordWithMask()
        }
        
        findPreference<EditTextPreference>(getString(R.string.server_idle_timeout_key))?.let { pref ->
            EditTextUtil(pref).setNumberOnly().setSuffix("s")
        }
    }
}