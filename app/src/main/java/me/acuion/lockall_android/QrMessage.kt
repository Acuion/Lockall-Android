package me.acuion.lockall_android

import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.acuion.lockall_android.crypto.EncryptionUtils
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
// host's bluetooth mac 17 bytes string
// host's server GUID 36 bytes string
// JSON user data...

class QrMessage(base64Data: String) {
    val pcEcdhPublicPemKey : ByteArray
    var pcNetworkInfo = PcNetworkInfo()

    init {
        val qrBytes = ByteBuffer.wrap(Base64.decode(base64Data, 0))
        qrBytes.order(ByteOrder.LITTLE_ENDIAN)
        val pcEcdhPublicLen = qrBytes.getInt()
        pcEcdhPublicPemKey = ByteArray(pcEcdhPublicLen)
        qrBytes.get(pcEcdhPublicPemKey)

        val userDataLength = qrBytes.getInt()
        val userDataBytes = ByteArray(userDataLength)
        qrBytes.get(userDataBytes)

        val userBytes = ByteBuffer.wrap(userDataBytes)
        userBytes.order(ByteOrder.LITTLE_ENDIAN)
        pcNetworkInfo.commMode = if (userBytes.get() == 1.toByte()) PcNetworkInfo.CommMode.Wifi else PcNetworkInfo.CommMode.Bluetooth
        if (pcNetworkInfo.commMode == PcNetworkInfo.CommMode.Wifi) {
            val hostIpBytes = ByteArray(4)
            userBytes.get(hostIpBytes)
            pcNetworkInfo.hostTcpAddress = InetAddress.getByAddress(hostIpBytes)
            pcNetworkInfo.hostTcpPort = userBytes.getInt()
        } else {
            val hostBtMacAddressBytes = ByteArray(17)
            val hostBtUuidBytes = ByteArray(36)
            userBytes.get(hostBtMacAddressBytes)
            userBytes.get(hostBtUuidBytes)
            pcNetworkInfo.hostBtMacAddress = String(hostBtMacAddressBytes, Charset.forName("UTF-8"))
            pcNetworkInfo.hostBtUuid = String(hostBtUuidBytes, Charset.forName("UTF-8"))
        }
    }
}