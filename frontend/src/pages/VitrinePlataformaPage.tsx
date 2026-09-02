import { Link } from 'react-router-dom';
import { BrandMark } from '../components/BrandMark';
import { EmptyState } from '../components/EmptyState';
import { IconLojas } from '../components/icons/IconLojas';

/**
 * Vitrine pública da plataforma (rota "/"). Mesmo layout/cabeçalho do mockup
 * VitrinePlataforma.dc.html, mas sem a faixa de estatísticas nem a grade de
 * lojas -- eram dados fictícios pra aprovar a direção visual. GET
 * /api/plataforma/lojas (ou um equivalente público) ainda não existe, então
 * aqui fica um estado vazio honesto até a Fase 1.
 */
export function VitrinePlataformaPage() {
  return (
    <div className="min-h-screen bg-paper">
      <header className="flex h-[76px] items-center justify-between border-b border-line px-16">
        <BrandMark size={13} textClassName="text-lg" />
        <nav className="flex items-center gap-9">
          <a href="#" className="text-sm text-ink-soft">
            Como funciona
          </a>
          <a href="#" className="text-sm text-ink-soft">
            Para lojistas
          </a>
          <Link
            to="/login"
            className="border-[1.5px] border-ink px-5 py-2.5 font-display text-[13px] font-semibold text-ink hover:bg-ink hover:text-paper"
          >
            ENTRAR
          </Link>
        </nav>
      </header>

      <section className="flex max-w-[820px] flex-col gap-5 px-16 pb-12 pt-18">
        <div className="tag text-accent-strong">MARKETPLACE DE LOJAS</div>
        <h1 className="font-display text-[44px] font-bold leading-[1.15] tracking-tight">
          Estoque auditável para negócios que cansaram da planilha.
        </h1>
        <p className="max-w-[620px] text-base leading-relaxed text-ink-soft">
          Cada loja aqui roda num ledger de estoque à prova de divergência, com reserva e concorrência
          tratadas de verdade — não é só um catálogo bonito.
        </p>
      </section>

      <section className="px-16 pb-22">
        <EmptyState
          icone={<IconLojas size={34} strokeWidth={1.4} />}
          titulo="Catálogo de lojas chega na Fase 1"
          descricao="Assim que as lojas começarem a operar por aqui, esta página lista cada uma com produtos, categoria e link direto pra vitrine."
          maxWidthDescricao={460}
        />
      </section>

      <footer className="flex items-center justify-between border-t border-line px-16 py-7">
        <div className="text-sm text-ink-faint">
          Estokio — plataforma para pequenos negócios gerenciarem estoque e pedidos.
        </div>
        <div className="flex gap-6">
          <a href="#" className="text-sm text-ink-faint">
            Criar minha loja
          </a>
          <Link to="/login" className="text-sm text-ink-faint">
            Entrar
          </Link>
        </div>
      </footer>
    </div>
  );
}
