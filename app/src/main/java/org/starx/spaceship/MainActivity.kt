package org.starx.spaceship

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.starx.spaceship.databinding.ActivityMainBinding
import org.starx.spaceship.store.FirstRun
import org.starx.spaceship.util.Resource

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
        firstRunResourceExtraction()
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
                this,
                "Notification permission is${if (granted) " " else " not "}granted",
                Toast.LENGTH_SHORT
            ).show()
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("BatteryLife")
    private fun checkAndRequestIgnoreBatteryOptimization() {
        val packageName: String = packageName
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        Toast.makeText(
            this,
            "Ignore battery optimization will help service keep-alive, please allow it",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun firstRunResourceExtraction() {
        Thread {
            val firstRun = FirstRun(this)
            if (!firstRun.firstRun) return@Thread
            Resource(this).extract()
            firstRun.firstRun = false
            Log.i("MainActivity", "onCreate: first-run resource extraction complete")
        }.start()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkAndRequestPermission() {
        val ret = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        val should = ret == PackageManager.PERMISSION_DENIED
        if (!should) return
        Toast.makeText(
            this,
            "Missing notification permission, requesting..",
            Toast.LENGTH_SHORT
        ).show()
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}