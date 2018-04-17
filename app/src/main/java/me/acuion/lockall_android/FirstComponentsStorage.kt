package me.acuion.lockall_android

class FirstComponentsStorage {
    private val map = HashMap<String, ByteArray>()

    fun put(name : String, firstComponent : ByteArray)
    {
        map[name] = firstComponent
    }

    val firstComponents : ArrayList<ByteArray>
    get() {
        val list = ArrayList<ByteArray>()
        map.forEach {
            list.add(it.component2())
        }
        return list
    }
}