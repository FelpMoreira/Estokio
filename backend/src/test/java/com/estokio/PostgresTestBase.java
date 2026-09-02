package com.estokio;

import com.estokio.domain.user.RefreshToken;
import com.estokio.domain.user.Usuario;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;

/**
 * Base compartilhada de todos os testes de integracao contra Postgres real (via
 * Testcontainers + podman rootless, ver comentario no {@code maven-surefire-plugin}
 * do {@code pom.xml} sobre DOCKER_HOST/ryuk). Um unico container Postgres e uma
 * unica execucao das migrations Flyway sao reaproveitados por toda a suite (campo
 * {@code static}, inicializado uma vez no carregamento desta classe) -- subir um
 * container por classe de teste deixaria a suite lenta demais.
 *
 * <p><b>Escolha de abordagem para os 3 papeis de banco (migrator/app_user/app_platform):</b>
 * em vez de reimplementar o script {@code infra/postgres/init/01-create-roles.sh} como
 * texto duplicado nos testes (que ficaria dessincronizado do script real usado pelo
 * docker-compose), o teste copia o proprio arquivo do repositorio para
 * {@code /docker-entrypoint-initdb.d/} do container via {@code withCopyFileToContainer}
 * -- a imagem oficial {@code postgres:16} executa qualquer script ali na primeira
 * inicializacao, exatamente como o compose faz. As senhas dos 3 papeis sao passadas
 * como env vars do container (o script exige as 3 via {@code set -u}).</p>
 *
 * <p>As migrations em si rodam via Flyway apontando direto para o container com o
 * papel {@code migrator}, sem depender de {@link com.estokio.config.DatabaseConfig}
 * (que exige variaveis de ambiente do processo real, ex: ESTOKIO_DB_URL) -- os testes
 * preferem instanciar Jdbi/Flyway diretamente com a URL efemera do Testcontainers.</p>
 */
public abstract class PostgresTestBase {

    protected static final String MIGRATOR_USER = "migrator";
    protected static final String APP_USER_USER = "app_user";
    protected static final String APP_PLATFORM_USER = "app_platform";

    private static final String MIGRATOR_PASSWORD = "migrator_test_pw";
    private static final String APP_USER_PASSWORD = "app_user_test_pw";
    private static final String APP_PLATFORM_PASSWORD = "app_platform_test_pw";

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withDatabaseName("estokio_test")
                .withUsername("postgres")
                .withPassword("postgres_test_pw")
                .withEnv("ESTOKIO_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
                .withEnv("ESTOKIO_APP_USER_PASSWORD", APP_USER_PASSWORD)
                .withEnv("ESTOKIO_APP_PLATFORM_PASSWORD", APP_PLATFORM_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(caminhoScriptDeRoles()),
                        "/docker-entrypoint-initdb.d/01-create-roles.sh");
        POSTGRES.start();
        rodarMigrations();
    }

    /**
     * {@code infra/postgres/init/01-create-roles.sh} vive na raiz do repo, um nivel
     * acima de {@code backend/} (o working directory do Maven). Resolver o caminho a
     * partir do arquivo real evita duplicar o script como texto no teste.
     */
    private static String caminhoScriptDeRoles() {
        Path caminho = Path.of("").toAbsolutePath().resolve("../infra/postgres/init/01-create-roles.sh").normalize();
        if (!Files.exists(caminho)) {
            throw new IllegalStateException(
                    "Script de criacao de roles nao encontrado em " + caminho
                            + ". Os testes esperam rodar com working directory = backend/ (raiz do modulo Maven).");
        }
        return caminho.toString();
    }

    private static void rodarMigrations() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    protected static Jdbi migratorJdbi() {
        return criarJdbi(MIGRATOR_USER, MIGRATOR_PASSWORD);
    }

    /** Sujeito a RLS; sem UPDATE/DELETE em estoque_movimento e pedido_status_historico. */
    protected static Jdbi appUserJdbi() {
        return criarJdbi(APP_USER_USER, APP_USER_PASSWORD);
    }

    /** BYPASSRLS. */
    protected static Jdbi appPlatformJdbi() {
        return criarJdbi(APP_PLATFORM_USER, APP_PLATFORM_PASSWORD);
    }

    private static Jdbi criarJdbi(String usuario, String senha) {
        Jdbi jdbi = Jdbi.create(POSTGRES.getJdbcUrl(), usuario, senha);
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.registerRowMapper(ConstructorMapper.factory(Usuario.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(RefreshToken.class));
        return jdbi;
    }

    /**
     * Equivalente de teste a {@code TenantContext#aplicarNaTransacao}: roda a acao
     * dentro de uma unica transacao com {@code app.tenant_id} setado via
     * {@code set_config(..., true)} (escopo de transacao), exatamente o mecanismo que
     * a aplicacao real usa antes de tocar qualquer tabela com RLS.
     */
    protected static <T> T comoTenant(Jdbi jdbi, UUID tenantId, Function<Handle, T> acao) {
        return jdbi.inTransaction(handle -> {
            handle.execute("SELECT set_config('app.tenant_id', ?, true)", tenantId.toString());
            return acao.apply(handle);
        });
    }

    /** Roda a acao numa transacao explicitamente SEM {@code app.tenant_id} setado (default-deny). */
    protected static <T> T semTenant(Jdbi jdbi, Function<Handle, T> acao) {
        return jdbi.inTransaction(acao::apply);
    }
}
