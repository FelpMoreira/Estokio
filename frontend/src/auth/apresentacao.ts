import type { Papel } from '../api/types';

/** Rótulo legível do papel, no mesmo estilo caixa-alta dos mockups (ADMIN DA LOJA, SUPER ADMIN...). */
export function rotuloPapel(papel: Papel): string {
  switch (papel) {
    case 'SUPER_ADMIN':
      return 'SUPER ADMIN';
    case 'ADMIN_LOJA':
      return 'ADMIN DA LOJA';
    case 'OPERADOR':
      return 'OPERADOR';
    case 'CLIENTE':
      return 'CLIENTE';
  }
}

/** Iniciais derivadas do email (não temos nome no JWT) pro avatar da sidebar. */
export function iniciaisEmail(email: string): string {
  const local = email.split('@')[0] ?? '';
  const partes = local.split(/[._-]+/).filter(Boolean);
  if (partes.length >= 2) {
    return (partes[0][0] + partes[1][0]).toUpperCase();
  }
  return (local.slice(0, 2) || '??').toUpperCase();
}
