import { apiFetch } from './client';
import type { CriarLojaRequest, Plano, Tenant } from './types';

/** GET /api/plataforma/planos -- planos ativos, usados no formulário de criação de loja. */
export function listarPlanosAtivos(): Promise<Plano[]> {
  return apiFetch<Plano[]>('/api/plataforma/planos');
}

/** GET /api/plataforma/lojas -- todos os tenants da plataforma. */
export function listarLojas(): Promise<Tenant[]> {
  return apiFetch<Tenant[]>('/api/plataforma/lojas');
}

/** POST /api/plataforma/lojas -- cria tenant + tenant_config padrão + admin inicial numa única transação. */
export function criarLoja(corpo: CriarLojaRequest): Promise<Tenant> {
  return apiFetch<Tenant>('/api/plataforma/lojas', {
    method: 'POST',
    body: JSON.stringify(corpo),
  });
}
