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
import me.acuion.lockall_android.messages.MessageStatus
import me.acuion.lockall_android.messages.Password.MessageWithPassword
import me.acuion.lockall_android.messages.Password.MessageWithResourceid
import me.acuion.lockall_android.storages.OtpDataStorage
import me.acuion.lockall_android.storages.PasswordsStorage
import org.apache.commons.codec.binary.Base32
import java.nio.ByteBuffer
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

    private fun getPasswordsStorage(): PasswordsStorage? {
        val gson = Gson()
        val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Filename.PasswordsStorage)
        val pjo = ejsm.data ?: return null
        return gson.fromJson(pjo, PasswordsStorage::class.java)
    }

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
                    when (data!!.getStringExtra("mode")!!) {
                        ScanQrActivity.QrScanMode.LOCKALL.mode -> {
                            val qrData = QrMessage(data.getStringExtra("data")!!)
                            val qrPrefix = data.getStringExtra("prefix")!!
                            val qrOverrideResourceid = data.getStringExtra("overrideResourceid")

                            when (qrPrefix) {
                                QrType.STORE.prefix -> {
                                    // store
                                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithPassword::class.java)!!

                                    val usedResourceid = qrOverrideResourceid ?: qrContent.resourceid

                                    val storage = getPasswordsStorage()
                                    if (storage == null) {
                                        Toast.makeText(applicationContext, "Failed to read the storage", Toast.LENGTH_SHORT).show()
                                        return@authUser
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
                                            Toast.makeText(applicationContext, "Failed to save updated storage", Toast.LENGTH_SHORT).show()
                                            return@selectProfile
                                        }
                                        val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                                qrData.pcEcdhPublicBytes)
                                        val message = NetworkMessage(key,
                                                gson.toJsonTree(MessageStatus("Stored")).asJsonObject)
                                        message.send(qrData.pcNetworkInfo)
                                    }
                                }
                                QrType.PULL.prefix -> {
                                    // pull password
                                    val qrContent = gson.fromJson(qrData.userDataJson, MessageWithResourceid::class.java)!!

                                    val usedResourceid = qrOverrideResourceid ?: qrContent.resourceid

                                    val storage = getPasswordsStorage()
                                    if (storage == null) {
                                        Toast.makeText(applicationContext, "Failed to read the storage", Toast.LENGTH_SHORT).show()
                                        return@authUser
                                    }

                                    val currentProfiles = storage.getProfilesForResource(usedResourceid)
                                    if (currentProfiles == null) {
                                        Toast.makeText(applicationContext, "Nothing to send", Toast.LENGTH_SHORT).show()
                                        return@authUser
                                    }
                                    selectProfile(usedResourceid, currentProfiles,
                                            false) {
                                        val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                                qrData.pcEcdhPublicBytes)
                                        val pass = storage.getPass(usedResourceid, it)!!
                                        val message = NetworkMessage(key,
                                                gson.toJsonTree(MessageWithPassword(usedResourceid, pass)).asJsonObject)
                                        message.send(qrData.pcNetworkInfo)
                                    }
                                }
                                QrType.OTP.prefix -> {
                                    // pull otp

                                    val ejsm = EncryptedJsonStorageManager(applicationContext, EncryptedJsonStorageManager.Filename.OtpsStorage)
                                    val pjo = ejsm.data
                                    if (pjo == null) {
                                        Toast.makeText(applicationContext, "Failed to read the storage", Toast.LENGTH_SHORT).show()
                                        return@authUser
                                    }
                                    val storage = gson.fromJson(pjo, OtpDataStorage::class.java)
                                    val currentProfiles = storage.getIssuerProfileKeys()
                                    if (currentProfiles.isEmpty()) {
                                        Toast.makeText(applicationContext, "Nothing to send", Toast.LENGTH_SHORT).show()
                                        return@authUser
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
                                        val res4 = (hmacBytes[offset + 3] ).toInt().and(0xFF)
                                        val result = res1.or(res2).or(res3).or(res4)
                                        val pass = (result.rem(1000000)).toString().padStart(6, '0')

                                        val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                                                qrData.pcEcdhPublicBytes)
                                        val message = NetworkMessage(key,
                                                gson.toJsonTree(MessageWithPassword("OTP", pass)).asJsonObject)
                                        message.send(qrData.pcNetworkInfo)
                                    }
                                }
                                else -> {
                                    Toast.makeText(applicationContext, "Unrecognized QR type", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        ScanQrActivity.QrScanMode.OTP.mode -> {
                            lateinit var secret : String
                            lateinit var account : String
                            lateinit var issuer : String
                            try {
                                val totpUri = Uri.parse(data.getStringExtra("data")!!)
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
                                Toast.makeText(applicationContext, "Error parsing OTP QR", Toast.LENGTH_SHORT).show()
                                return@authUser
                            }
                            val otpEjsm = EncryptedJsonStorageManager(applicationContext,
                                    EncryptedJsonStorageManager.Filename.OtpsStorage)
                            val otpsStorageJson = otpEjsm.data
                            if (otpsStorageJson == null) {
                                Toast.makeText(applicationContext, "Cannot load OTPs storage", Toast.LENGTH_SHORT).show()
                                return@authUser
                            }
                            val otpsStorage = gson.fromJson(otpsStorageJson, OtpDataStorage::class.java)
                            otpsStorage.put(issuer, account, secret)

                            try {
                                otpEjsm.data = gson.toJsonTree(otpsStorage).asJsonObject
                            } catch (ex: Exception) {
                                Toast.makeText(applicationContext, "Failed to save updated storage", Toast.LENGTH_SHORT).show()
                                return@authUser
                            }

                            Toast.makeText(applicationContext, "OTP generator stored for $account from $issuer", Toast.LENGTH_SHORT).show()
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
