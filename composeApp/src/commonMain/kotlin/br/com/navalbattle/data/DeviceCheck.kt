package br.com.navalbattle.data

/**
 * Aparelho dos testes automáticos do Google (Firebase Test Lab, usado na revisão de
 * cada envio à Play, #23). Lido do próprio aparelho: no Android, a configuração de
 * sistema `firebase.test.lab`; no iPhone é sempre falso (o `.ipa` não passa por
 * revisão automática). O app só relata ao servidor (report_device_check); quem marca
 * a conta como de teste e a tira do ranking e da partida rápida é o servidor.
 */
expect fun isTestLabDevice(): Boolean

/** Valor da configuração `firebase.test.lab`: só "true" (sem espaços) conta. */
fun isTestLabFlag(value: String?): Boolean = value?.trim() == "true"

/**
 * Chave do "já relatado" guardada no aparelho: o relato vai uma vez por conta e
 * versão — conta nova ou versão nova relatam de novo.
 */
fun deviceCheckKey(userId: String, appVersion: String): String = "$userId|$appVersion"
