import { definirAccessToken, obterAccessToken } from './authSession';
import { definirEmailArmazenado, definirRefreshTokenArmazenado, obterRefreshTokenArmazenado } from './tokenStorage';
import type { ErroPadrao, ParDeTokens } from './types';

const URL_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:7000';

/** Erro de negócio/validação vindo do backend, já normalizado para {codigo, mensagem, detalhes}. */
export class ApiError extends Error {
  readonly codigo: string;
  readonly detalhes: unknown[];
  readonly status: number;

  constructor(codigo: string, mensagem: string, detalhes: unknown[], status: number) {
    super(mensagem);
    this.name = 'ApiError';
    this.codigo = codigo;
    this.detalhes = detalhes;
    this.status = status;
  }
}

/** Encerra a sessão local: limpa o access token em memória e o refresh token persistido. */
export function encerrarSessao(): void {
  definirAccessToken(null);
  definirRefreshTokenArmazenado(null);
  definirEmailArmazenado(null);
}

// Garante que, mesmo com várias chamadas concorrentes (retry de 401 e/ou
// restauração de sessão no mount do AuthProvider -- que em StrictMode roda o
// efeito duas vezes), só um POST /api/auth/refresh seja disparado por vez.
// Sem isso, dois refreshes concorrentes com o mesmo token bruto colidem com
// a rotação atômica do backend (RefreshTokenRepository.consumirSeValido):
// o primeiro consome o token, o segundo recebe 401 e derruba a sessão que
// acabou de ser renovada.
let renovacaoEmAndamento: Promise<boolean> | null = null;

export async function tentarRenovarSessao(): Promise<boolean> {
  const refreshToken = obterRefreshTokenArmazenado();
  if (!refreshToken) {
    return false;
  }

  if (!renovacaoEmAndamento) {
    renovacaoEmAndamento = (async () => {
      try {
        const resposta = await fetch(`${URL_BASE}/api/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refresh_token: refreshToken }),
        });
        if (!resposta.ok) {
          return false;
        }
        const par = (await resposta.json()) as ParDeTokens;
        definirAccessToken(par.access_token);
        definirRefreshTokenArmazenado(par.refresh_token);
        return true;
      } catch {
        return false;
      } finally {
        renovacaoEmAndamento = null;
      }
    })();
  }

  return renovacaoEmAndamento;
}

interface OpcoesRequisicao extends RequestInit {
  /** Pula o header Authorization e o retry automático em 401 (usado por login/refresh). */
  semAutenticacao?: boolean;
}

/**
 * Wrapper de fetch com o contrato de erro do projeto e retry automático de
 * uma tentativa em 401 via refresh token (ver seção "Auth de verdade" da tarefa).
 */
export async function apiFetch<T>(caminho: string, opcoes: OpcoesRequisicao = {}): Promise<T> {
  const { semAutenticacao, ...opcoesFetch } = opcoes;

  const executar = (): Promise<Response> => {
    const headers = new Headers(opcoesFetch.headers);
    headers.set('Content-Type', 'application/json');
    const token = obterAccessToken();
    if (token && !semAutenticacao) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return fetch(`${URL_BASE}${caminho}`, { ...opcoesFetch, headers });
  };

  let resposta = await executar();

  if (resposta.status === 401 && !semAutenticacao) {
    const renovou = await tentarRenovarSessao();
    if (renovou) {
      resposta = await executar();
    } else {
      encerrarSessao();
    }
  }

  const corpoBruto = await resposta.text();
  const corpo = corpoBruto ? JSON.parse(corpoBruto) : null;

  if (!resposta.ok) {
    const erro = corpo as ErroPadrao | null;
    throw new ApiError(
      erro?.codigo ?? 'ERRO_DESCONHECIDO',
      erro?.mensagem ?? 'Ocorreu um erro inesperado. Tente novamente.',
      erro?.detalhes ?? [],
      resposta.status,
    );
  }

  return corpo as T;
}
