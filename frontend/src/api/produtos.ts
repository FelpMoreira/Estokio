import { apiFetch } from './client';
import type { AtualizarVariacaoRequest, CriarProdutoRequest, CriarVariacaoRequest, Produto, ProdutoComVariacoes, Variacao } from './types';

/** GET /api/loja/produtos -- produtos do tenant atual, com suas variacoes/SKUs embutidas. */
export function listarProdutos(): Promise<ProdutoComVariacoes[]> {
  return apiFetch<ProdutoComVariacoes[]>('/api/loja/produtos');
}

/** POST /api/loja/produtos -- produto nasce sem variacao/SKU (isso e um passo separado). */
export function criarProduto(corpo: CriarProdutoRequest): Promise<Produto> {
  return apiFetch<Produto>('/api/loja/produtos', {
    method: 'POST',
    body: JSON.stringify(corpo),
  });
}

/** POST /api/loja/produtos/{id}/variacoes -- cria o SKU com saldo zerado (ver estoque.ts). */
export function criarVariacao(produtoId: string, corpo: CriarVariacaoRequest): Promise<Variacao> {
  return apiFetch<Variacao>(`/api/loja/produtos/${produtoId}/variacoes`, {
    method: 'POST',
    body: JSON.stringify(corpo),
  });
}

/** PATCH /api/loja/variacoes/{id} -- so campos regulares (preco, ponto de reposicao, ativo). */
export function atualizarVariacao(variacaoId: string, corpo: AtualizarVariacaoRequest): Promise<Variacao> {
  return apiFetch<Variacao>(`/api/loja/variacoes/${variacaoId}`, {
    method: 'PATCH',
    body: JSON.stringify(corpo),
  });
}
