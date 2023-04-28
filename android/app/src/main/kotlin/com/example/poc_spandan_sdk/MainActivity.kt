package com.example.poc_spandan_sdk

import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import `in`.sunfox.healthcare.commons.android.sericom.SeriCom
import `in`.sunfox.healthcare.commons.android.spandan_sdk.OnInitializationCompleteListener
import `in`.sunfox.healthcare.commons.android.spandan_sdk.SpandanSDK
import `in`.sunfox.healthcare.commons.android.spandan_sdk.collection.EcgTest
import `in`.sunfox.healthcare.commons.android.spandan_sdk.collection.EcgTestCallback
import `in`.sunfox.healthcare.commons.android.spandan_sdk.conclusion.EcgReport
import `in`.sunfox.healthcare.commons.android.spandan_sdk.connection.OnDeviceConnectionStateChangeListener
import `in`.sunfox.healthcare.commons.android.spandan_sdk.enums.DeviceConnectionState
import `in`.sunfox.healthcare.commons.android.spandan_sdk.enums.EcgPosition
import `in`.sunfox.healthcare.commons.android.spandan_sdk.enums.EcgTestType
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodChannel


class MainActivity : FlutterActivity() {
    private val TAG = "MainActivity.TAG"
    private val CHANNEL = "com.example.poc_spandan_sdk/sericom"
    private val CHANNEL_EVENT = "com.example.poc_spandan_sdk/sericom_event"

    //sdk variables
    lateinit var span: SpandanSDK
    lateinit var token: String

    private lateinit var ecgTest: EcgTest
    private lateinit var ecgTestType: EcgTestType
    val hashMap = HashMap<EcgPosition, ArrayList<Double>>()
    private lateinit var ecgReport: EcgReport
    private val ecgDataHash = hashMapOf<EcgPosition, ArrayList<Double>>()

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel

    private var deviceStatusData = MutableLiveData<String>()
    private var timerData = MutableLiveData<String>()
    private var resultData = MutableLiveData<String>()


    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        Log.w(TAG, "configureFlutterEngine: Called....")
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "setUpConnection" -> {
                    setUpConnection()
                    result.success(true)
                }

                "sendCommand" -> {
                    //fetching params
                    val argument = call.argument<String>("command").toString()
                    when (argument) {
                        "0" -> {
//                            if (::span.isInitialized) span.unbind(application)
                            SeriCom.sendCommand(argument)
                        }

                        "1" -> {
                            val ecgPositionArray = arrayOf(EcgPosition.LEAD_2)
                            performLeadTest(EcgTestType.LEAD_TWO, ecgPositionArray, 0, ecgPositionArray.size)
                        }

                        "2" -> {
                            val ecgPositionArray = arrayOf(
                                EcgPosition.V1,
                                EcgPosition.V2,
                                EcgPosition.V3,
                                EcgPosition.V4,
                                EcgPosition.V5,
                                EcgPosition.V6,
                                EcgPosition.LEAD_2
                            )
                            performLeadTest(EcgTestType.HYPERKALEMIA, ecgPositionArray, 0, ecgPositionArray.size)
                        }
                        else -> {
                            SeriCom.sendCommand(argument)
                        }
                    }
                    result.success(true)
                }

