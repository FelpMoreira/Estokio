import { Fragment, type FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { listarSaldos, registrarEntrada } from '../../api/estoque';
import type { EstoqueSaldoResumo } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { IconEstoque } from '../../components/icons/IconEstoque';

const CLASSE_INPUT =
  'border-0 border-b-[1.5px] border-line-strong bg-transparent px-0.5 py-1.5 font-body text-sm text-ink outline-none focus:border-accent-strong';

interface FormEntrada {
  quantidade: string;
  motivo: string;
  erro: string | null;
  enviando: boolean;
}

const FORM_ENTRADA_VAZIO: FormEntrada = { quantidade: '', motivo: '', erro: null, enviando: false };

function classeDisponivel(saldo: EstoqueSaldoResumo): string {
  if (saldo.disponivel <= 0) return 'text-stock-out';
  if (saldo.disponivel <= saldo.ponto_reposicao) return 'text-stock-low';
  return 'text-stock-ok';
}

/**
 * Consulta de saldos + entrada de estoque (Fase 1, item 2 -- ver [[Ledger de Estoque]]).
 * Estoque físico só sobe via POST /api/loja/estoque/entrada, que gera um movimento
 * ENTRADA no ledger e atualiza o snapshot na mesma transação -- não existe campo de
 * "quantidade" editável direto na tabela, de propósito.
 */
export function EstoquePage() {
  const [saldos, setSaldos] = useState<EstoqueSaldoResumo[] | null>(null);
  const [erroCarregamento, setErroCarregamento] = useState<string | null>(null);
  const [linhaAberta, setLinhaAberta] = useState<string | null>(null);
  const [formsEntrada, setFormsEntrada] = useState<Record<string, FormEntrada>>({});

  useEffect(() => {
    let cancelado = false;

    async function carregar() {
      try {
        const lista = await listarSaldos();
        if (cancelado) return;
        setSaldos(lista);
      } catch (excecao) {
        if (cancelado) return;
        setErroCarregamento(
          excecao instanceof ApiError ? excecao.message : 'Não foi possível carregar o estoque. Tente novamente.',
        );
      }
    }

    carregar();
    return () => {
      cancelado = true;
    };
  }, []);

  function formDe(variacaoId: string): FormEntrada {
    return formsEntrada[variacaoId] ?? FORM_ENTRADA_VAZIO;
  }

  function atualizarForm(variacaoId: string, patch: Partial<FormEntrada>) {
    setFormsEntrada((atual) => ({ ...atual, [variacaoId]: { ...formDe(variacaoId), ...patch } }));
  }

  async function aoSubmeterEntrada(evento: FormEvent, variacaoId: string) {
    evento.preventDefault();
    const form = formDe(variacaoId);

    const quantidade = Number(form.quantidade);
    if (!Number.isInteger(quantidade) || quantidade <= 0) {
      atualizarForm(variacaoId, { erro: 'Quantidade deve ser um número inteiro maior que zero.' });
      return;
    }

    atualizarForm(variacaoId, { erro: null, enviando: true });

    try {
      const saldoAtualizado = await registrarEntrada({
        variacao_id: variacaoId,
        quantidade,
        motivo: form.motivo.trim() === '' ? undefined : form.motivo,
      });
      setSaldos((atual) => (atual ?? []).map((saldo) => (saldo.variacao_id === variacaoId ? saldoAtualizado : saldo)));
      setFormsEntrada((atual) => ({ ...atual, [variacaoId]: FORM_ENTRADA_VAZIO }));
      setLinhaAberta(null);
    } catch (excecao) {
      atualizarForm(variacaoId, {
        enviando: false,
        erro: excecao instanceof ApiError ? excecao.message : 'Não foi possível registrar a entrada. Tente novamente.',
      });
    }
  }

  return (
    <>
      <header className="flex h-16 shrink-0 items-center justify-between border-b border-line px-8">
        <div className="flex items-baseline gap-3">
          <div className="font-display text-[19px] font-bold">Estoque</div>
          {saldos && <span className="tag border border-line px-[7px] py-[3px] text-ink-faint">{saldos.length} SKUS</span>}
        </div>
      </header>

      <div className="flex flex-col gap-9 px-8 py-9">
        {erroCarregamento && (
          <div role="alert" className="border-l-2 border-accent-strong bg-accent-soft px-3.5 py-3 text-sm text-accent-strong">
            {erroCarregamento}
          </div>
        )}

        {!erroCarregamento && saldos === null && <div className="tag text-ink-faint">CARREGANDO…</div>}

        {saldos !== null &&
          (saldos.length === 0 ? (
            <EmptyState
              icone={<IconEstoque size={34} strokeWidth={1.4} />}
              titulo="Nenhum SKU com saldo ainda"
              descricao="Cadastre um produto e uma variação em Produtos -- o saldo aparece aqui zerado, pronto para receber a primeira entrada de estoque."
            />
          ) : (
            <div className="overflow-hidden border border-line">
              <table className="w-full border-collapse text-left text-sm">
                <thead>
                  <tr className="border-b border-line bg-paper-dim">
                    <th className="tag px-4 py-3 font-normal text-ink-faint">SKU</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">PRODUTO</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">FÍSICO</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">RESERVADO</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">DISPONÍVEL</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">PONTO DE REPOSIÇÃO</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint"></th>
                  </tr>
                </thead>
                <tbody>
                  {saldos.map((saldo) => {
                    const aberta = linhaAberta === saldo.variacao_id;
                    const form = formDe(saldo.variacao_id);
                    return (
                      <Fragment key={saldo.variacao_id}>
                        <tr className="border-b border-line last:border-b-0">
                          <td className="px-4 py-3 font-mono text-ink-soft">{saldo.sku}</td>
                          <td className="px-4 py-3">{saldo.nome_produto}</td>
                          <td className="px-4 py-3 font-mono">{saldo.qtd_fisica}</td>
                          <td className="px-4 py-3 font-mono text-ink-soft">{saldo.qtd_reservada}</td>
                          <td className={`px-4 py-3 font-mono font-bold ${classeDisponivel(saldo)}`}>{saldo.disponivel}</td>
                          <td className="px-4 py-3 font-mono text-ink-soft">{saldo.ponto_reposicao}</td>
                          <td className="px-4 py-3 text-right">
                            <button
                              type="button"
                              onClick={() => setLinhaAberta(aberta ? null : saldo.variacao_id)}
                              className="border border-line px-2.5 py-1.5 text-[13px] text-ink-soft"
                            >
                              {aberta ? 'CANCELAR' : '+ ENTRADA'}
                            </button>
                          </td>
                        </tr>
                        {aberta && (
                          <tr className="border-b border-line bg-paper-dim last:border-b-0">
                            <td colSpan={7} className="px-4 py-4">
                              <form
                                className="flex flex-wrap items-end gap-4"
                                onSubmit={(evento) => aoSubmeterEntrada(evento, saldo.variacao_id)}
                                noValidate
                              >
                                <label className="flex flex-col gap-1">
                                  <span className="tag text-ink-faint">QUANTIDADE</span>
                                  <input
                                    type="number"
                                    min={1}
                                    required
                                    autoFocus
                                    value={form.quantidade}
                                    onChange={(evento) => atualizarForm(saldo.variacao_id, { quantidade: evento.target.value })}
                                    className={`${CLASSE_INPUT} w-28`}
                                  />
                                </label>
                                <label className="flex flex-col gap-1">
                                  <span className="tag text-ink-faint">MOTIVO (OPCIONAL)</span>
                                  <input
                                    type="text"
                                    value={form.motivo}
                                    onChange={(evento) => atualizarForm(saldo.variacao_id, { motivo: evento.target.value })}
                                    className={`${CLASSE_INPUT} w-64`}
                                    placeholder="Reposição de fornecedor"
                                  />
                                </label>
                                <button
                                  type="submit"
                                  disabled={form.enviando}
                                  className="bg-accent-strong px-3.5 py-2 font-display text-[13px] font-bold tracking-[0.02em] text-paper transition-opacity disabled:opacity-60"
                                >
                                  {form.enviando ? 'REGISTRANDO…' : 'REGISTRAR ENTRADA'}
                                </button>
                                {form.erro && <span className="text-sm text-accent-strong">{form.erro}</span>}
                              </form>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ))}
      </div>
    </>
  );
}
