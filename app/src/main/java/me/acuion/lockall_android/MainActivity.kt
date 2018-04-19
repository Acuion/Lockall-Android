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
    lateinit var keyguardManager : KeyguardManager

    lateinit var systemwideAuthSuccessCallback : () -> Unit

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100) {
            if (resultCode == RESULT_OK) {
                systemwideAuthSuccessCallback()
            }
            return
        }

        val gson = Gson()
        authUser {
            val firstComponentsEjsm = EncryptedJsonStorageManager(applicationContext,
                    EncryptedJsonStorageManager.Companion.Filename.FirstComponentsStorage)
            val fcjo = firstComponentsEjsm.data
            if (fcjo == null) {
                TODO("Failed")
            }
            val fcstorage = gson.fromJson(fcjo, FirstComponentsStorage::class.java)

            val qrData = QrMessage(data!!.getStringExtra("data")!!, fcstorage)
            if (qrData.firstComponent == null) {
                TODO("Failed")
            }

            when (requestCode) {
                42 -> { // pairing
                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithName::class.java)!!

                    fcstorage.put(qrContent.name, qrData.firstComponent!!)
                    try {
                        firstComponentsEjsm.data = gson.toJsonTree(fcstorage).asJsonObject
                    } catch (ex: Exception) {
                        TODO("Failed")
                    }
                    val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                            qrData.secondComponent)
                    val message = NetworkMessage(key,
                            gson.toJsonTree(MessageWithName(qrContent.name)).asJsonObject)
                    message.send(qrData.hostAddress!!, qrData.hostPort)
                }
                43 -> { // store
                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithPassword::class.java)!!

                    val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Companion.Filename.PasswordsStorage)
                    val pjo = ejsm.data
                    if (pjo == null) {
                        TODO("Failed")
                    }
                    val storage = gson.fromJson(pjo, PasswordsStorage::class.java)
                    storage.put(qrContent.resourceid, qrContent.password)
                    try {
                        ejsm.data = gson.toJsonTree(storage).asJsonObject
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        TODO("Failed")
                    }
                    val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                            qrData.secondComponent)
                    val message = NetworkMessage(key,
                            gson.toJsonTree(MessageStatus("Stored")).asJsonObject)
                    message.send(qrData.hostAddress!!, qrData.hostPort)
                }
                44 -> { // load
                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithResourceid::class.java)!!

                    val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Companion.Filename.PasswordsStorage)
                    val pjo = ejsm.data
                    if (pjo == null) {
                        TODO("Failed")
                    }
                    val storage = gson.fromJson(pjo, PasswordsStorage::class.java)
                    val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                            qrData.secondComponent)
                    val message = NetworkMessage(key,
                            gson.toJsonTree(MessageWithPassword(qrContent.resourceid, storage.getPass(qrContent.resourceid)!!)).asJsonObject)
                    message.send(qrData.hostAddress!!, qrData.hostPort)
                }
            }
        }
    }

    fun authUser(successCallback : () -> Unit) {
        systemwideAuthSuccessCallback = successCallback
        val authintent = keyguardManager.createConfirmDeviceCredentialIntent("Access to the lockall keystorage", "")
        if (authintent != null) {
            startActivityForResult(authintent, 100)
        } else {
            systemwideAuthSuccessCallback()
        }
    }

    fun launchQrActivity(requestCode: Int) {
        val qrIntent = Intent(applicationContext, ScanQrActivity::class.java)
        when (requestCode) {
            42 -> qrIntent.putExtra("prefix", "PAIRING")
            43 -> qrIntent.putExtra("prefix", "STORE")
            44 -> qrIntent.putExtra("prefix", "LOAD")
        }
        startActivityForResult(qrIntent, requestCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keyguardManager = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (!keyguardManager.isDeviceSecure) {
            Toast.makeText(applicationContext, "Should be locked", Toast.LENGTH_LONG).show()
            return
        }

        buttonPair.setOnClickListener {
            launchQrActivity(42)
        }
        buttonStore.setOnClickListener {
            launchQrActivity(43)
        }
        buttonLoad.setOnClickListener {
            launchQrActivity(44)
        }
    }
}
