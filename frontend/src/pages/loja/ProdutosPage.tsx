import { type FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { criarProduto, criarVariacao, listarProdutos } from '../../api/produtos';
import type { ProdutoComVariacoes } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { IconChevronDown } from '../../components/icons/IconChevronDown';
import { IconProdutos } from '../../components/icons/IconProdutos';

const CLASSE_INPUT =
  'border-0 border-b-[1.5px] border-line-strong bg-transparent px-0.5 py-2 font-body text-[15px] text-ink outline-none focus:border-accent-strong';

const FORMATADOR_PRECO = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

function formatarCentavos(centavos: number): string {
  return FORMATADOR_PRECO.format(centavos / 100);
}

/** "tamanho:M, cor:Azul" -> {tamanho: "M", cor: "Azul"}; entradas em branco são ignoradas. */
function parsearAtributos(texto: string): Record<string, string> {
  const atributos: Record<string, string> = {};
  for (const par of texto.split(',')) {
    const [chave, ...resto] = par.split(':');
    const valor = resto.join(':').trim();
    if (chave && chave.trim() && valor) {
      atributos[chave.trim()] = valor;
    }
  }
  return atributos;
}

/** Converte "89,90" ou "89.90" para 8990 (centavos); retorna null se não for um número válido. */
function parsearPrecoParaCentavos(texto: string): number | null {
  const normalizado = texto.trim().replace(',', '.');
  if (normalizado === '' || Number.isNaN(Number(normalizado))) {
    return null;
  }
  return Math.round(Number(normalizado) * 100);
}

interface FormVariacao {
  sku: string;
  atributosTexto: string;
  precoTexto: string;
  pontoReposicao: string;
  erro: string | null;
  enviando: boolean;
}

const FORM_VARIACAO_VAZIO: FormVariacao = {
  sku: '',
  atributosTexto: '',
  precoTexto: '',
  pontoReposicao: '0',
  erro: null,
  enviando: false,
};

/**
 * Admin/Operador cadastra produto + variação/SKU (Fase 1, item 2 -- ver [[Fase 1 - MVP]]).
 * Um produto nasce sem SKU nenhum; cada variação é um passo separado, com preço e ponto
 * de reposição próprios. Estoque não aparece aqui de propósito -- toda variação nasce
 * com saldo zerado, e a entrada de estoque vive em {@code /loja/estoque} (regra de ouro
 * do domínio: nada de campo de "quantidade inicial" na criação do SKU).
 */
export function ProdutosPage() {
  const [produtos, setProdutos] = useState<ProdutoComVariacoes[] | null>(null);
  const [erroCarregamento, setErroCarregamento] = useState<string | null>(null);

  const [nomeProduto, setNomeProduto] = useState('');
  const [descricaoProduto, setDescricaoProduto] = useState('');
  const [erroFormProduto, setErroFormProduto] = useState<string | null>(null);
  const [enviandoProduto, setEnviandoProduto] = useState(false);

  const [produtoExpandido, setProdutoExpandido] = useState<string | null>(null);
  const [formsVariacao, setFormsVariacao] = useState<Record<string, FormVariacao>>({});

  useEffect(() => {
    let cancelado = false;

    async function carregar() {
      try {
        const lista = await listarProdutos();
        if (cancelado) return;
        setProdutos(lista);
      } catch (excecao) {
        if (cancelado) return;
        setErroCarregamento(
          excecao instanceof ApiError ? excecao.message : 'Não foi possível carregar os produtos. Tente novamente.',
        );
      }
    }

    carregar();
    return () => {
      cancelado = true;
    };
  }, []);

  async function aoSubmeterProduto(evento: FormEvent) {
    evento.preventDefault();
    setErroFormProduto(null);
    setEnviandoProduto(true);

    try {
      const novoProduto = await criarProduto({
        nome: nomeProduto,
        descricao: descricaoProduto.trim() === '' ? undefined : descricaoProduto,
      });
      setProdutos((atual) => [{ ...novoProduto, variacoes: [] }, ...(atual ?? [])]);
      setNomeProduto('');
      setDescricaoProduto('');
    } catch (excecao) {
      setErroFormProduto(
        excecao instanceof ApiError ? excecao.message : 'Não foi possível criar o produto. Tente novamente.',
      );
    } finally {
      setEnviandoProduto(false);
    }
  }

  function formVariacaoDe(produtoId: string): FormVariacao {
    return formsVariacao[produtoId] ?? FORM_VARIACAO_VAZIO;
  }

  function atualizarFormVariacao(produtoId: string, patch: Partial<FormVariacao>) {
    setFormsVariacao((atual) => ({
      ...atual,
      [produtoId]: { ...formVariacaoDe(produtoId), ...patch },
    }));
  }

  async function aoSubmeterVariacao(evento: FormEvent, produtoId: string) {
    evento.preventDefault();
    const form = formVariacaoDe(produtoId);

    const precoCentavos = parsearPrecoParaCentavos(form.precoTexto);
    if (precoCentavos === null || precoCentavos < 0) {
      atualizarFormVariacao(produtoId, { erro: 'Preço inválido -- use um valor como 89,90.' });
      return;
    }
    const pontoReposicao = Number(form.pontoReposicao);
    if (!Number.isInteger(pontoReposicao) || pontoReposicao < 0) {
      atualizarFormVariacao(produtoId, { erro: 'Ponto de reposição deve ser um número inteiro maior ou igual a zero.' });
      return;
    }

    atualizarFormVariacao(produtoId, { erro: null, enviando: true });

    try {
      const novaVariacao = await criarVariacao(produtoId, {
        sku: form.sku,
        atributos: parsearAtributos(form.atributosTexto),
        preco_centavos: precoCentavos,
        ponto_reposicao: pontoReposicao,
      });
      setProdutos((atual) =>
        (atual ?? []).map((produto) =>
          produto.id === produtoId ? { ...produto, variacoes: [...produto.variacoes, novaVariacao] } : produto,
        ),
      );
      setFormsVariacao((atual) => ({ ...atual, [produtoId]: FORM_VARIACAO_VAZIO }));
    } catch (excecao) {
      atualizarFormVariacao(produtoId, {
        enviando: false,
        erro: excecao instanceof ApiError ? excecao.message : 'Não foi possível criar a variação. Tente novamente.',
      });
    }
  }

  return (
    <>
      <header className="flex h-16 shrink-0 items-center justify-between border-b border-line px-8">
        <div className="flex items-baseline gap-3">
          <div className="font-display text-[19px] font-bold">Produtos</div>
          {produtos && (
            <span className="tag border border-line px-[7px] py-[3px] text-ink-faint">{produtos.length} NO TOTAL</span>
          )}
        </div>
      </header>

      <div className="flex flex-col gap-9 px-8 py-9">
        {erroCarregamento && (
          <div role="alert" className="border-l-2 border-accent-strong bg-accent-soft px-3.5 py-3 text-sm text-accent-strong">
            {erroCarregamento}
          </div>
        )}

        {!erroCarregamento && produtos === null && <div className="tag text-ink-faint">CARREGANDO…</div>}

        {produtos !== null &&
          (produtos.length === 0 ? (
            <EmptyState
              icone={<IconProdutos size={34} strokeWidth={1.4} />}
              titulo="Nenhum produto ainda"
              descricao="Cadastre o primeiro produto no formulário abaixo -- ele nasce sem SKU nenhum, você adiciona variações (tamanho, cor) em seguida."
            />
          ) : (
            <div className="flex flex-col gap-4">
              {produtos.map((produto) => {
                const expandido = produtoExpandido === produto.id;
                const form = formVariacaoDe(produto.id);
                return (
                  <div key={produto.id} className="border border-line">
                    <div className="flex items-center justify-between border-b border-line bg-paper-dim px-4 py-3">
                      <div className="flex flex-col gap-0.5">
                        <span className="font-display text-[15px] font-bold">{produto.nome}</span>
                        {produto.descricao && <span className="text-[13px] text-ink-soft">{produto.descricao}</span>}
                      </div>
                      <button
                        type="button"
                        onClick={() => setProdutoExpandido(expandido ? null : produto.id)}
                        className="flex items-center gap-1.5 border border-line px-2.5 py-1.5 text-[13px] text-ink-soft"
                      >
                        + NOVA VARIAÇÃO
                        <IconChevronDown className={expandido ? 'rotate-180' : ''} />
                      </button>
                    </div>

                    {produto.variacoes.length === 0 ? (
                      <div className="px-4 py-4 text-[13.5px] text-ink-faint">Nenhuma variação/SKU ainda.</div>
                    ) : (
                      <table className="w-full border-collapse text-left text-sm">
                        <thead>
                          <tr className="border-b border-line">
                            <th className="tag px-4 py-2.5 font-normal text-ink-faint">SKU</th>
                            <th className="tag px-4 py-2.5 font-normal text-ink-faint">ATRIBUTOS</th>
                            <th className="tag px-4 py-2.5 font-normal text-ink-faint">PREÇO</th>
                            <th className="tag px-4 py-2.5 font-normal text-ink-faint">PONTO DE REPOSIÇÃO</th>
                            <th className="tag px-4 py-2.5 font-normal text-ink-faint">STATUS</th>
                          </tr>
                        </thead>
                        <tbody>
                          {produto.variacoes.map((variacao) => (
                            <tr key={variacao.id} className="border-b border-line last:border-b-0">
                              <td className="px-4 py-2.5 font-mono text-ink-soft">{variacao.sku}</td>
                              <td className="px-4 py-2.5 text-ink-soft">
                                {Object.entries(variacao.atributos)
                                  .map(([chave, valor]) => `${chave}: ${valor}`)
                                  .join(' · ') || '—'}
                              </td>
                              <td className="px-4 py-2.5 font-mono">{formatarCentavos(variacao.preco_centavos)}</td>
                              <td className="px-4 py-2.5 font-mono text-ink-soft">{variacao.ponto_reposicao}</td>
                              <td className="px-4 py-2.5">
                                <span
                                  className={`tag inline-block px-2 py-1 ${variacao.ativo ? 'bg-stock-ok-soft text-stock-ok' : 'bg-stock-out-soft text-stock-out'}`}
                                >
                                  {variacao.ativo ? 'Ativo' : 'Inativo'}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}

                    {expandido && (
                      <form
                        className="flex flex-col gap-4 border-t border-line bg-paper-dim px-4 py-5"
                        onSubmit={(evento) => aoSubmeterVariacao(evento, produto.id)}
                        noValidate
                      >
                        <div className="grid grid-cols-2 gap-4">
                          <label className="flex flex-col gap-1.5">
                            <span className="tag text-ink-faint">SKU</span>
                            <input
                              type="text"
                              required
                              value={form.sku}
                              onChange={(evento) => atualizarFormVariacao(produto.id, { sku: evento.target.value })}
                              className={`${CLASSE_INPUT} font-mono`}
                              placeholder="CAM-M-AZUL"
                            />
                          </label>
                          <label className="flex flex-col gap-1.5">
                            <span className="tag text-ink-faint">ATRIBUTOS (OPCIONAL)</span>
                            <input
                              type="text"
                              value={form.atributosTexto}
                              onChange={(evento) =>
                                atualizarFormVariacao(produto.id, { atributosTexto: evento.target.value })
                              }
                              className={CLASSE_INPUT}
                              placeholder="tamanho:M, cor:Azul"
                            />
                          </label>
                          <label className="flex flex-col gap-1.5">
                            <span className="tag text-ink-faint">PREÇO (R$)</span>
                            <input
                              type="text"
                              required
                              inputMode="decimal"
                              value={form.precoTexto}
                              onChange={(evento) => atualizarFormVariacao(produto.id, { precoTexto: evento.target.value })}
                              className={CLASSE_INPUT}
                              placeholder="89,90"
                            />
                          </label>
                          <label className="flex flex-col gap-1.5">
                            <span className="tag text-ink-faint">PONTO DE REPOSIÇÃO</span>
                            <input
                              type="number"
                              min={0}
                              value={form.pontoReposicao}
                              onChange={(evento) =>
                                atualizarFormVariacao(produto.id, { pontoReposicao: evento.target.value })
                              }
                              className={CLASSE_INPUT}
                            />
                          </label>
                        </div>

                        {form.erro && (
                          <div role="alert" className="border-l-2 border-accent-strong bg-accent-soft px-3.5 py-3 text-sm text-accent-strong">
                            {form.erro}
                          </div>
                        )}

                        <button
                          type="submit"
                          disabled={form.enviando}
                          className="self-start bg-accent-strong px-3.5 py-2.5 font-display text-[13px] font-bold tracking-[0.02em] text-paper transition-opacity disabled:opacity-60"
                        >
                          {form.enviando ? 'CRIANDO SKU…' : 'CRIAR SKU'}
                        </button>
                      </form>
                    )}
                  </div>
                );
              })}
            </div>
          ))}

        <section className="flex max-w-[560px] flex-col gap-6 border border-line p-6">
          <div className="flex flex-col gap-1">
            <div className="font-display text-base font-bold">Novo produto</div>
            <div className="text-[13.5px] text-ink-soft">
              O produto nasce sem SKU -- adicione variações (tamanho, cor, etc.) depois de criado.
            </div>
          </div>

          <form className="flex flex-col gap-4" onSubmit={aoSubmeterProduto} noValidate>
            <label className="flex flex-col gap-1.5">
              <span className="tag text-ink-faint">NOME</span>
              <input
                type="text"
                required
                value={nomeProduto}
                onChange={(evento) => setNomeProduto(evento.target.value)}
                className={CLASSE_INPUT}
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="tag text-ink-faint">DESCRIÇÃO (OPCIONAL)</span>
              <input
                type="text"
                value={descricaoProduto}
                onChange={(evento) => setDescricaoProduto(evento.target.value)}
                className={CLASSE_INPUT}
              />
            </label>

            {erroFormProduto && (
              <div role="alert" className="border-l-2 border-accent-strong bg-accent-soft px-3.5 py-3 text-sm text-accent-strong">
                {erroFormProduto}
              </div>
            )}

            <button
              type="submit"
              disabled={enviandoProduto}
              className="bg-accent-strong px-3.5 py-3.5 font-display text-sm font-bold tracking-[0.02em] text-paper transition-opacity disabled:opacity-60"
            >
              {enviandoProduto ? 'CRIANDO PRODUTO…' : 'CRIAR PRODUTO'}
            </button>
          </form>
        </section>
      </div>
    </>
  );
}