                else -> {
                    result.notImplemented()
                }
            }
        }

        eventChannel = EventChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_EVENT)
        eventChannel.setStreamHandler(object : StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                //observe device response data
                resultData.observe(this@MainActivity, Observer {
                    events!!.success(resultData.value)
                })
            }

            override fun onCancel(arguments: Any?) {

            }

        })

        Log.w(TAG, "configureFlutterEngine: Called End....")
    }

    private fun performLeadTest(
        testType: EcgTestType, ecgPositionArray: Array<EcgPosition>, start: Int, end: Int
    ) {
        Log.d(TAG, "performLeadTest: EcgTestType --> $testType EcgPositionArray --> $ecgPositionArray")

        //do lead test
        var currentEcgIndex = start
        val lastEcgIndex = end

        span = SpandanSDK.getInstance()

        if (currentEcgIndex < lastEcgIndex) {
            val ecgPositionName = ecgPositionArray[currentEcgIndex]

            ecgTest = span.createTest(testType, object : EcgTestCallback {
                override fun onTestFailed(statusCode: Int) {
                    Log.e(TAG, "onTestFailed: $statusCode")
                    runOnUiThread {
                        resultData.value = "Failed with code $statusCode"
                    }
                }

                override fun onTestStarted(ecgPosition: EcgPosition) {
                    Log.d(TAG, "onTestStarted: EcgPosition -> $ecgPosition")
                    runOnUiThread {
                        resultData.value = "Started : $ecgPosition"
                    }
                }

                override fun onElapsedTimeChanged(elapsedTime: Long, remainingTime: Long) {
                    //update only lead 2 progress bar
                    runOnUiThread {
                        timerData.value = "Test Name : $ecgPositionName \nRemaining ${remainingTime.toInt()} : from ${elapsedTime.toInt()}"
                    }
                }

                override fun onReceivedData(data: String) {
                    Log.w(TAG, "onReceivedData: $data")
                    runOnUiThread {
                        resultData.value = "Data : $data"
                    }
                }

                override fun onPositionRecordingComplete(
                    ecgPosition: EcgPosition, ecgPoints: ArrayList<Double>?
                ) {
                    Log.d(TAG, "onPositionRecordingComplete: EcgPosition --> $ecgPosition : EcgPoints --> ${ecgPoints!!.size}")

                    //put all the ecgPoints in hashmap to generate report
                    hashMap[ecgPosition] = ecgPoints
                    Toast.makeText(this@MainActivity, "$ecgPositionName : Test Complete $currentEcgIndex : ${lastEcgIndex - 1}", Toast.LENGTH_SHORT).show()

                    //generate report if currentTest is lastTest
                    if (currentEcgIndex == lastEcgIndex - 1) {
                        Toast.makeText(this@MainActivity, "Report Generation work started...", Toast.LENGTH_SHORT).show()

                        /* //generate report
                         span.generateReport(32, hashMap, token, object : OnReportGenerationStateListener {
                                 override fun onReportGenerationSuccess(ecgReport: EcgReport) {
                                     val conclusion = ecgReport.conclusion as LeadTwoConclusion
                                     val characteristics = ecgReport.ecgCharacteristics
                                     Log.d(TAG, "onReportGenerationSuccess:  Conclusion --> $conclusion : Characteristics --> $characteristics")
                                     runOnUiThread {
                                         resultData = "${conclusion.detection} ${conclusion.ecgType}"
                                     }
                                 }

                                 override fun onReportGenerationFailed(errorCode: Int, errorMsg: String) {
                                     runOnUiThread {
                                         Log.e(TAG, "onReportGenerationFailed: $errorMsg")
                                         Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                                     }
                                 }
                             })*/
                    } else if (currentEcgIndex < lastEcgIndex) {//0 < 1
                        currentEcgIndex++
                        //start another task
                        performLeadTest(testType, ecgPositionArray, currentEcgIndex, lastEcgIndex)
                    }
                }
            }, token)

            ecgTest.start(ecgPositionName)
        }
    }

    private fun setUpConnection(){

        SpandanSDK.initialize(application, "4u838u43u439u3", object : OnInitializationCompleteListener {
                override fun onInitializationSuccess(authenticationToken: String) {
                    token = authenticationToken

                    Log.d(TAG, "onInitializationSuccess: $authenticationToken")
                    span = SpandanSDK.getInstance()

                    span.setOnDeviceConnectionStateChangedListener(object :
                        OnDeviceConnectionStateChangeListener {
                        override fun onDeviceConnectionStateChanged(deviceConnectionState: DeviceConnectionState) {
                            Log.d(TAG, "onDeviceConnectionStateChanged: $deviceConnectionState")
                            deviceStatusData.value = "$deviceConnectionState"
                        }

                        override fun onDeviceTypeChange(deviceType: String) {
                        }

                        override fun onDeviceVerified() {
                            Log.d(TAG, "onDeviceConnectionStateChanged: Device Verified")
                            deviceStatusData.value = "Device Verified..."
                        }

                    })
                }

                override fun onInitializationFailed(message: String) {
                }
            })
    }
}
