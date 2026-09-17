package com.estokio.service.tenant;

import com.estokio.domain.tenant.Plan;
import com.estokio.domain.tenant.Tenant;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.ApiException;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.repository.tenant.PlanoRepository;
import com.estokio.repository.tenant.TenantRepository;
import com.estokio.security.PasswordEncoder;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.postgresql.util.PSQLException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fluxo do Super Admin (Fase 1, item 1 -- ver Fase 1 - MVP): criar loja + admin inicial.
 * Roda inteiramente via {@code appPlatformJdbi} (BYPASSRLS), nunca chama
 * {@link com.estokio.security.TenantContext#aplicarNaTransacao} -- o Super Admin nao
 * pertence a um tenant, e nenhuma tabela tocada aqui exige {@code SET LOCAL app.tenant_id}
 * (tenant/tenant_config/usuario, ver Multi-Tenancy e RLS).
 */
public final class TenantService {

    /** Codigo do postgres para "unique_violation" (23505) -- rede de seguranca contra corrida entre a checagem previa e o INSERT. */
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    private final Jdbi appPlatformJdbi;
    private final TenantRepository tenantRepository;
    private final PlanoRepository planoRepository;
    private final UsuarioRepository usuarioRepository;

    public TenantService(Jdbi appPlatformJdbi, TenantRepository tenantRepository, PlanoRepository planoRepository,
                          UsuarioRepository usuarioRepository) {
        this.appPlatformJdbi = appPlatformJdbi;
        this.tenantRepository = tenantRepository;
        this.planoRepository = planoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<Plan> listarPlanosAtivos() {
        return planoRepository.listarAtivos();
    }

    public List<Tenant> listarLojas() {
        return tenantRepository.listarTodos();
    }

    /**
     * Cria tenant + tenant_config padrao + usuario admin inicial numa unica transacao:
     * se qualquer passo falhar (plano invalido, slug ou email duplicado), nada e gravado.
     */
    public Tenant criarLoja(String nomeLoja, String slug, UUID planoId, String nomeAdmin, String emailAdmin,
                             String senhaAdmin) {
        try {
            return appPlatformJdbi.inTransaction(handle -> {
                Plan plano = planoRepository.buscarPorId(handle, planoId)
                        .filter(Plan::ativo)
                        .orElseThrow(() -> ApiException.requisicaoInvalida(
                                "PLANO_INVALIDO", "Plano informado nao existe ou nao esta ativo."));

                if (tenantRepository.existeSlug(handle, slug)) {
                    throw ApiException.conflito("SLUG_JA_USADO", "Ja existe uma loja com este slug.");
                }
                if (usuarioRepository.buscarPorEmail(handle, emailAdmin).isPresent()) {
                    throw ApiException.conflito("EMAIL_JA_CADASTRADO", "Ja existe uma conta com este email.");
                }

                Tenant tenant = tenantRepository.inserir(handle, nomeLoja, slug, plano.id());

                usuarioRepository.inserir(handle, new Usuario(
                        UUID.randomUUID(), tenant.id(), nomeAdmin, emailAdmin,
                        PasswordEncoder.hash(senhaAdmin), Papel.ADMIN_LOJA, true, Instant.now()));

                return tenant;
            });
        } catch (UnableToExecuteStatementException e) {
            throw traduzirViolacaoDeUnicidade(e);
        }
    }

    /**
     * A checagem previa de slug/email dentro da transacao fecha a maior parte dos casos, mas
     * ainda existe uma janela de corrida entre o SELECT e o INSERT (duas requisicoes com o
     * mesmo slug/email ao mesmo tempo). Se a constraint do banco pegar isso, traduzimos para
     * o mesmo contrato de erro em vez de deixar a mensagem crua do driver JDBC vazar.
     */
    private ApiException traduzirViolacaoDeUnicidade(UnableToExecuteStatementException e) {
        if (e.getCause() instanceof PSQLException causa
                && SQLSTATE_UNIQUE_VIOLATION.equals(causa.getSQLState())) {
            String restricao = causa.getServerErrorMessage() == null ? "" : String.valueOf(causa.getServerErrorMessage().getConstraint());
            if (restricao.contains("slug")) {
                return ApiException.conflito("SLUG_JA_USADO", "Ja existe uma loja com este slug.");
            }
            if (restricao.contains("email")) {
                return ApiException.conflito("EMAIL_JA_CADASTRADO", "Ja existe uma conta com este email.");
            }
            return ApiException.conflito("DADO_DUPLICADO", "Ja existe um registro com estes dados.");
        }
        throw new IllegalStateException("Falha inesperada ao criar loja.", e);
    }
}
