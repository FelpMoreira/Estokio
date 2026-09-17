/** Papéis de usuário -- espelha o enum Papel do backend (com.estokio.domain.user.Papel). */
export type Papel = 'SUPER_ADMIN' | 'ADMIN_LOJA' | 'OPERADOR' | 'CLIENTE';

/** Corpo de resposta de POST /api/auth/login e POST /api/auth/refresh. */
export interface ParDeTokens {
  access_token: string;
  refresh_token: string;
}

/** Claims decodificados do access token (JwtService.emitirAccessToken). */
export interface ClaimsAccessToken {
  sub: string;
  papel: Papel;
  tenant_id?: string;
  iat: number;
  exp: number;
}

/** Contrato de erro padrão de qualquer resposta 4xx/5xx (ver ErroPadrao.java). */
export interface ErroPadrao {
  codigo: string;
  mensagem: string;
  detalhes?: unknown[];
}

/** Ciclo de vida de um tenant -- espelha com.estokio.domain.tenant.StatusTenant. */
export type StatusTenant = 'PENDENTE' | 'ATIVA' | 'SUSPENSA' | 'CANCELADA';

/** Plano da plataforma (com.estokio.domain.tenant.Plan) -- corpo de GET /api/plataforma/planos. */
export interface Plano {
  id: string;
  nome: string;
  max_produtos: number;
  max_pedidos_mes: number;
  max_usuarios: number;
  preco_centavos: number;
  ativo: boolean;
  criado_em: string;
}

/** Tenant/loja (com.estokio.domain.tenant.Tenant) -- corpo de GET/POST /api/plataforma/lojas. */
export interface Tenant {
  id: string;
  nome: string;
  slug: string;
  status: StatusTenant;
  plano_id: string;
  criado_em: string;
}

/** Corpo de POST /api/plataforma/lojas (PlataformaController.CriarLojaRequest). */
export interface CriarLojaRequest {
  loja: {
    nome: string;
    slug: string;
    plano_id: string;
  };
  admin: {
    nome: string;
    email: string;
    senha: string;
  };
}
