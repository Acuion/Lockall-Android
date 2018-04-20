package me.acuion.lockall_android.storages

class PasswordsStorage {
    private val map = HashMap<String, HashMap<String, String>>()

    fun put(resourceid : String, profile : String, password : String) {
        lateinit var resource : HashMap<String, String>
        if (map.containsKey(resourceid))
            resource = map[resourceid]!!
        else
            resource = HashMap()
        resource[profile] = password
        map[resourceid] = resource
    }

    fun getPass(resourceid : String, profile : String) : String? {
        if (!map.containsKey(resourceid))
            return null
        return map[resourceid]!![profile]
    }

    fun getProfilesForResource(resourceid: String) : Array<String>? {
        if (!map.containsKey(resourceid))
            return null
        return map[resourceid]!!.keys.toTypedArray()
    }
}
