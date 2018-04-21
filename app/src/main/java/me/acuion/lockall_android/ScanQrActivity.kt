package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View.GONE
import android.view.View.VISIBLE
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import kotlinx.android.synthetic.main.activity_scanqr.*
import java.io.IOException

class ScanQrActivity : Activity() {
    enum class QrScanMode(val mode : String) {
        LOCKALL("LOCKALL"),
        OTP("OTP")
    }

    lateinit var qrDetecor : BarcodeDetector
    lateinit var cameraSource: CameraSource

    var needConfirmation = false
    val resultIntent = Intent()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        when (requestCode)
        {
            1001 -> { // camera
                if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
                try {
                    cameraSource.start(cameraPreview.holder)
                }
                catch (e : IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun lockallQrDetectionsHandler(p0: Detector.Detections<Barcode>?) {
        if (p0!!.detectedItems.size() == 0) {
            txtresult.post {
                txtresult.text = getString(R.string.scan_qr)
                needConfirmation = false
            }
            return
        }
        if (!p0.detectedItems.valueAt(0).displayValue.startsWith("LOCKALL:")) {
            txtresult.post {
                txtresult.text = getString(R.string.not_lockall_qr)
                needConfirmation = false
            }
            return
        }
        val data = p0.detectedItems.valueAt(0).displayValue.substring(8)
        if (data.indexOf(":") == -1)
            return
        val prefix = data.split(':')[0]
        resultIntent.putExtra("prefix", prefix)
        resultIntent.putExtra("data", data.split(':')[1])
        if (prefix == QrType.PAIRING.prefix) {
            txtresult.post {
                txtresult.text = getString(R.string.pair_qr)
                needConfirmation = true
            }
        } else {
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    fun otpQrDetectionsHander(p0: Detector.Detections<Barcode>?) {
        if (p0!!.detectedItems.size() == 0) {
            txtresult.post {
                txtresult.text = getString(R.string.scan_qr)
                needConfirmation = false
            }
            return
        }
        if (!p0.detectedItems.valueAt(0).displayValue.startsWith("otpauth://")) {
            txtresult.post {
                txtresult.text = getString(R.string.not_otp_qr)
                needConfirmation = false
            }
            return
        }
        resultIntent.putExtra("data", p0.detectedItems.valueAt(0).displayValue)
        txtresult.post {
            txtresult.text = getString(R.string.otp_qr)
            needConfirmation = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanqr)

        resultIntent.putExtra("mode", intent.getStringExtra("mode")!!)

        qrDetecor = BarcodeDetector.Builder(this).setBarcodeFormats(Barcode.QR_CODE).build()
        cameraSource = CameraSource.Builder(this, qrDetecor).setRequestedPreviewSize(640, 480).setAutoFocusEnabled(true).build()

        cameraPreview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(p0: SurfaceHolder?) {
                cameraSource.stop()
            }

            override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
            }

            override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
                if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(Array(1){android.Manifest.permission.CAMERA}, 1001)
                    return
                }
                try {
                    cameraSource.start(cameraPreview.holder)
                }
                catch (e : IOException) {
                    e.printStackTrace()
                }
            }
        })

        qrDetecor.setProcessor(object: Detector.Processor<Barcode>{
            override fun receiveDetections(p0: Detector.Detections<Barcode>?) {
                when (intent.getStringExtra("mode")!!) {
                    QrScanMode.LOCKALL.mode -> {
                        lockallQrDetectionsHandler(p0)
                    }
                    QrScanMode.OTP.mode -> {
                        otpQrDetectionsHander(p0)
                    }
                }
            }

            override fun release() {
            }

        })

        cameraPreview.setOnClickListener {
            if (!needConfirmation)
                return@setOnClickListener
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}
