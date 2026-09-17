-- POST /api/plataforma/lojas (Fase 1) cria tenant + tenant_config padrao + admin inicial
-- numa unica transacao via app_platform (BYPASSRLS). V17 so deu SELECT em usuario e
-- tenant_config a esse papel; falta INSERT para essas duas tabelas. Nao concede UPDATE
-- nem DELETE -- essa rota so cria, nunca altera ou apaga.
GRANT INSERT ON usuario, tenant_config TO app_platform;
