import { Link, useParams } from 'react-router-dom';
import { EmptyState } from '../components/EmptyState';
import { IconProdutos } from '../components/icons/IconProdutos';

/**
 * Vitrine pública de uma loja (rota "/l/:slug"). Mesmo layout do mockup
 * VitrineLoja.dc.html, mas sem nome/categoria/descrição/produtos inventados:
 * GET /api/lojas/{slug} e GET /api/lojas/{slug}/catalogo ainda não existem
 * (chegam na Fase 1), então só mostramos o que é real -- o slug da URL --
 * e um estado vazio honesto no lugar do catálogo.
 */
export function VitrineLojaPage() {
  const { slug } = useParams<{ slug: string }>();

  return (
    <div className="min-h-screen bg-paper">
      <header className="flex h-14 items-center border-b border-line px-16">
        <Link to="/" className="font-mono text-xs text-ink-faint hover:text-ink-soft">
          &larr; TODAS AS LOJAS
        </Link>
      </header>

      <div className="flex items-center gap-5 border-b border-line px-16 py-10">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center bg-accent-soft text-accent-strong">
          <IconProdutos size={24} strokeWidth={1.4} />
        </div>
        <div className="flex flex-1 flex-col gap-1.5">
          <div className="flex items-center gap-3">
            <span className="font-mono text-sm text-ink-faint">/l/{slug}</span>
          </div>
          <div className="text-sm text-ink-soft">
            Ainda não conseguimos buscar os dados desta loja — o catálogo público chega na Fase 1.
          </div>
        </div>
      </div>

      <div className="px-16 py-22">
        <EmptyState
          icone={<IconProdutos size={34} strokeWidth={1.4} />}
          titulo="Catálogo desta loja chega na Fase 1"
          descricao="Produtos, variações, preço e disponibilidade em tempo real aparecem aqui assim que o catálogo público existir."
        />
      </div>
    </div>
  );
}
