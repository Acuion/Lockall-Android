package me.acuion.lockall_android

import com.google.gson.JsonObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.acuion.lockall_android.crypto.EncryptionUtils
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import android.support.v4.app.ActivityCompat.startActivityForResult
import android.content.Intent
import java.util.*


class NetworkMessage(key : ByteArray, userDataJson : JsonObject) {
    val REQUEST_ENABLE_BT = 1

    val readyMessage : ByteArray

    init {
        val iv = EncryptionUtils.generate128bitIv()
        val encryptedUserData = EncryptionUtils.encryptDataWithAes256(
                userDataJson.toString().toByteArray(Charset.forName("UTF-8")), key, iv)

        val message  = ByteBuffer.allocate(4 + iv.size + encryptedUserData.size)
        message.order(ByteOrder.LITTLE_ENDIAN)
        message.put(iv)
        message.putInt(encryptedUserData.size)
        message.put(encryptedUserData)
        readyMessage = message.array()
    }

    fun send(pcNetworkInfo: PcNetworkInfo) : Job {
        return GlobalScope.launch {
            //TODO("check it")
            if (pcNetworkInfo.commMode == PcNetworkInfo.CommMode.Wifi) {
                // TCP
                val toHostConn = Socket(pcNetworkInfo.hostTcpAddress, pcNetworkInfo.hostTcpPort)
                toHostConn.outputStream.write(readyMessage)
                toHostConn.close()
            } else {
                // BT
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
                if (!bluetoothAdapter.isEnabled) {
                    return@launch // TODO
                }
                val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
                pairedDevices?.forEach { device ->
                    val deviceName = device.name
                    val deviceHardwareAddress = device.address // MAC address

                    if (deviceHardwareAddress == pcNetworkInfo.hostBtMacAddress) {
                        val connUUID = UUID.fromString(pcNetworkInfo.hostBtUuid)
                        val socket = device.createInsecureRfcommSocketToServiceRecord(connUUID)
                        socket.connect()
                        socket.outputStream.write(readyMessage)
                        socket.close()
                    }
                }
                // TODO: pairing
            }
        }
    }
}