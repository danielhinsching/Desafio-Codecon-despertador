package com.codecon.despertador.toothbrush

/**
 * Fim do desafio da escova. A ligação real (parar o som do alarme, limpar o desafio pendente e fechar)
 * é injetada por quem usa a tela (#48); nos testes entra um fake.
 */
interface ChallengeCompletion {
    fun onCompleted()
}
