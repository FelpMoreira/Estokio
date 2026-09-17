import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/useAuth';
import { PainelEsquerdoAutenticacao } from '../components/PainelEsquerdoAutenticacao';

/**
 * Cadastro de cliente (spec seção 11: POST /api/auth/registro-cliente). Cliente é
 * papel global -- o mesmo cadastro serve pra comprar em qualquer loja da plataforma.
 * Ao final, a sessão já fica autenticada (mesmo comportamento do login), mas ainda
 * não existe painel de cliente (chega na Fase 1) -- mesma tela final da LoginPage.
 */
export function RegistroPage() {
  const { registrar } = useAuth();

  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [cadastroConcluido, setCadastroConcluido] = useState(false);

  async function aoSubmeter(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);

    try {
      await registrar(nome, email, senha);
      setCadastroConcluido(true);
    } catch (excecao) {
      setErro(excecao instanceof ApiError ? excecao.message : 'Não foi possível criar a conta. Tente novamente.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="flex min-h-screen">
      <PainelEsquerdoAutenticacao />

      <div className="flex flex-1 items-center justify-center bg-paper p-8">
        <div className="flex w-full max-w-[380px] flex-col gap-7">
          {cadastroConcluido ? (
            <div className="flex flex-col gap-4">
              <div className="font-display text-3xl font-bold">Conta criada</div>
              <div className="border border-line bg-paper-dim px-4 py-4 text-sm leading-relaxed text-ink-soft">
                A área do cliente chega na Fase 1. Por enquanto, navegue pelo catálogo público das lojas.
              </div>
              <Link to="/" className="text-sm text-accent-strong hover:text-accent">
                Ver vitrine de lojas &rarr;
              </Link>
            </div>
          ) : (
            <>
              <div className="flex flex-col gap-1.5">
                <div className="font-display text-3xl font-bold">Criar conta</div>
                <div className="text-sm text-ink-soft">Um único cadastro compra em qualquer loja da plataforma.</div>
              </div>

              <form className="flex flex-col gap-5" onSubmit={aoSubmeter} noValidate>
                <div className="flex flex-col gap-4">
                  <label className="flex flex-col gap-1.5">
                    <span className="tag text-ink-faint">NOME</span>
                    <input
                      type="text"
                      autoComplete="name"
                      required
                      value={nome}
                      onChange={(evento) => setNome(evento.target.value)}
                      className="border-0 border-b-[1.5px] border-line-strong bg-transparent px-0.5 py-2 font-body text-[15px] text-ink outline-none focus:border-accent-strong"
                    />
                  </label>
                  <label className="flex flex-col gap-1.5">
                    <span className="tag text-ink-faint">EMAIL</span>
                    <input
                      type="email"
                      autoComplete="email"
                      required
                      value={email}
                      onChange={(evento) => setEmail(evento.target.value)}
                      className="border-0 border-b-[1.5px] border-line-strong bg-transparent px-0.5 py-2 font-body text-[15px] text-ink outline-none focus:border-accent-strong"
                    />
                  </label>
                  <label className="flex flex-col gap-1.5">
                    <span className="tag text-ink-faint">SENHA</span>
                    <input
                      type="password"
                      autoComplete="new-password"
                      required
                      minLength={8}
                      value={senha}
                      onChange={(evento) => setSenha(evento.target.value)}
                      className="border-0 border-b-[1.5px] border-line-strong bg-transparent px-0.5 py-2 font-body text-[15px] text-ink outline-none focus:border-accent-strong"
                    />
                    <span className="text-xs text-ink-faint">Mínimo de 8 caracteres.</span>
                  </label>
                </div>

                {erro && (
                  <div role="alert" className="border-l-2 border-accent-strong bg-accent-soft px-3.5 py-3 text-sm text-accent-strong">
                    {erro}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={enviando}
                  className="bg-accent-strong px-3.5 py-3.5 font-display text-sm font-bold tracking-[0.02em] text-paper transition-opacity disabled:opacity-60"
                >
                  {enviando ? 'CRIANDO CONTA…' : 'CRIAR CONTA'}
                </button>

                <div className="text-center text-sm text-ink-soft">
                  Já tem conta?{' '}
                  <Link to="/login" className="text-accent-strong hover:text-accent">
                    Entrar
                  </Link>
                </div>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
