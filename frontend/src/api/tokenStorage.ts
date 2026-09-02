/**
 * Persistência do refresh token em localStorage (simplificação da Fase 0 --
 * o ideal seria um cookie httpOnly, mas isso exige mudanças no backend
 * (Set-Cookie, CORS com credentials) fora do escopo desta fase; ver nota em
 * estokio-vault/03-Fases/Notas de Implementacao - Fase 0 Frontend.md).
 *
 * O access token nunca é persistido aqui -- ele só existe em memória
 * (ver src/api/authSession.ts), é recriado a cada refresh/login.
 */
const CHAVE_REFRESH_TOKEN = 'estokio.refresh_token';
// O access token (JwtService.emitirAccessToken) não carrega nome nem email do
// usuário, só `sub` (id), `papel` e `tenant_id` -- então pra mostrar algo mais
// legível que um UUID no rodapé da sidebar, guardamos o email real usado no
// login (não é segredo, só cache de apresentação) e o restauramos junto do
// refresh token entre recarregamentos de página.
const CHAVE_EMAIL = 'estokio.email';

export function obterRefreshTokenArmazenado(): string | null {
  return localStorage.getItem(CHAVE_REFRESH_TOKEN);
}

export function definirRefreshTokenArmazenado(token: string | null): void {
  if (token) {
    localStorage.setItem(CHAVE_REFRESH_TOKEN, token);
  } else {
    localStorage.removeItem(CHAVE_REFRESH_TOKEN);
  }
}

export function obterEmailArmazenado(): string | null {
  return localStorage.getItem(CHAVE_EMAIL);
}

export function definirEmailArmazenado(email: string | null): void {
  if (email) {
    localStorage.setItem(CHAVE_EMAIL, email);
  } else {
    localStorage.removeItem(CHAVE_EMAIL);
  }
}
