import { MetricTileGrid } from '../../components/MetricTileGrid';
import { EmptyState } from '../../components/EmptyState';
import { IconLojas } from '../../components/icons/IconLojas';

const ROTULOS_TILES = ['LOJAS ATIVAS', 'GMV TOTAL (MÊS)', 'NOVAS LOJAS', 'ALERTAS CRÍTICOS'];

export function DashboardPage() {
  return (
    <>
      <header className="flex h-16 shrink-0 items-center justify-between border-b border-line px-8">
        <div className="flex items-baseline gap-3">
          <div className="font-display text-[19px] font-bold">Dashboard da Plataforma</div>
          <span className="tag border border-line px-[7px] py-[3px] text-ink-faint">FASE 0</span>
        </div>
      </header>

      <div className="flex flex-col gap-7 px-8 py-9">
        <MetricTileGrid labels={ROTULOS_TILES} />

        <EmptyState
          icone={<IconLojas size={34} strokeWidth={1.4} />}
          titulo="Métricas agregadas chegam na Fase 2"
          descricao="GMV total, crescimento por loja e churn aparecem aqui assim que existir volume real de pedidos — sempre agregado, nunca dado operacional de uma loja."
          maxWidthDescricao={440}
        />
      </div>
    </>
  );
}
