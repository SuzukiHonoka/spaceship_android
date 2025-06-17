package org.starx.spaceship

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.starx.spaceship.databinding.ActivityMainBinding
import org.starx.spaceship.store.Runtime
import org.starx.spaceship.util.Resource
import org.starx.spaceship.util.Resource.Companion.TAG
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_logs
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // first run resource extraction
        firstRunResourceExtraction()

        // permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkAndRequestPermission()
        }
        checkAndRequestIgnoreBatteryOptimization()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) return
        if (requestCode == 1) {
            val granted = grantResults.first() == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                applicationContext,
                "Notification permission is${if (granted) " " else " not "}granted",
                Toast.LENGTH_SHORT
            ).show()
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("BatteryLife")
    private fun checkAndRequestIgnoreBatteryOptimization() {
        val packageName: String = packageName
        val pm = getSystemService(POWER_SERVICE) as PowerManager

        // Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        Toast.makeText(
            applicationContext,
            "Ignore battery optimization will help keeping service running, please allow it",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = "package:$packageName".toUri()
        }
        startActivity(intent)
    }

    private fun firstRunResourceExtraction() {
        // first run check
        val runtime = Runtime(applicationContext)
        if (runtime.firstRun) runtime.firstRun = false

        // check version
        val resVersion = runtime.resourceVersion
        if (resVersion < Resource.VERSION) {
            Log.i(TAG, "current res version: $resVersion extract: ${Resource.VERSION}")
            Thread {
                // extract resource
                try {
                    Resource(applicationContext).extract()
                }
                catch (e: IOException) {
                    Log.e(TAG, "extract resource failed: $e")
                    return@Thread
                }

                runtime.resourceVersion = Resource.VERSION
                Log.i(TAG, "extract version: ${Resource.VERSION} done")
            }.start()
            return
        }

        Log.i(TAG, "current res version: $resVersion")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkAndRequestPermission() {
        val permissions = mutableSetOf(
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.QUERY_ALL_PACKAGES,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
        }

        permissions.forEach {
            permission ->
            val ret = checkSelfPermission(permission)
            if (ret != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    applicationContext,
                    "Missing permission: $permission, requesting..",
                    Toast.LENGTH_SHORT
                ).show()
                requestPermissions(arrayOf(permission), 1)
            }
        }
    }
}