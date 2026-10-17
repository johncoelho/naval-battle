package br.com.navalbattle.data

/**
 * Armazenamento chave-valor da carreira do comandante. É o mínimo que a versão 2
 * precisa: o perfil e o estaleiro têm que sobreviver ao fechar o aplicativo.
 */
expect class Prefs() {
    fun getString(key: String, default: String): String
    fun getInt(key: String, default: Int): Int
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    /** Grava o lote inteiro numa só escrita (no Android, atômica no arquivo; ver [PrefsBatch]). */
    fun putBatch(batch: PrefsBatch)
    fun clear()
}

/**
 * Escritas que precisam andar juntas (ex.: saldo e conjunto de compras entregues). Quem
 * chama monta o lote e manda para [Prefs.putBatch]; a última escrita de cada chave vale.
 */
class PrefsBatch {
    val strings = linkedMapOf<String, String>()
    val ints = linkedMapOf<String, Int>()

    fun putString(key: String, value: String) {
        strings[key] = value
    }

    fun putInt(key: String, value: Int) {
        ints[key] = value
    }
}
