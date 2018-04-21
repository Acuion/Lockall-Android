package me.acuion.lockall_android.storages

class OtpDataStorage {
    private val map = HashMap<Pair<String, String>, String>()

    fun getIssuerProfileKeys() : Array<Pair<String, String>>
    {
        return map.keys.toTypedArray()
    }

    fun put(issuer : String, profile : String, secret : String) {
        val ipKey = Pair(issuer, profile)
        map[ipKey] = secret
    }

    fun getSecretFrom(ipKey : Pair<String, String>) : String {
        return map[ipKey]!!
    }
}
