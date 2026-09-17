import { BrandMark } from './BrandMark';

// Textura decorativa do painel esquerdo (mesmos valores do mockup Main.dc.html)
// -- é ornamento de UI, não dado de negócio, então não conflita com o
// princípio de "nenhuma tela com dado falso".
const LINHAS_LEDGER = ['+10', '+2', '-2', '-2', '+2', '+6', '+1', '-4', '+3', '-1', '+8', '-2', '+2', '-6', '+4', '+1'];

/**
 * Painel escuro compartilhado por login e cadastro (marca + textura de ledger +
 * citação). Extraído da LoginPage para o cadastro reusar o mesmo bloco, em vez de
 * duplicar um layout que já teve que ser ajustado uma vez (centralização vertical).
 */
export function PainelEsquerdoAutenticacao() {
  return (
    <div className="hidden w-[560px] shrink-0 flex-col bg-panel-dark p-14 text-on-dark lg:flex">
      <BrandMark size={14} textClassName="text-xl text-on-dark" />

      {/* Linhas de ledger e citação centralizadas como um grupo só no espaço
          abaixo da marca, em vez de a citação ficar presa no rodapé do painel
          em telas altas. */}
      <div className="flex flex-1 flex-col justify-center gap-12">
        <div className="flex flex-col gap-0 overflow-hidden opacity-50">
          {LINHAS_LEDGER.map((valor, indice) => (
            <div
              key={indice}
              className="flex h-7 items-center gap-4 border-b border-line-dark font-mono text-[11px] text-on-dark-soft"
            >
              <span className="w-7 text-right opacity-60">{indice + 1}</span>
              <span className="h-px flex-1 self-center border-b border-dotted border-line-dark" />
              <span className="w-[70px] text-right">{valor}</span>
            </div>
          ))}
        </div>

        <div className="flex flex-col gap-5">
          <div className="max-w-[420px] font-display text-[26px] font-semibold leading-snug">
            &ldquo;Todo movimento de estoque é um registro. Nada se edita — só se corrige.&rdquo;
          </div>
          <div className="tag text-on-dark-soft">PRINCÍPIO DO LEDGER APPEND-ONLY</div>
          <div className="flex gap-2.5">
            {['MULTI-TENANT', 'RLS', 'RESERVA COM LOCK'].map((selo) => (
              <span key={selo} className="tag border border-line-dark px-2 py-1 text-on-dark-soft">
                {selo}
              </span>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
