package me.acuion.lockall_android.storages

class PasswordsStorage {
    private val map = HashMap<String, String>()

    fun put(resourceid : String, password : String) {
        map[resourceid] = password
    }

    fun getPass(resourceid : String) : String? {
        return map[resourceid]
    }
}