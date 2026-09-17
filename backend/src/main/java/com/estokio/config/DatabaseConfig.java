package com.estokio.config;

import com.estokio.domain.tenant.Plan;
import com.estokio.domain.tenant.Tenant;
import com.estokio.domain.user.RefreshToken;
import com.estokio.domain.user.Usuario;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.postgres.PostgresPlugin;

import javax.sql.DataSource;

/**
 * Monta as conexoes com os 3 papeis de banco (Decisao de kickoff #10):
 * {@code migrator} roda as migrations Flyway uma unica vez na inicializacao;
 * {@code app_user} atende as rotas normais (sujeito a RLS, sem UPDATE/DELETE no ledger);
 * {@code app_platform} atende so {@code /api/plataforma/**} (BYPASSRLS).
 * Cada papel tem usuario/senha proprios (ver .env / infra/postgres/init), mas
 * compartilham host/porta/banco (ESTOKIO_DB_URL).
 */
public final class DatabaseConfig {

    private DatabaseConfig() {
    }

    /** Roda as migrations Flyway com o papel `migrator`, dono do schema. Chamar uma vez, no boot. */
    public static void rodarMigrations() {
        DataSource dataSource = construirDataSource(
                requiredEnv("ESTOKIO_MIGRATOR_USER"), requiredEnv("ESTOKIO_MIGRATOR_PASSWORD"), 2);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        ((HikariDataSource) dataSource).close();
    }

    /** Jdbi usado pelas rotas normais (fora de /api/plataforma/**), sujeito a RLS. */
    public static Jdbi appUserJdbi() {
        return construirJdbi(requiredEnv("ESTOKIO_APP_USER_USER"), requiredEnv("ESTOKIO_APP_USER_PASSWORD"), 10);
    }

    /** Jdbi usado exclusivamente pelas rotas /api/plataforma/**, BYPASSRLS. */
    public static Jdbi appPlatformJdbi() {
        return construirJdbi(requiredEnv("ESTOKIO_APP_PLATFORM_USER"), requiredEnv("ESTOKIO_APP_PLATFORM_PASSWORD"), 4);
    }

    private static Jdbi construirJdbi(String usuario, String senha, int poolMaximo) {
        Jdbi jdbi = Jdbi.create(construirDataSource(usuario, senha, poolMaximo));
        jdbi.installPlugin(new PostgresPlugin());
        registrarMappers(jdbi);
        return jdbi;
    }

    private static void registrarMappers(Jdbi jdbi) {
        jdbi.registerRowMapper(ConstructorMapper.factory(Usuario.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(RefreshToken.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(Tenant.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(Plan.class));
    }

    private static DataSource construirDataSource(String usuario, String senha, int poolMaximo) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(requiredEnv("ESTOKIO_DB_URL"));
        hikari.setUsername(usuario);
        hikari.setPassword(senha);
        hikari.setMaximumPoolSize(poolMaximo);
        hikari.setPoolName("estokio-" + usuario);
        return new HikariDataSource(hikari);
    }

    private static String requiredEnv(String chave) {
        String valor = System.getenv(chave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente obrigatoria ausente: " + chave);
        }
        return valor;
    }
}
