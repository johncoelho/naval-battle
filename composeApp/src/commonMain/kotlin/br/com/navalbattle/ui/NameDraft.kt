package br.com.navalbattle.ui

/**
 * Rascunho de um campo de nome ao lado do nome salvo (#61). O campo de texto copia o nome só quando a tela
 * monta, mas o nome salvo muda com a tela aberta (login com Google no próprio Perfil, adoção da carreira da
 * nuvem, sair da conta). Se o jogador **não editou** (o rascunho, sem espaços nas pontas, ainda é o nome salvo
 * de antes), o campo acompanha o novo nome salvo; se editou, mantém o que ele está digitando.
 */
fun followSaved(draft: String, previousSaved: String, newSaved: String): String =
    if (draft.trim() == previousSaved.trim()) newSaved else draft
