package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import me.acuion.lockall_android.crypto.EncryptedJsonStorageManager
import me.acuion.lockall_android.crypto.EncryptionUtils
import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.acuion.lockall_android.crypto.EncryptionUtils.Companion.completeEcdhGetKeyAndPublic
import me.acuion.lockall_android.messages.MessageStatus
import me.acuion.lockall_android.messages.Password.MessageWithPassword
import me.acuion.lockall_android.messages.Password.MessageWithResourceid
import me.acuion.lockall_android.messages.PcCommunicator
import me.acuion.lockall_android.storages.OtpDataStorage
import me.acuion.lockall_android.storages.PasswordsStorage
import org.apache.commons.codec.binary.Base32
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.experimental.and


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

    private fun issueToast(text: String) = runOnUiThread {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    private fun processLockallQr(qrData: String, prefix: String, overrideResourceId: String?) = GlobalScope.launch {
        val gson = Gson()
        val qrData = QrMessage(qrData)
        val pcComm = PcCommunicator(qrData.pcNetworkInfo)
        try {
            pcComm.connect(applicationContext)
        } catch (ex: Exception) {
            issueToast("Connection failed: ${ex.localizedMessage}")
            return@launch
        }
        val keyStructure = completeEcdhGetKeyAndPublic(qrData.pcEcdhPublicPemKey)
        try {
            pcComm.send(NetworkMessage(keyStructure))
        } catch (ex: Exception) {
            issueToast("ECDH sent failed: ${ex.localizedMessage}")
            return@launch
        }
        lateinit var decryptedPayload: String
        try {
            decryptedPayload = String(pcComm.readEncrypted(keyStructure.key),
                    Charset.forName("UTF-8"))
        } catch(ex: Exception) {
            issueToast("Data recieve failed: ${ex.localizedMessage}")
            return@launch
        }
        lateinit var userDataJson: JsonObject
        try {
            userDataJson = JsonParser().parse(decryptedPayload).asJsonObject
        } catch(ex: Exception) {
            issueToast("Message decode failed: ${ex.localizedMessage}")
            return@launch
        }

        when (prefix) {
            QrType.STORE.prefix -> {
                // store
                val qrContent = gson.fromJson(userDataJson, MessageWithPassword::class.java)!!

                val usedResourceid = overrideResourceId
                        ?: qrContent.resourceid

                val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Filename.PasswordsStorage)
                val pjo = ejsm.data
                val storage = if (pjo == null) null else gson.fromJson(pjo, PasswordsStorage::class.java)

                if (storage == null) {
                    issueToast("Failed to read the storage")
                    return@launch
                }
                var currentProfiles = storage.getProfilesForResource(usedResourceid)
                if (currentProfiles == null)
                    currentProfiles = Array(0) { _ -> "" }
                selectProfile(usedResourceid, currentProfiles,
                        true) {
                    storage.put(usedResourceid, it, qrContent.password)
                    try {
                        ejsm.data = gson.toJsonTree(storage).asJsonObject
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        issueToast("Failed to save updated storage")
                        return@selectProfile
                    }

                    val message = NetworkMessage(keyStructure.key,
                            gson.toJsonTree(MessageStatus("Stored")).asJsonObject)
                    try {
                        pcComm.send(message)
                    } catch (ex: Exception) {
                        issueToast("Feedback failed: ${ex.localizedMessage}")
                        return@selectProfile
                    }
                }
            }
            QrType.PULL.prefix -> {
                // pull password
                val qrContent = gson.fromJson(userDataJson, MessageWithResourceid::class.java)!!

                val usedResourceid = overrideResourceId
                        ?: qrContent.resourceid

                val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Filename.PasswordsStorage)
                val pjo = ejsm.data
                val storage = if (pjo == null) null else gson.fromJson(pjo, PasswordsStorage::class.java)

                if (storage == null) {
                    issueToast("Failed to read the storage")
                    return@launch
                }

                val currentProfiles = storage.getProfilesForResource(usedResourceid)
                if (currentProfiles == null) {
                    issueToast("Nothing to send")
                    return@launch
                }
                selectProfile(usedResourceid, currentProfiles,
                        false) {

                    val pass = storage.getPass(usedResourceid, it)!!
                    val message = NetworkMessage(keyStructure.key,
                            gson.toJsonTree(MessageWithPassword(usedResourceid, pass)).asJsonObject)
                    try {
                        pcComm.send(message)
                    } catch (ex: Exception) {
                        issueToast("Feedback failed: ${ex.localizedMessage}")
                        return@selectProfile
                    }
                }
            }
            QrType.OTP.prefix -> {
                // pull otp

                val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Filename.OtpsStorage)
                val pjo = ejsm.data
                if (pjo == null) {
                    issueToast("Failed to read the storage")
                    return@launch
                }
                val storage = gson.fromJson(pjo, OtpDataStorage::class.java)
                val currentProfiles = storage.getIssuerProfileKeys()
                if (currentProfiles.isEmpty()) {
                    issueToast("Nothing to send")
                    return@launch
                }
                // TODO("resource name")
                selectProfile("OTP", currentProfiles,
                        false) {
                    val secret = storage.getSecretFrom(it)
                    val currTime = (System.currentTimeMillis() / 1000) / 30 // 30 sec period

                    val secretBytes = Base32().decode(secret)
                    val timeBytes = ByteBuffer.allocate(8)
                    timeBytes.putLong(currTime)
                    val hmacBytes = EncryptionUtils.hmacSha1(timeBytes.array(), secretBytes)
                    val offset = (hmacBytes.last() and 0x0F).toInt()
                    val res1 = (hmacBytes[offset]).toInt().and(0x7F).shl(24)
                    val res2 = (hmacBytes[offset + 1]).toInt().and(0xFF).shl(16)
                    val res3 = (hmacBytes[offset + 2]).toInt().and(0xFF).shl(8)
                    val res4 = (hmacBytes[offset + 3]).toInt().and(0xFF)
                    val result = res1.or(res2).or(res3).or(res4)
                    val pass = (result.rem(1000000)).toString().padStart(6, '0')

                    val message = NetworkMessage(keyStructure.key,
                            gson.toJsonTree(MessageWithPassword("OTP", pass)).asJsonObject)
                    try {
                        pcComm.send(message)
                    } catch (ex: Exception) {
                        issueToast("Feedback failed: ${ex.localizedMessage}")
                        return@selectProfile
                    }
                }
            }
            else -> {
                issueToast("Unrecognized QR type")
            }
        }
    }

    private fun processOtpQr(qrData: String) = GlobalScope.launch {
        val gson = Gson()
        lateinit var secret : String
        lateinit var account : String
        lateinit var issuer : String
        try {
            val totpUri = Uri.parse(qrData)
            secret = totpUri.getQueryParameter("secret")!!
            val path = totpUri.path.substring(1)
            issuer = totpUri.getQueryParameter("issuer")
            if (path.contains(':')) {
                if (issuer == null)
                    issuer = path.split(':')[0]
                account = path.split(':')[1]
            } else {
                if (issuer == null)
                    issuer = "unknown"
                account = path
            }
        } catch (ex : Exception) {
            issueToast("Error parsing OTP QR")
            return@launch
        }
        val otpEjsm = EncryptedJsonStorageManager(applicationContext,
                EncryptedJsonStorageManager.Filename.OtpsStorage)
        val otpsStorageJson = otpEjsm.data
        if (otpsStorageJson == null) {
            issueToast("Cannot load OTPs storage")
            return@launch
        }
        val otpsStorage = gson.fromJson(otpsStorageJson, OtpDataStorage::class.java)
        otpsStorage.put(issuer, account, secret.toUpperCase())

        try {
            otpEjsm.data = gson.toJsonTree(otpsStorage).asJsonObject
        } catch (ex: Exception) {
            issueToast("Failed to save updated storage")
            return@launch
        }

        issueToast("OTP generator stored for $account from $issuer")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_CANCELED) {
            return
        }

        when (requestCode) {
            RequestCodeEnum.UserAuth.code -> GlobalScope.launch {
                systemwideAuthSuccessCallback()
            }

            RequestCodeEnum.ProfileSelect.code -> GlobalScope.launch {
                systemwideProfileSelectSuccessCallback(data!!.getStringExtra("profile")!!)
            }

            RequestCodeEnum.ScanQr.code -> {
                authUser {
                    when (data!!.getStringExtra("mode")!!) {
                        ScanQrActivity.QrScanMode.LOCKALL.mode -> {
                            processLockallQr(data.getStringExtra("data")!!,
                                    data.getStringExtra("prefix")!!,
                                    data.getStringExtra("overrideResourceid"))
                        }
                        ScanQrActivity.QrScanMode.OTP.mode -> {
                            processOtpQr(data.getStringExtra("data")!!)
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

    fun launchQrActivity(mode : ScanQrActivity.QrScanMode) {
        val qrIntent = Intent(applicationContext, ScanQrActivity::class.java)
        qrIntent.putExtra("mode", mode.mode)
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
            launchQrActivity(ScanQrActivity.QrScanMode.LOCKALL)
        }

        buttonOtp.setOnClickListener {
            launchQrActivity(ScanQrActivity.QrScanMode.OTP)
        }
    }
}
