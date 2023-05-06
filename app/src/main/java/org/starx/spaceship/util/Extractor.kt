package org.starx.spaceship.util

class Extractor {
    companion object{
        fun extractPort(ns: String): Int{
            val index = ns.lastIndexOf(':')
            if (index != -1)
            {
                return ns.substring(index+1).toInt()
            }
            return ns.toInt()
        }
    }
}