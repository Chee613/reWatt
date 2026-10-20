package com.rewatt.app.security

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Registered FAMILY face samples, stored as files (not Room: the database uses destructive
 * migration, which would wipe registered faces on the next schema bump).
 * Layout: filesDir/security/family/<id>.emb (embedding) + <id>.jpg (112×112 aligned thumbnail).
 */
class FamilyFaceStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "security/family").apply { mkdirs() }
    private val mutableSamples = MutableStateFlow(load())
    val samples: StateFlow<List<FaceSample>> = mutableSamples.asStateFlow()

    @Synchronized
    fun add(embedding: FloatArray, thumbnail: Bitmap): FaceSample {
        val id = UUID.randomUUID().toString().take(12)
        val createdAt = System.currentTimeMillis()
        val thumbFile = File(dir, "$id.jpg")
        thumbFile.outputStream().use { thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        DataOutputStream(File(dir, "$id.emb").outputStream().buffered()).use { out ->
            out.writeInt(FILE_VERSION)
            out.writeLong(createdAt)
            out.writeInt(embedding.size)
            embedding.forEach(out::writeFloat)
        }
        val sample = FaceSample(id, FAMILY_LABEL, createdAt, embedding, thumbFile.absolutePath)
        mutableSamples.value = (mutableSamples.value + sample).sortedBy { it.createdAtMillis }
        return sample
    }

    @Synchronized
    fun delete(id: String) {
        File(dir, "$id.emb").delete()
        File(dir, "$id.jpg").delete()
        mutableSamples.value = mutableSamples.value.filterNot { it.id == id }
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        mutableSamples.value = emptyList()
    }

    private fun load(): List<FaceSample> =
        dir.listFiles { file -> file.extension == "emb" }.orEmpty().mapNotNull { file ->
            runCatching {
                DataInputStream(file.inputStream().buffered()).use { input ->
                    check(input.readInt() == FILE_VERSION)
                    val createdAt = input.readLong()
                    val size = input.readInt()
                    val embedding = FloatArray(size) { input.readFloat() }
                    val id = file.nameWithoutExtension
                    FaceSample(id, FAMILY_LABEL, createdAt, embedding, File(dir, "$id.jpg").absolutePath)
                }
            }.onFailure { Log.w(TAG, "Skipping unreadable face sample ${file.name}", it) }.getOrNull()
        }.sortedBy { it.createdAtMillis }

    companion object {
        private const val TAG = "FamilyFaceStore"
        private const val FILE_VERSION = 1
        const val FAMILY_LABEL = "FAMILY"
    }
}
