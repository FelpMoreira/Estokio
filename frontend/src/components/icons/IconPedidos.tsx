import type { IconProps } from './IconProps';

/** Recibo/nota -- item de navegação "Pedidos" no painel da loja. */
export function IconPedidos({ size = 17, className, strokeWidth = 1.6 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <rect x="4" y="2.5" width="12" height="15" rx="0.5" />
      <path d="M7 7 H13 M7 10.5 H13 M7 14 H10.5" />
    </svg>
  );
}
