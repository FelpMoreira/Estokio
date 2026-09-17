package com.estokio.service.inventory;

import com.estokio.domain.inventory.EstoqueSaldoResumo;
import com.estokio.domain.inventory.TipoMovimento;
import com.estokio.exception.ApiException;
import com.estokio.repository.inventory.EstoqueRepository;
import com.estokio.security.TenantContext;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.UUID;

/**
 * Movimentacao de estoque da Fase 1, item 2 -- ver [[Fase 1 - MVP]] e [[Ledger de Estoque]].
 * Toda escrita segue a regra de ouro do dominio: insere um {@code estoque_movimento}
 * e atualiza {@code estoque_saldo} na MESMA transacao, com lock pessimista
 * ({@code SELECT ... FOR UPDATE}) na linha de saldo antes do update (ver
 * [[Reserva e Concorrencia]]) -- mesma disciplina de qualquer outra escrita de
 * estoque futura, mesmo afetando um unico SKU aqui.
 */
public final class EstoqueService {

    private final Jdbi appUserJdbi;
    private final EstoqueRepository estoqueRepository;

    public EstoqueService(Jdbi appUserJdbi, EstoqueRepository estoqueRepository) {
        this.appUserJdbi = appUserJdbi;
        this.estoqueRepository = estoqueRepository;
    }

    public EstoqueSaldoResumo registrarEntrada(UUID variacaoId, int quantidade, String motivo) {
        if (quantidade <= 0) {
            throw ApiException.requisicaoInvalida("QUANTIDADE_INVALIDA", "Quantidade deve ser maior que zero.");
        }

        UUID tenantId = TenantContext.tenantIdObrigatorio();
        UUID usuarioId = TenantContext.usuarioId();

        return appUserJdbi.inTransaction(handle -> {
            TenantContext.aplicarNaTransacao(handle);

            // Lock pessimista antes de qualquer leitura/escrita de saldo -- ver classe-doc.
            // RLS ja restringe a linha ao tenant atual, entao "nao encontrado" cobre tanto
            // "SKU nao existe" quanto "SKU e de outro tenant" sem distinguir os dois casos
            // (o mesmo contrato de erro da tarefa).
            estoqueRepository.buscarParaAtualizar(handle, tenantId, variacaoId)
                    .orElseThrow(() -> ApiException.naoEncontrado(
                            "VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));

            estoqueRepository.registrarMovimento(
                    handle, tenantId, variacaoId, TipoMovimento.ENTRADA, quantidade, 0, motivo, usuarioId);
            estoqueRepository.atualizarSaldoFisico(handle, variacaoId, quantidade);

            return estoqueRepository.buscarResumoPorVariacao(handle, tenantId, variacaoId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Saldo desapareceu apos a entrada de estoque para " + variacaoId));
        });
    }

    public List<EstoqueSaldoResumo> listarSaldos() {
        UUID tenantId = TenantContext.tenantIdObrigatorio();
        return appUserJdbi.inTransaction(handle -> {
            TenantContext.aplicarNaTransacao(handle);
            return estoqueRepository.listarResumo(handle, tenantId);
        });
    }
}
