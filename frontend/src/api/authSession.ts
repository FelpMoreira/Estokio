/**
 * Fonte única do access token em memória. Módulo comum entre o AuthContext
 * (que precisa re-renderizar a árvore React quando o token muda) e o
 * apiFetch (que precisa ler/escrever o token fora de qualquer componente,
 * por exemplo durante o retry automático após um 401).
 */
type Ouvinte = (accessToken: string | null) => void;

let accessTokenAtual: string | null = null;
const ouvintes = new Set<Ouvinte>();

export function obterAccessToken(): string | null {
  return accessTokenAtual;
}

export function definirAccessToken(token: string | null): void {
  accessTokenAtual = token;
  ouvintes.forEach((ouvinte) => ouvinte(token));
}

export function inscreverAccessToken(ouvinte: Ouvinte): () => void {
  ouvintes.add(ouvinte);
  return () => {
    ouvintes.delete(ouvinte);
  };
}
