import type { IconProps } from './IconProps';

/** Engrenagem -- item de navegação "Configurações" no painel da loja. */
export function IconConfiguracoes({ size = 17, className, strokeWidth = 1.6 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <circle cx="10" cy="10" r="2.6" />
      <path d="M10 3.5 V5.5 M10 14.5 V16.5 M3.5 10 H5.5 M14.5 10 H16.5 M5.4 5.4 L6.8 6.8 M13.2 13.2 L14.6 14.6 M5.4 14.6 L6.8 13.2 M13.2 6.8 L14.6 5.4" />
    </svg>
  );
}
