package com.rewatt.app.perception

import android.content.Context
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter

class YamnetDetector private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    val inputSampleCount: Int,
) : Closeable {
    fun detect(samples: FloatArray): List<SoundDetection> {
        val preparedSamples = FloatArray(inputSampleCount)
        samples.copyInto(
            destination = preparedSamples,
            endIndex = minOf(samples.size, inputSampleCount),
        )

        val output = Array(1) { FloatArray(labels.size.coerceAtLeast(DEFAULT_LABEL_COUNT)) }
        interpreter.run(preparedSamples, output)
        return YamnetPostProcessor.rank(output[0], labels)
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val MODEL_ASSET = "yamnet.tflite"
        private const val CLASS_MAP_ASSET = "yamnet_class_map.csv"
        private const val LABEL_ASSET = "yamnet_label_list.txt"
        private const val DEFAULT_LABEL_COUNT = 521

        fun create(context: Context): YamnetDetector {
            val modelBytes = AssetModelLoader.loadBytes(context, MODEL_ASSET)
            val modelBuffer = ByteBuffer
                .allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(modelBytes)
                    rewind()
                }
            val labels = readClassMapLabels(context)
                .ifEmpty {
                    AssetModelLoader
                        .readAssociatedTextFile(modelBytes, LABEL_ASSET)
                        ?.lineSequence()
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.toList()
                        .orEmpty()
                }

            val interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2))
            val inputShape = interpreter.getInputTensor(0).shape()
            val inputSampleCount = inputShape.firstOrNull() ?: 15_600

            return YamnetDetector(
                interpreter = interpreter,
                labels = labels.ifEmpty { fallbackYamnetLabels },
                inputSampleCount = inputSampleCount,
            )
        }

        private fun readClassMapLabels(context: Context): List<String> =
            AssetModelLoader.loadTextOrNull(context, CLASS_MAP_ASSET)
                ?.let(YamnetClassMap::parseLabels)
                .orEmpty()
    }
}

private val fallbackYamnetLabels = List(521) { "sound class $it" }
