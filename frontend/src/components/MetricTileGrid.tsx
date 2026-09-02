import { MetricTile } from './MetricTile';

interface Props {
  labels: string[];
}

/** Grade de 4 colunas com fios de 1px entre os tiles -- padrão visual repetido nos dois dashboards. */
export function MetricTileGrid({ labels }: Props) {
  return (
    <div className="grid grid-cols-4 gap-px border border-line bg-line">
      {labels.map((label) => (
        <MetricTile key={label} label={label} />
      ))}
    </div>
  );
}
