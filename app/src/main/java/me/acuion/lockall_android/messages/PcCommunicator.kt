package me.acuion.lockall_android.messages

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import me.acuion.lockall_android.NetworkMessage
import me.acuion.lockall_android.PcNetworkInfo
import me.acuion.lockall_android.crypto.EncryptionUtils
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import android.net.wifi.WifiManager


class PcCommunicator(val pcNetworkInfo: PcNetworkInfo) {
    private lateinit var outputStream: OutputStream
    private lateinit var inputStream: InputStream
    private var tcpSocket: Socket? = null
    private var btSocket: BluetoothSocket? = null

    fun connect(context: Context) {
        if (pcNetworkInfo.commMode == PcNetworkInfo.CommMode.Wifi) {
            // TCP
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifi.isWifiEnabled) {
                throw Exception("Wifi is not enabled!")
            }
            tcpSocket = Socket(pcNetworkInfo.hostTcpAddress, pcNetworkInfo.hostTcpPort)
            outputStream = tcpSocket!!.outputStream
            inputStream = tcpSocket!!.inputStream
        } else {
            // BT
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!bluetoothAdapter.isEnabled) {
                throw Exception("Bluetooth is not enabled!")
            }
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
            pairedDevices?.forEach { device ->
                val deviceName = device.name
                val deviceHardwareAddress = device.address // MAC address

                if (deviceHardwareAddress == pcNetworkInfo.hostBtMacAddress) {
                    val connUUID = UUID.fromString(pcNetworkInfo.hostBtUuid)
                    btSocket = device.createInsecureRfcommSocketToServiceRecord(connUUID)
                    btSocket!!.connect()
                    outputStream = btSocket!!.outputStream
                    inputStream = btSocket!!.inputStream
                }
            }
            // TODO: pairing
        }
    }

    fun send(message: NetworkMessage) {
        outputStream.write(message.readyMessage)
    }

    fun readEncrypted(aes256Key: ByteArray) : ByteArray {
        val dis = DataInputStream(inputStream)

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

    fun close() {
        btSocket?.close()
        tcpSocket?.close()
    }
}
