package me.acuion.lockall_android.messages

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.acuion.lockall_android.NetworkMessage
import me.acuion.lockall_android.PcNetworkInfo
import me.acuion.lockall_android.crypto.EncryptionUtils
import java.io.DataInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class PcCommunicator(val pcNetworkInfo: PcNetworkInfo) {
    private lateinit var outputStream: OutputStream
    private lateinit var intputStream: InputStream

    fun connect() : Job {
        return GlobalScope.launch {
            if (pcNetworkInfo.commMode == PcNetworkInfo.CommMode.Wifi) {
                // TCP
                val toHostConn = Socket(pcNetworkInfo.hostTcpAddress, pcNetworkInfo.hostTcpPort)
                outputStream = toHostConn.outputStream
                intputStream = toHostConn.inputStream
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
                        outputStream = socket.outputStream
                        intputStream = socket.inputStream
                    }
                }
                // TODO: pairing
            }
        }
    }

    fun send(message: NetworkMessage)
    {
        outputStream.write(message.readyMessage)
    }

    fun readEncrypted(aes256Key: ByteArray) : ByteArray
    {
        val dis = InputStreamReader(intputStream)

        val iv = ByteArray(16)
        dis.readFully(iv)
        val msgLenBytes = ByteArray(4)
        dis.readFully(msgLenBytes)
        val msgLenBuffer = ByteBuffer.wrap(msgLenBytes)
        msgLenBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val msgLen = msgLenBuffer.getInt()
        val result = ByteArray(msgLen)
        dis.readFully(result)
        return EncryptionUtils.decryptDataWithAes256(result, aes256Key, iv)
    }
}
