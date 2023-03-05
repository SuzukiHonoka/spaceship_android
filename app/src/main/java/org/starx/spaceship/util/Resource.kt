package org.starx.spaceship.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class Resource(private val ctx: Context) {
    companion object{
        const val TAG = "Resource"
    }
    fun extract(){
        val assets = ctx.assets
        val files = assets.list("opt")!!

        if (files.isEmpty()) return
        val path = ctx.filesDir
        try {
            files.forEach { file ->
                val out = File(path, file)
                Log.i(TAG, "extract: $file -> ${out.absolutePath}")
                val outStream = FileOutputStream(out)
                val inStream = assets.open("opt/$file")
                inStream.copyTo(outStream)
                inStream.close()
                outStream.close()
            }
        }catch (e: IOException){
            Log.e(TAG, "extract: $e" )
        }
    }
}