package org.starx.spaceship.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class Resource(private val ctx: Context) {
    companion object {
        const val TAG = "Resource"
        const val VERSION = 1

        const val OPT_DIR = "opt"
        const val OPT_ASSET_CN_AGGREGATED_ZONE_V4 = "cn-aggregated-v4.zone"
        const val OPT_ASSET_CN_AGGREGATED_ZONE_V6 = "cn-aggregated-v6.zone"
        const val OPT_ASSET_CHINALIST = "chinalist.txt"
        const val OPT_ASSET_FAKECA = "fakeca.pem"

        val LAN_CIDR = listOf(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16"
        )
    }

    fun extract() {
        val assets = ctx.assets
        val files = runCatching { assets.list(OPT_DIR) }
            .getOrElse { 
                Log.e(TAG, "Failed to list assets in $OPT_DIR", it)
                return
            }
        
        if (files.isNullOrEmpty()) {
            Log.w(TAG, "No files found in $OPT_DIR directory")
            return
        }

        val path = ctx.filesDir
        var extractedCount = 0
        var errorCount = 0
        
        files.forEach { file ->
            runCatching {
                val out = File(path, file)
                if (out.exists()) {
                    Log.w(TAG, "File already exists, overwriting: ${out.absolutePath}")
                } else {
                    Log.i(TAG, "Extracting: $file -> ${out.absolutePath}")
                }
                
                assets.open("$OPT_DIR/$file").use { inStream ->
                    FileOutputStream(out).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                extractedCount++
                Log.d(TAG, "Successfully extracted: $file")
            }.onFailure { e ->
                Log.e(TAG, "Failed to extract file: $file", e)
                errorCount++
            }
        }
        
        Log.i(TAG, "Extraction completed: $extractedCount successful, $errorCount errors")
        if (errorCount > 0) {
            throw RuntimeException("Failed to extract $errorCount files")
        }
    }

    fun getFile(name: String): InputStream {
        return runCatching {
            ctx.assets.open("$OPT_DIR/$name")
        }.onFailure { e ->
            Log.e(TAG, "Failed to open asset file: $name", e)
        }.getOrThrow()
    }
}