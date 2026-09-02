import { jwtDecode } from 'jwt-decode';
import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { apiFetch, encerrarSessao, tentarRenovarSessao } from '../api/client';
import { definirAccessToken, inscreverAccessToken, obterAccessToken } from '../api/authSession';
import {
  definirEmailArmazenado,
  definirRefreshTokenArmazenado,
  obterEmailArmazenado,
  obterRefreshTokenArmazenado,
} from '../api/tokenStorage';
import type { ClaimsAccessToken, ParDeTokens } from '../api/types';

export interface AuthContextValor {
  /** Claims do usuário logado, ou null se não houver sessão válida. */
  claims: ClaimsAccessToken | null;
  /**
   * Email usado no login. Não vem do JWT (o access token só carrega sub/papel/
   * tenant_id) -- é cacheado localmente no momento do login só pra exibição
   * (ver nota em src/api/tokenStorage.ts).
   */
  email: string | null;
  /** true enquanto a sessão está sendo restaurada a partir do refresh token salvo. */
  restaurandoSessao: boolean;
  login(email: string, senha: string): Promise<ClaimsAccessToken>;
  logout(): void;
}

export const AuthContext = createContext<AuthContextValor | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(obterAccessToken());
  const [email, setEmail] = useState<string | null>(obterEmailArmazenado());
  const [restaurandoSessao, setRestaurandoSessao] = useState(true);

  useEffect(() => inscreverAccessToken(setAccessToken), []);

  // Ao carregar a aplicação (ex: F5), não há access token em memória, mas pode
  // haver um refresh token salvo de uma sessão anterior -- tenta trocá-lo por
  // um novo par antes de decidir se o usuário está autenticado.
  useEffect(() => {
    let cancelado = false;

    async function restaurarSessao() {
      if (!obterRefreshTokenArmazenado()) {
        setRestaurandoSessao(false);
        return;
      }

      // tentarRenovarSessao é single-flight (ver src/api/client.ts): se este
      // efeito rodar duas vezes em sequência (StrictMode) ou se uma outra
      // chamada já estiver renovando por causa de um 401 concorrente, as
      // duas chamadas colapsam numa só requisição real ao backend.
      const renovou = await tentarRenovarSessao();
      if (!renovou) {
        encerrarSessao();
        if (!cancelado) {
          setEmail(null);
        }
      }
      if (!cancelado) {
        setRestaurandoSessao(false);
      }
    }

    restaurarSessao();
    return () => {
      cancelado = true;
    };
  }, []);

  const claims = useMemo<ClaimsAccessToken | null>(() => {
    if (!accessToken) {
      return null;
    }
    try {
      return jwtDecode<ClaimsAccessToken>(accessToken);
    } catch {
      return null;
    }
  }, [accessToken]);

  const login = useCallback(async (emailDigitado: string, senha: string) => {
    const par = await apiFetch<ParDeTokens>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: emailDigitado, senha }),
      semAutenticacao: true,
    });
    definirAccessToken(par.access_token);
    definirRefreshTokenArmazenado(par.refresh_token);
    definirEmailArmazenado(emailDigitado);
    setEmail(emailDigitado);
    return jwtDecode<ClaimsAccessToken>(par.access_token);
  }, []);

  const logout = useCallback(() => {
    encerrarSessao();
    setEmail(null);
  }, []);

  const valor = useMemo<AuthContextValor>(
    () => ({ claims, email, restaurandoSessao, login, logout }),
    [claims, email, restaurandoSessao, login, logout],
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}
