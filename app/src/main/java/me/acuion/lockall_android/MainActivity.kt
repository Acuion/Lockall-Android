package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import me.acuion.lockall_android.crypto.EncryptedJsonStorageManager
import me.acuion.lockall_android.crypto.EncryptionUtils
import me.acuion.lockall_android.messages.pairing.MessageWithName
import android.app.KeyguardManager
import android.content.Context
import me.acuion.lockall_android.messages.MessageStatus
import me.acuion.lockall_android.messages.Password.MessageWithPassword
import me.acuion.lockall_android.messages.Password.MessageWithResourceid
import me.acuion.lockall_android.storages.FirstComponentsStorage
import me.acuion.lockall_android.storages.PasswordsStorage


class MainActivity : Activity() {
    companion object {
        enum class RequestCodeEnum(val code : Int) {
            ScanQr(42),
            ProfileSelect(77),
            UserAuth(100)
        }
    }

    lateinit var keyguardManager : KeyguardManager

    lateinit var systemwideAuthSuccessCallback : () -> Unit
    lateinit var systemwideProfileSelectSuccessCallback : (selectedProfile : String) -> Unit

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_CANCELED) {
            return
        }

        when (requestCode) {
            RequestCodeEnum.UserAuth.code -> {
                systemwideAuthSuccessCallback()
            }

            RequestCodeEnum.ProfileSelect.code -> {
                systemwideProfileSelectSuccessCallback(data!!.getStringExtra("profile")!!)
            }

            RequestCodeEnum.ScanQr.code -> {
                val gson = Gson()
                authUser {
                    val firstComponentsEjsm = EncryptedJsonStorageManager(applicationContext,
                            EncryptedJsonStorageManager.Companion.Filename.FirstComponentsStorage)
                    val fcjo = firstComponentsEjsm.data
                    if (fcjo == null) {
                        Toast.makeText(applicationContext, "Cannot find corresponding keybase. Have you paired with the device?", Toast.LENGTH_SHORT).show()
                        return@authUser
                    }
                    val fcstorage = gson.fromJson(fcjo, FirstComponentsStorage::class.java)

                    val qrData = QrMessage(data!!.getStringExtra("data")!!, fcstorage)
                    val qrPrefix = data.getStringExtra("prefix")!!
                    if (qrData.firstComponent == null) {
                        Toast.makeText(applicationContext, "Cannot find corresponding keybase. Have you paired with the device?", Toast.LENGTH_SHORT).show()
                        return@authUser
                    }

                    when (qrPrefix) {
                        QrType.PAIRING.prefix -> {
                            val qrContent = gson.fromJson(qrData.userDataJson, MessageWithName::class.java)!!

                            fcstorage.put(qrContent.name, qrData.firstComponent!!)
                            try {
                                firstComponentsEjsm.data = gson.toJsonTree(fcstorage).asJsonObject
                            } catch (ex: Exception) {
                                Toast.makeText(applicationContext, "Failed to save updated storage", Toast.LENGTH_SHORT).show()
                                return@authUser
                            }
                            val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                    qrData.secondComponent)
                            val message = NetworkMessage(key,
                                    gson.toJsonTree(MessageWithName(qrContent.name)).asJsonObject)
                            message.send(qrData.hostAddress!!, qrData.hostPort)
                        }
                        QrType.STORE.prefix -> { // store
                            val qrContent = gson.fromJson(qrData.userDataJson, MessageWithPassword::class.java)!!

                            val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Companion.Filename.PasswordsStorage)
                            val pjo = ejsm.data
                            if (pjo == null) {
                                Toast.makeText(applicationContext, "Failed to read the storage", Toast.LENGTH_SHORT).show()
                                return@authUser
                            }
                            val storage = gson.fromJson(pjo, PasswordsStorage::class.java)
                            var currentProfiles = storage.getProfilesForResource(qrContent.resourceid)
                            if (currentProfiles == null)
                                currentProfiles = Array(0, {_ -> ""})
                            selectProfile(qrContent.resourceid, currentProfiles,
                                    true) {
                                storage.put(qrContent.resourceid, it, qrContent.password)
                                try {
                                    ejsm.data = gson.toJsonTree(storage).asJsonObject
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                    Toast.makeText(applicationContext, "Failed to save updated storage", Toast.LENGTH_SHORT).show()
                                    return@selectProfile
                                }
                                val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                        qrData.secondComponent)
                                val message = NetworkMessage(key,
                                        gson.toJsonTree(MessageStatus("Stored")).asJsonObject)
                                message.send(qrData.hostAddress!!, qrData.hostPort)
                            }
                        }
                        QrType.PULL.prefix -> { // pull
                            val qrContent = gson.fromJson(qrData.userDataJson, MessageWithResourceid::class.java)!!

                            val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Companion.Filename.PasswordsStorage)
                            val pjo = ejsm.data
                            if (pjo == null) {
                                Toast.makeText(applicationContext, "Failed to read the storage", Toast.LENGTH_SHORT).show()
                                return@authUser
                            }
                            val storage = gson.fromJson(pjo, PasswordsStorage::class.java)
                            val currentProfiles = storage.getProfilesForResource(qrContent.resourceid)
                            if (currentProfiles == null) {
                                Toast.makeText(applicationContext, "Nothing to send", Toast.LENGTH_SHORT).show()
                                return@authUser
                            }
                            selectProfile(qrContent.resourceid, currentProfiles,
                                    false) {
                                val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                        qrData.secondComponent)
                                val pass = storage.getPass(qrContent.resourceid, it)!!
                                val message = NetworkMessage(key,
                                        gson.toJsonTree(MessageWithPassword(qrContent.resourceid, pass)).asJsonObject)
                                message.send(qrData.hostAddress!!, qrData.hostPort)
                            }
                        }
                        else -> {
                            Toast.makeText(applicationContext, "Unrecognized QR type", Toast.LENGTH_LONG).show()
                        }
                    }
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

    fun selectProfile(resourceName : String, profiles : Array<String>, allowProfileCreation : Boolean,
                      successCallback : (selectedProfile : String) -> Unit) {
        systemwideProfileSelectSuccessCallback = successCallback
        if (!allowProfileCreation && profiles.size == 1) {
            systemwideProfileSelectSuccessCallback(profiles[0])
            return
        }
        val selectIntent = Intent(applicationContext, ProfileSelectorActivity::class.java)
        selectIntent.putExtra("allowProfileCreation", allowProfileCreation)
        selectIntent.putExtra("resourceName", resourceName)
        selectIntent.putExtra("accountsList", profiles)
        startActivityForResult(selectIntent, RequestCodeEnum.ProfileSelect.code)
    }

    fun launchQrActivity() {
        val qrIntent = Intent(applicationContext, ScanQrActivity::class.java)
        startActivityForResult(qrIntent, RequestCodeEnum.ScanQr.code)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keyguardManager = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (!keyguardManager.isDeviceSecure) {
            Toast.makeText(applicationContext, "The device should be locked", Toast.LENGTH_LONG).show()
            return
        }

        buttonScan.setOnClickListener {
            launchQrActivity()
        }
    }
}
