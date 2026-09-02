import type { Papel } from '../api/types';

/**
 * Rota "de casa" para cada papel -- usada tanto no redirecionamento pós-login
 * quanto no bounce de RotaProtegida quando o papel do token não bate com o
 * exigido pela rota. CLIENTE não tem painel próprio nesta fase (só chega na
 * Fase 1), então volta para a vitrine pública da plataforma.
 */
export function rotaDeCasaParaPapel(papel: Papel): string {
  switch (papel) {
    case 'SUPER_ADMIN':
      return '/plataforma';
    case 'ADMIN_LOJA':
    case 'OPERADOR':
      return '/loja';
    case 'CLIENTE':
      return '/';
  }
}
