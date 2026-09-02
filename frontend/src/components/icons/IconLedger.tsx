import type { IconProps } from './IconProps';

/** Quadro cruzado (planilha/ledger em branco) -- ícone do estado vazio do dashboard da loja. */
export function IconLedger({ size = 34, className, strokeWidth = 1.4 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <rect x="3" y="3" width="14" height="14" />
      <path d="M3 8 H17 M8 3 V17" />
    </svg>
  );
}
