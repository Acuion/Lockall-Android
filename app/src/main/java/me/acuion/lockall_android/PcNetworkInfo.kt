package me.acuion.lockall_android

import java.net.InetAddress

class PcNetworkInfo {
    var commMode = CommMode.Wifi

    var hostTcpAddress : InetAddress? = null
    var hostTcpPort : Int = 0

    var hostBtMacAddress : String? = null
    var hostBtUuid : String? = null

    enum class CommMode {
        Wifi,
        Bluetooth
    }
}