package com.example.genericaicamera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import androidx.core.graphics.set
import com.example.genericaicamera.Utils.MODEL_INPUTS_SIZE
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class MainViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    private var interpreter: Interpreter? = null
    private val context = application
    private val _maskBitmap = MutableStateFlow(createBitmap(1, 1))
    val maskBitmap = _maskBitmap.asStateFlow()
    private var androidCameraViewWidth = 0
    private var androidCameraViewHeight = 0

    init {
        loadModel()
    }

    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(Utils.MODEL_TFLITE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return retFile
    }

    // If LiteRT comes back with a fix for num of threads
//    private fun loadModel(model: String) {
//        val compatList = CompatibilityList()
//        val options = Interpreter.Options().apply{
//            if(compatList.isDelegateSupportedOnThisDevice){
//                // if the device has a supported GPU, add the GPU delegate
//                val delegateOptions = compatList.bestOptionsForThisDevice
//                this.addDelegate(GpuDelegate(delegateOptions))
//            } else {
//                // If the GPU is not supported, run on 7 threads
//                // Check instructions on how 7 threads were selected here
//                // https://ai.google.dev/edge/litert/models/measurement#native_benchmark_binary
//                this.setNumThreads(7)
//            }
//        }
//
//        interpreter = Interpreter(loadModelFile(model), options)
//    }

    // If you want to go with LiteRT
    private fun loadModel() {
        val options = Interpreter.Options().apply {
            this.setNumThreads(7)
        }

        interpreter = Interpreter(loadModelFile(), options)
    }

    // Runs face detection on live streaming cameras frame-by-frame
    fun detectLivestreamFrame(imageProxy: ImageProxy) {
        val frameTime = System.currentTimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            createBitmap(imageProxy.width, imageProxy.height)
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()
        // Rotate the frame received from the camera to be in the same direction as it'll be shown
        val matrix =
            Matrix().apply {
                // Log.v("rotationOfDevice", rotationOfDevice.toString())
                // Log.v("rotationProxy", imageProxy.imageInfo.rotationDegrees.toFloat().toString())
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                // postScale is used here because we're forcing using the front camera lens
                // This can be set behind a bool if the camera is togglable.
                // Not using postScale here with the front camera causes the horizontal axis
                // to be mirrored.
                // postScale(
                // -1f,
                // 1f,
                // imageProxy.width.toFloat(),
                // imageProxy.height.toFloat()
                // )
            }

        val rotatedBitmap =
            Bitmap.createBitmap(
                bitmapBuffer,
                0,
                0,
                bitmapBuffer.width,
                bitmapBuffer.height,
                matrix,
                true
            )

        // Some info about tensors
        // val inTensor  = interpreter?.getInputTensor(0)
        // val outTensor = interpreter?.getOutputTensor(0)

        // Log.v("tensors_", inTensor?.shape()?.get(0).toString() + " " + inTensor?.dataType())
        // Log.v("tensors_", outTensor?.shape()?.get(0).toString() + " " + outTensor?.dataType())

        // Inputs
        val imageProcessor =
            ImageProcessor.Builder()
                .add(
                    ResizeOp(
                        MODEL_INPUTS_SIZE,
                        MODEL_INPUTS_SIZE,
                        ResizeOp.ResizeMethod.BILINEAR
                    )
                )
                .add(NormalizeOp(0.0f, 255.0f))
                .build()
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(rotatedBitmap)
        tensorImage = imageProcessor.process(tensorImage)
        val inputTensorBuffer = tensorImage.buffer
        // val inputArray = arrayOf(inputTensorBuffer)

        // Outputs
        val probabilityBuffer = TensorBuffer.createFixedSize(
            intArrayOf(1, MODEL_INPUTS_SIZE, MODEL_INPUTS_SIZE, 1),
            DataType.FLOAT32
        )

        // val outputMap = HashMap<Int, Any>()
        // outputMap[0] = probabilityBuffer.buffer
        // outputMap[1] = probabilityBuffer2.buffer

        // interpreter?.runForMultipleInputsOutputs(inputArray, outputMap)
        interpreter?.run(inputTensorBuffer, probabilityBuffer.buffer)

        val floatArray = probabilityBuffer.floatArray

        val mask = convertFloatArrayToBitmap(floatArray)
        val resizedMask = mask.scale(androidCameraViewWidth, androidCameraViewHeight, false)

        _maskBitmap.value = resizedMask

        Log.v("time_", (System.currentTimeMillis() - frameTime).toString())

        rotatedBitmap.recycle()
    }

    private fun convertFloatArrayToBitmap(
        floatArray: FloatArray,
        imageWidth: Int = MODEL_INPUTS_SIZE,
        imageHeight: Int = MODEL_INPUTS_SIZE
    ): Bitmap {
        val bitmap = createBitmap(imageWidth, imageHeight)
        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                val index = y * imageWidth + x
                if (floatArray[index] > 0.05) { // threshold from python notebook
                    bitmap[x, y] = Color.RED
                } else {
                    bitmap[x, y] = Color.TRANSPARENT
                }
            }
        }
        return bitmap
    }

    fun updateCameraViewValues(width: Int, height: Int) {
        androidCameraViewWidth = width
        androidCameraViewHeight = height
    }

    override fun onCleared() {
        super.onCleared()
        interpreter?.close()
    }
}
