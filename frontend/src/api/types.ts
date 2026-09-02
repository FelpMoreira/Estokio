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
