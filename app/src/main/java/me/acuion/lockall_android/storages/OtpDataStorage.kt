package me.acuion.lockall_android.storages

class OtpDataStorage {
    private val map = HashMap<String, String>()

    fun getIssuerProfileKeys() : Array<String>
    {
        return map.keys.toTypedArray()
    }

    fun put(issuer : String, profile : String, secret : String) {
        val ipKey = "$profile ($issuer)"
        map[ipKey] = secret
    }

    fun getSecretFrom(keyStr : String) : String {
        return map[keyStr]!!
    }
}
