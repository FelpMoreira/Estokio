import { apiFetch } from './client';
import type { EntradaEstoqueRequest, EstoqueSaldoResumo } from './types';

/** GET /api/loja/estoque -- saldos do tenant atual (fisico, reservado, disponivel calculado). */
export function listarSaldos(): Promise<EstoqueSaldoResumo[]> {
  return apiFetch<EstoqueSaldoResumo[]>('/api/loja/estoque');
}

/**
 * POST /api/loja/estoque/entrada -- unico jeito de fazer estoque físico subir (regra de
 * ouro do dominio: mesmo a entrada inicial de um SKU novo passa por aqui, nunca por um
 * campo de "quantidade" editável).
 */
export function registrarEntrada(corpo: EntradaEstoqueRequest): Promise<EstoqueSaldoResumo> {
  return apiFetch<EstoqueSaldoResumo>('/api/loja/estoque/entrada', {
    method: 'POST',
    body: JSON.stringify(corpo),
  });
}
