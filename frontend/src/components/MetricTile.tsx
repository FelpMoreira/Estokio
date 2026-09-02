interface Props {
  label: string;
}

/**
 * Tile de métrica com valor "—" (traço) em vez de número inventado -- a Fase 0
 * não tem produto/estoque/pedido real, então não há métrica pra calcular.
 */
export function MetricTile({ label }: Props) {
  return (
    <div className="flex flex-col gap-2.5 bg-paper p-5">
      <div className="tag text-ink-faint">{label}</div>
      <div className="font-mono text-[26px] font-medium text-ink-faint">—</div>
    </div>
  );
}
