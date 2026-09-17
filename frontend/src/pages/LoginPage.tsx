import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/useAuth';
import { rotaDeCasaParaPapel } from '../auth/rotas';
import { PainelEsquerdoAutenticacao } from '../components/PainelEsquerdoAutenticacao';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [loginClienteConcluido, setLoginClienteConcluido] = useState(false);

  async function aoSubmeter(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);

    try {
      const claims = await login(email, senha);
      if (claims.papel === 'CLIENTE') {
        // Não existe painel de cliente ainda (chega na Fase 1) -- a sessão já
        // foi criada, só não navegamos pra uma rota que não existe.
        setLoginClienteConcluido(true);
        return;
      }
      navigate(rotaDeCasaParaPapel(claims.papel), { replace: true });
    } catch (excecao) {
      setErro(excecao instanceof ApiError ? excecao.message : 'Não foi possível entrar. Tente novamente.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="flex min-h-screen">
      <PainelEsquerdoAutenticacao />

      {/* Painel direito: formulário */}
      <div className="flex flex-1 items-center justify-center bg-paper p-8">
        <div className="flex w-full max-w-[380px] flex-col gap-7">
          {loginClienteConcluido ? (
            <div className="flex flex-col gap-4">
              <div className="font-display text-3xl font-bold">Login efetuado</div>
              <div className="border border-line bg-paper-dim px-4 py-4 text-sm leading-relaxed text-ink-soft">
                A área do cliente chega na Fase 1. Por enquanto, navegue pelo catálogo público das lojas.
              </div>
              <Link to="/" className="text-sm">
                Ver vitrine de lojas &rarr;
              </Link>
            </div>
          ) : (
            <>
              <div className="flex flex-col gap-1.5">
                <div className="font-display text-3xl font-bold">Entrar</div>
                <div className="text-sm text-ink-soft">Acesse o painel da sua loja ou da plataforma.</div>
              </div>

              <form className="flex flex-col gap-5" onSubmit={aoSubmeter} noValidate>
                <div className="flex flex-col gap-4">
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
                      autoComplete="current-password"
                      required
                      value={senha}
                      onChange={(evento) => setSenha(evento.target.value)}
                      className="border-0 border-b-[1.5px] border-line-strong bg-transparent px-0.5 py-2 font-body text-[15px] text-ink outline-none focus:border-accent-strong"
                    />
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
                  {enviando ? 'ENTRANDO…' : 'ENTRAR'}
                </button>

                <div className="mt-1 flex items-center gap-3">
                  <div className="h-px flex-1 bg-line" />
                  <span className="tag text-ink-faint">OU</span>
                  <div className="h-px flex-1 bg-line" />
                </div>

                <div className="flex flex-col items-center gap-1.5 text-center text-sm text-ink-soft">
                  <div>
                    Quer comprar em uma loja?{' '}
                    <Link to="/" className="text-accent-strong hover:text-accent">
                      Ver vitrine de lojas &rarr;
                    </Link>
                  </div>
                  <div>
                    Ainda não tem conta?{' '}
                    <Link to="/registro" className="text-accent-strong hover:text-accent">
                      Criar conta
                    </Link>
                  </div>
                </div>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
