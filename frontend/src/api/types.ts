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

/** Produto (com.estokio.domain.catalog.Produto) -- corpo de POST /api/loja/produtos. */
export interface Produto {
  id: string;
  tenant_id: string;
  categoria_id: string | null;
  nome: string;
  descricao: string | null;
  ativo: boolean;
  criado_em: string;
}

/**
 * Variacao/SKU (com.estokio.domain.catalog.Variacao) -- corpo de
 * POST /api/loja/produtos/{id}/variacoes, PATCH /api/loja/variacoes/{id} e o item
 * de `variacoes` embutido em {@link ProdutoComVariacoes}. Nasce sempre com saldo
 * zerado -- quantidade so entra via POST /api/loja/estoque/entrada.
 */
export interface Variacao {
  id: string;
  tenant_id: string;
  produto_id: string;
  sku: string;
  atributos: Record<string, string>;
  preco_centavos: number;
  ponto_reposicao: number;
  ativo: boolean;
}

/** Projecao de leitura de GET /api/loja/produtos: produto + suas variacoes/SKUs. */
export interface ProdutoComVariacoes {
  id: string;
  nome: string;
  descricao: string | null;
  ativo: boolean;
  variacoes: Variacao[];
}

/**
 * Projecao de leitura de GET /api/loja/estoque e resposta de
 * POST /api/loja/estoque/entrada (com.estokio.domain.inventory.EstoqueSaldoResumo).
 */
export interface EstoqueSaldoResumo {
  variacao_id: string;
  sku: string;
  nome_produto: string;
  qtd_fisica: number;
  qtd_reservada: number;
  disponivel: number;
  ponto_reposicao: number;
}

/** Corpo de POST /api/loja/produtos. */
export interface CriarProdutoRequest {
  nome: string;
  descricao?: string;
}

/** Corpo de POST /api/loja/produtos/{id}/variacoes. */
export interface CriarVariacaoRequest {
  sku: string;
  atributos?: Record<string, string>;
  preco_centavos: number;
  ponto_reposicao?: number;
}

/** Corpo de PATCH /api/loja/variacoes/{id} -- nunca inclui estoque, so ledger muda isso. */
export interface AtualizarVariacaoRequest {
  preco_centavos?: number;
  ponto_reposicao?: number;
  ativo?: boolean;
}

/** Corpo de POST /api/loja/estoque/entrada. */
export interface EntradaEstoqueRequest {
  variacao_id: string;
  quantidade: number;
  motivo?: string;
}
