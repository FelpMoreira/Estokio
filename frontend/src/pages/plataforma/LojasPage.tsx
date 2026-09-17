import { type FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { criarLoja, listarLojas, listarPlanosAtivos } from '../../api/plataforma';
import type { Plano, StatusTenant, Tenant } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { IconLojas } from '../../components/icons/IconLojas';

const CLASSE_INPUT =
  'border-0 border-b-[1.5px] border-line-strong bg-transparent px-0.5 py-2 font-body text-[15px] text-ink outline-none focus:border-accent-strong';

const ROTULO_STATUS: Record<StatusTenant, string> = {
  PENDENTE: 'Pendente',
  ATIVA: 'Ativa',
  SUSPENSA: 'Suspensa',
  CANCELADA: 'Cancelada',
};

const CLASSE_STATUS: Record<StatusTenant, string> = {
  ATIVA: 'bg-stock-ok-soft text-stock-ok',
  PENDENTE: 'bg-paper-dim text-ink-faint',
  SUSPENSA: 'bg-stock-low-soft text-stock-low',
  CANCELADA: 'bg-stock-out-soft text-stock-out',
};

const FORMATADOR_DATA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

/**
 * Super Admin cria loja + admin inicial (Fase 1, item 1 -- ver Fase 1 - MVP). Tabela das
 * lojas existentes (nome, slug, status, plano por nome, cruzado localmente com a lista de
 * planos já carregada) + formulário inline de criação, sem modal.
 */
export function LojasPage() {
  const [lojas, setLojas] = useState<Tenant[] | null>(null);
  const [planos, setPlanos] = useState<Plano[] | null>(null);
  const [erroCarregamento, setErroCarregamento] = useState<string | null>(null);

  const [nomeLoja, setNomeLoja] = useState('');
  const [slug, setSlug] = useState('');
  const [planoId, setPlanoId] = useState('');
  const [nomeAdmin, setNomeAdmin] = useState('');
  const [emailAdmin, setEmailAdmin] = useState('');
  const [senhaAdmin, setSenhaAdmin] = useState('');
  const [erroFormulario, setErroFormulario] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    let cancelado = false;

    async function carregar() {
      try {
        const [listaLojas, listaPlanos] = await Promise.all([listarLojas(), listarPlanosAtivos()]);
        if (cancelado) return;
        setLojas(listaLojas);
        setPlanos(listaPlanos);
      } catch (excecao) {
        if (cancelado) return;
        setErroCarregamento(
          excecao instanceof ApiError ? excecao.message : 'Não foi possível carregar as lojas. Tente novamente.',
        );
      }
    }

    carregar();
    return () => {
      cancelado = true;
    };
  }, []);

  // Default honesto: primeiro plano ativo assim que a lista carrega -- evita mandar
  // plano_id vazio pro backend (não existe opção "nenhum plano" no domínio). Derivado
  // no render em vez de sincronizado via efeito -- não há sistema externo aqui.
  const planoSelecionado = planoId || planos?.[0]?.id || '';

  function nomeDoPlano(idPlano: string): string {
    return planos?.find((plano) => plano.id === idPlano)?.nome ?? idPlano.slice(0, 8);
  }

  async function aoSubmeter(evento: FormEvent) {
    evento.preventDefault();
    setErroFormulario(null);
    setEnviando(true);

    try {
      const novaLoja = await criarLoja({
        loja: { nome: nomeLoja, slug, plano_id: planoSelecionado },
        admin: { nome: nomeAdmin, email: emailAdmin, senha: senhaAdmin },
      });
      setLojas((atual) => [novaLoja, ...(atual ?? [])]);
      setNomeLoja('');
      setSlug('');
      setNomeAdmin('');
      setEmailAdmin('');
      setSenhaAdmin('');
    } catch (excecao) {
      setErroFormulario(
        excecao instanceof ApiError ? excecao.message : 'Não foi possível criar a loja. Tente novamente.',
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <header className="flex h-16 shrink-0 items-center justify-between border-b border-line px-8">
        <div className="flex items-baseline gap-3">
          <div className="font-display text-[19px] font-bold">Lojas</div>
          {lojas && <span className="tag border border-line px-[7px] py-[3px] text-ink-faint">{lojas.length} NO TOTAL</span>}
        </div>
      </header>

      <div className="flex flex-col gap-9 px-8 py-9">
        {erroCarregamento && (
          <div role="alert" className="border-l-2 border-accent-strong bg-accent-soft px-3.5 py-3 text-sm text-accent-strong">
            {erroCarregamento}
          </div>
        )}

        {!erroCarregamento && lojas === null && <div className="tag text-ink-faint">CARREGANDO…</div>}

        {lojas !== null &&
          (lojas.length === 0 ? (
            <EmptyState
              icone={<IconLojas size={34} strokeWidth={1.4} />}
              titulo="Nenhuma loja ainda"
              descricao="Crie a primeira loja da plataforma no formulário abaixo -- ela já nasce ativa, com o admin informado pronto para logar."
            />
          ) : (
            <div className="overflow-hidden border border-line">
              <table className="w-full border-collapse text-left text-sm">
                <thead>
                  <tr className="border-b border-line bg-paper-dim">
                    <th className="tag px-4 py-3 font-normal text-ink-faint">NOME</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">SLUG</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">STATUS</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">PLANO</th>
                    <th className="tag px-4 py-3 font-normal text-ink-faint">CRIADA EM</th>
                  </tr>
                </thead>
                <tbody>
                  {lojas.map((loja) => (
                    <tr key={loja.id} className="border-b border-line last:border-b-0">
                      <td className="px-4 py-3">{loja.nome}</td>
                      <td className="px-4 py-3 font-mono text-ink-soft">{loja.slug}</td>
                      <td className="px-4 py-3">
                        <span className={`tag inline-block px-2 py-1 ${CLASSE_STATUS[loja.status]}`}>
                          {ROTULO_STATUS[loja.status]}
                        </span>
                      </td>
                      <td className="px-4 py-3">{nomeDoPlano(loja.plano_id)}</td>
                      <td className="px-4 py-3 font-mono text-xs text-ink-faint">
                        {FORMATADOR_DATA.format(new Date(loja.criado_em))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}

        <section className="flex max-w-[560px] flex-col gap-6 border border-line p-6">
          <div className="flex flex-col gap-1">
            <div className="font-display text-base font-bold">Nova loja</div>
            <div className="text-[13.5px] text-ink-soft">
              A loja nasce ativa. Defina a senha do admin diretamente -- não há convite por email.
            </div>
          </div>

          <form className="flex flex-col gap-6" onSubmit={aoSubmeter} noValidate>
            <div className="flex flex-col gap-4">
              <span className="tag text-ink-faint">DADOS DA LOJA</span>
              <label className="flex flex-col gap-1.5">
                <span className="tag text-ink-faint">NOME</span>
                <input
                  type="text"
                  required
                  value={nomeLoja}
                  onChange={(evento) => setNomeLoja(evento.target.value)}
                  className={CLASSE_INPUT}
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="tag text-ink-faint">SLUG</span>
                <input
                  type="text"
                  required
                  value={slug}
                  onChange={(evento) => setSlug(evento.target.value)}
                  className={`${CLASSE_INPUT} font-mono`}
                  placeholder="loja-exemplo"
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="tag text-ink-faint">PLANO</span>
                <select
                  required
                  value={planoSelecionado}
                  onChange={(evento) => setPlanoId(evento.target.value)}
                  className={CLASSE_INPUT}
                >
                  {(planos ?? []).map((plano) => (
                    <option key={plano.id} value={plano.id}>
                      {plano.nome}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="flex flex-col gap-4">
              <span className="tag text-ink-faint">ADMIN INICIAL</span>
              <label className="flex flex-col gap-1.5">
                <span className="tag text-ink-faint">NOME</span>
                <input
                  type="text"
                  required
                  value={nomeAdmin}
                  onChange={(evento) => setNomeAdmin(evento.target.value)}
                  className={CLASSE_INPUT}
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="tag text-ink-faint">EMAIL</span>
                <input
                  type="email"
                  required
                  value={emailAdmin}
                  onChange={(evento) => setEmailAdmin(evento.target.value)}
                  className={CLASSE_INPUT}
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="tag text-ink-faint">SENHA</span>
                <input
                  type="password"
                  required
                  minLength={8}
                  value={senhaAdmin}
                  onChange={(evento) => setSenhaAdmin(evento.target.value)}
                  className={CLASSE_INPUT}
                />
                <span className="text-xs text-ink-faint">Mínimo de 8 caracteres.</span>
              </label>
            </div>

            {erroFormulario && (
              <div role="alert" className="border-l-2 border-accent-strong bg-accent-soft px-3.5 py-3 text-sm text-accent-strong">
                {erroFormulario}
              </div>
            )}

            <button
              type="submit"
              disabled={enviando || !planoSelecionado}
              className="bg-accent-strong px-3.5 py-3.5 font-display text-sm font-bold tracking-[0.02em] text-paper transition-opacity disabled:opacity-60"
            >
              {enviando ? 'CRIANDO LOJA…' : 'CRIAR LOJA'}
            </button>
          </form>
        </section>
      </div>
    </>
  );
}
