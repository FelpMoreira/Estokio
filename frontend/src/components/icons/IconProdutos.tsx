import type { IconProps } from './IconProps';

/** Caixa aberta -- item de navegação "Produtos" no painel da loja. */
export function IconProdutos({ size = 17, className, strokeWidth = 1.6 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <path d="M3 6 L10 2.5 L17 6 L10 9.5 Z" />
      <path d="M3 6 V14 L10 17.5 L17 14 V6" />
      <path d="M10 9.5 V17.5" />
    </svg>
  );
}
