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
    fun clear()
}
