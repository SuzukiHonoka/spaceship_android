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
    }

    fun extract() {
        val assets = ctx.assets
        val files = assets.list(OPT_DIR)
        if (files.isNullOrEmpty()) return

        val path = ctx.filesDir
        files.forEach { file ->
            val out = File(path, file)
            if (out.exists()) {
                Log.w(TAG, "File already exists, overwriting: ${out.absolutePath}")
            } else {
                Log.i(TAG, "Extracting: $file -> ${out.absolutePath}")
            }
            val outStream = FileOutputStream(out)
            val inStream = assets.open("$OPT_DIR/$file")
            inStream.copyTo(outStream)
            inStream.close()
            outStream.close()
        }
    }

    fun getFile(name: String): InputStream {
        return ctx.assets.open("$OPT_DIR/$name")
    }
}