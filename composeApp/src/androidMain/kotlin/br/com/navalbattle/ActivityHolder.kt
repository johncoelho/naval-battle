package br.com.navalbattle

import android.app.Activity

/**
 * A Activity em primeiro plano, para o Credential Manager do login com Google —
 * ele precisa de uma Activity de verdade para desenhar a caixa de seleção de conta,
 * o Context de aplicação (ver [br.com.navalbattle.audio.AudioContextHolder]) não basta.
 */
object ActivityHolder {
    var current: Activity? = null
}
