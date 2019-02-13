package me.acuion.lockall_android

import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.acuion.lockall_android.crypto.EncryptionUtils
import me.acuion.lockall_android.storages.FirstComponentsStorage
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

// QR:
// 00/01 - safe or unsafe (without the first component or with)
// if unsafe, then the first component's length 4 bytes and the first component itself
// the second component length 4 bytes
// the second component itself
// IV 16 bytes
// the encrypted body length
// the encrypted body itself

// Unencrypted body TCP:
// host's ip 4 bytes
// host's port 4 bytes
// JSON user data...

// Unencrypted body BT:
// host's bluetooth mac 8 bytes
// host's server GUID 16 bytes
// JSON user data...

class QrMessage(base64Data: String, fcstorage : FirstComponentsStorage) {
    var firstComponent : ByteArray? = null
    val secondComponent : ByteArray
    var userDataJson : JsonObject = JsonObject()

    var commMode = CommMode.Wifi

    var hostTcpAddress : InetAddress? = null
    var hostTcpPort : Int = 0

    var hostBtMacAddress : ByteArray? = null
    var hostBtUuid : ByteArray? = null

    private fun tryToDecrypt(encryptedBody : ByteArray, key : ByteArray, iv : ByteArray) : Boolean {
        return try {
            val userBytes = ByteBuffer.wrap(EncryptionUtils.decryptDataWithAes256(encryptedBody, key, iv))
            userBytes.order(ByteOrder.LITTLE_ENDIAN)
            commMode = if (userBytes.get() == 1.toByte()) CommMode.Wifi else CommMode.Bluetooth
            if (commMode == CommMode.Wifi) {
                val hostIpBytes = ByteArray(4)
                userBytes.get(hostIpBytes)
                hostTcpAddress = InetAddress.getByAddress(hostIpBytes)
                hostTcpPort = userBytes.getInt()
            } else {
                hostBtMacAddress = ByteArray(8)
                hostBtUuid = ByteArray(16)
                userBytes.get(hostBtMacAddress)
                userBytes.get(hostBtUuid)
            }

            val userDataRaw = ByteArray(userBytes.remaining())
            userBytes.get(userDataRaw)
            userDataJson = JsonParser().parse(String(userDataRaw, Charset.forName("UTF-8"))).asJsonObject
            true
        } catch (ex : Exception) {
            false
        }
    }

    init {
        val qrBytes = ByteBuffer.wrap(Base64.decode(base64Data, 0))
        qrBytes.order(ByteOrder.LITTLE_ENDIAN)
        if (qrBytes.get().compareTo(1) == 0) {
            val fcLen = qrBytes.getInt()
            firstComponent = ByteArray(fcLen)
            qrBytes.get(firstComponent)
        }
        val scLen = qrBytes.getInt()
        secondComponent = ByteArray(scLen)
        qrBytes.get(secondComponent)
        val iv = ByteArray(16)
        qrBytes.get(iv)
        val encryptedBodyLen = qrBytes.getInt()
        val encryptedBody = ByteArray(encryptedBodyLen)
        qrBytes.get(encryptedBody)
        if (firstComponent != null) {
            tryToDecrypt(encryptedBody,
                    EncryptionUtils.produce256BitsFromComponents(firstComponent!!, secondComponent), iv)
        } else {
            fcstorage.firstComponents.forEach {
                if (tryToDecrypt(encryptedBody, EncryptionUtils.produce256BitsFromComponents(it, secondComponent), iv)) {
                    firstComponent = it
                    return@forEach
                }
            }
        }
    }

    enum class CommMode {
        Wifi,
        Bluetooth
    }
}