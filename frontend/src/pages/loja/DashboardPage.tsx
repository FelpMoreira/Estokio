import { MetricTileGrid } from '../../components/MetricTileGrid';
import { EmptyState } from '../../components/EmptyState';
import { IconLedger } from '../../components/icons/IconLedger';

const ROTULOS_TILES = ['VENDAS DO MÊS', 'TICKET MÉDIO', 'PEDIDOS ABERTOS', 'ESTOQUE BAIXO'];

export function DashboardPage() {
  return (
    <>
      <header className="flex h-16 shrink-0 items-center justify-between border-b border-line px-8">
        <div className="flex items-baseline gap-3">
          <div className="font-display text-[19px] font-bold">Dashboard</div>
          <span className="tag border border-line px-[7px] py-[3px] text-ink-faint">FASE 0</span>
        </div>
      </header>

      <div className="flex flex-col gap-7 px-8 py-9">
        <MetricTileGrid labels={ROTULOS_TILES} />

        <EmptyState
          icone={<IconLedger />}
          titulo="Métricas chegam na Fase 1"
          descricao="Assim que produtos, estoque e pedidos existirem, este painel mostra vendas por período, ticket médio e estoque baixo em tempo real."
        />
      </div>
    </>
  );
}
