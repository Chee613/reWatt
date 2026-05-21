package com.rewatt.app.perception

import android.content.Context
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

object AssetModelLoader {
    fun assetExists(context: Context, assetName: String): Boolean =
        runCatching {
            context.assets.open(assetName).use { true }
        }.getOrDefault(false)

    fun loadBytes(context: Context, assetName: String): ByteArray =
        context.assets.open(assetName).use { it.readBytes() }

    fun loadTextOrNull(context: Context, assetName: String): String? =
        runCatching {
            context.assets.open(assetName).use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()

    fun loadDirectBuffer(context: Context, assetName: String): ByteBuffer {
        val bytes = loadBytes(context, assetName)
        return ByteBuffer
            .allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                rewind()
            }
    }

    fun readAssociatedTextFile(modelBytes: ByteArray, fileName: String): String? =
        ZipInputStream(ByteArrayInputStream(modelBytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == fileName }
                ?.let { zip.readBytes().toString(Charsets.UTF_8) }
        }
}
