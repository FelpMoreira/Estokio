import type { IconProps } from './IconProps';

/** Prateleira/paletização -- item de navegação "Estoque" no painel da loja. */
export function IconEstoque({ size = 17, className, strokeWidth = 1.6 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <path d="M3 15 L3 7 L10 3 L17 7 L17 15 L3 15" />
      <path d="M3 7 L10 11 L17 7" />
      <path d="M10 11 V17" />
    </svg>
  );
}
