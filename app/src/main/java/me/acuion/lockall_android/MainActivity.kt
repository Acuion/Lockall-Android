package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import me.acuion.lockall_android.crypto.EncryptedJsonStorageManager
import me.acuion.lockall_android.crypto.EncryptionUtils
import me.acuion.lockall_android.messages.pairing.MessageWithName
import android.app.KeyguardManager
import android.content.Context
import android.support.annotation.NonNull
import android.support.v4.os.CancellationSignal


class MainActivity : Activity() {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            42 -> { // pairing
                runOnUiThread {
                    val gson = Gson()

                    val base64FromQr = data!!.extras.getString("data")

                    val qrData = QrMessage(base64FromQr)
                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithName::class.java)!!

                    val fcm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Companion.Filename.FirstComponentsStorage)
                    fcm.loadData(CancellationSignal()) {
                        val storage = gson.fromJson(it, FirstComponentsStorage::class.java)
                        storage.put(qrContent.name, qrData.firstComponent!!)
                        fcm.setData(gson.toJsonTree(storage).asJsonObject, CancellationSignal()) {
                                val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent,
                                        qrData.secondComponent)
                                val message = NetworkMessage(key,
                                        gson.toJsonTree(MessageWithName(qrContent.name)).asJsonObject)
                                message.send(qrData.hostAddress, qrData.hostPort)
                        }
                    }
                }
            }
        }
    }

    enum class SensorState {
        NOT_SUPPORTED,
        NOT_BLOCKED,
        NO_FINGERPRINTS,
        READY
    }

    private fun checkSensorState(context: Context): SensorState {
        val fingerprintManager = FingerprintManagerCompat.from(context)
        if (fingerprintManager.isHardwareDetected) {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguardManager.isKeyguardSecure) {
                return SensorState.NOT_BLOCKED
            }
            return if (!fingerprintManager.hasEnrolledFingerprints()) {
                SensorState.NO_FINGERPRINTS
            } else SensorState.READY
        } else {
            return SensorState.NOT_SUPPORTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkSensorState(applicationContext) != SensorState.READY) {
            Toast.makeText(applicationContext, "Fingerprint problems", Toast.LENGTH_LONG).show()
            return
        }

        buttonPair.setOnClickListener {
            val qrIntent = Intent(applicationContext, ScanQrActivity::class.java)
            qrIntent.putExtra("prefix", "PAIRING")
            startActivityForResult(qrIntent, 42)
        }
    }
}
