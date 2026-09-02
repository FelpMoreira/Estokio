import type { ReactNode } from 'react';

interface Props {
  icone: ReactNode;
  titulo: string;
  descricao: string;
  /** Largura máxima do texto de descrição, em px (mockups usam 420/440). */
  maxWidthDescricao?: number;
}

/**
 * Estado vazio honesto: borda tracejada + ícone + título + explicação do que
 * falta e quando chega. Usado em todo conteúdo que dependeria de dados reais
 * ainda não disponíveis (dashboards, catálogo público, grid de lojas) --
 * nunca mostramos dado inventado no lugar.
 */
export function EmptyState({ icone, titulo, descricao, maxWidthDescricao = 420 }: Props) {
  return (
    <div className="flex flex-col items-center justify-center gap-3.5 border border-dashed border-line-strong px-8 py-16">
      <div className="text-ink-faint">{icone}</div>
      <div className="font-display text-base font-bold">{titulo}</div>
      <div
        className="text-center text-[13.5px] leading-relaxed text-ink-soft"
        style={{ maxWidth: maxWidthDescricao }}
      >
        {descricao}
      </div>
    </div>
  );
}
