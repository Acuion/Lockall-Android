package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import kotlinx.android.synthetic.main.activity_pairing.*
import java.io.IOException

class PairingActivity : Activity() {

    lateinit var qrDetecor : BarcodeDetector
    lateinit var cameraSource: CameraSource

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

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
                if (p0!!.detectedItems.size() == 0)
                    return
                if (!p0.detectedItems.valueAt(0).displayValue.startsWith("LOCKALL:")) {
                    txtresult.post {
                        txtresult.text = "It is not a Lockall qr!"
                    }
                    return
                }
                var resultIntent = Intent()
                resultIntent.putExtra("qr", p0.detectedItems.valueAt(0).displayValue.substring(8))
                setResult(RESULT_OK, resultIntent)
                finish()
            }

            override fun release() {
            }

        })
    }
}
