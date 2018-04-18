package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
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
import android.support.v4.os.CancellationSignal
import me.acuion.lockall_android.messages.MessageStatus
import me.acuion.lockall_android.messages.Password.MessageWithPassword
import me.acuion.lockall_android.messages.Password.MessageWithResourceid
import me.acuion.lockall_android.storages.FirstComponentsStorage
import me.acuion.lockall_android.storages.PasswordsStorage


class MainActivity : Activity() {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val gson = Gson()
        val base64FromQr = data!!.extras.getString("data")

        val firstComponentsEjsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Companion.Filename.FirstComponentsStorage)
        firstComponentsEjsm.loadData(CancellationSignal()) {
            if (it == null) {
                TODO("Failed")
            }
            val fcstorage = gson.fromJson(it, FirstComponentsStorage::class.java)

            val qrData = QrMessage(base64FromQr, fcstorage)
            if (qrData.firstComponent == null) {
                TODO("Failed")
            }

            when (requestCode) {
                42 -> { // pairing
                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithName::class.java)!!

                    fcstorage.put(qrContent.name, qrData.firstComponent!!)
                    firstComponentsEjsm.setData(gson.toJsonTree(fcstorage).asJsonObject, CancellationSignal()) {
                        if (!it) {
                            TODO("Failed")
                        }
                        val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                qrData.secondComponent)
                        val message = NetworkMessage(key,
                                gson.toJsonTree(MessageWithName(qrContent.name)).asJsonObject)
                        message.send(qrData.hostAddress!!, qrData.hostPort)
                    }
                }
                43 -> { // store
                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithPassword::class.java)!!

                    val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Companion.Filename.PasswordsStorage)
                    ejsm.loadData(CancellationSignal()) {
                        if (it == null) {
                            TODO("Failed")
                        }
                        val storage = gson.fromJson(it, PasswordsStorage::class.java)
                        storage.put(qrContent.resourceid, qrContent.password)
                        ejsm.setData(gson.toJsonTree(storage).asJsonObject, CancellationSignal()) {
                            if (!it) {
                                TODO("Failed")
                            }

                            val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                    qrData.secondComponent)
                            val message = NetworkMessage(key,
                                    gson.toJsonTree(MessageStatus("Stored")).asJsonObject)
                            message.send(qrData.hostAddress!!, qrData.hostPort)
                        }
                    }
                }
                44 -> { // load
                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithResourceid::class.java)!!

                    val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Companion.Filename.PasswordsStorage)
                    ejsm.loadData(CancellationSignal()) {
                        if (it == null) {
                            TODO("Failed")
                        }
                        val storage = gson.fromJson(it, PasswordsStorage::class.java)
                        val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                qrData.secondComponent)
                        val message = NetworkMessage(key,
                                gson.toJsonTree(MessageWithPassword(qrContent.resourceid, storage.getPass(qrContent.resourceid)!!)).asJsonObject)
                        message.send(qrData.hostAddress!!, qrData.hostPort)
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
        buttonStore.setOnClickListener {
            val qrIntent = Intent(applicationContext, ScanQrActivity::class.java)
            qrIntent.putExtra("prefix", "STORE")
            startActivityForResult(qrIntent, 43)
        }
        buttonLoad.setOnClickListener {
            val qrIntent = Intent(applicationContext, ScanQrActivity::class.java)
            qrIntent.putExtra("prefix", "LOAD")
            startActivityForResult(qrIntent, 44)
        }
    }
}
