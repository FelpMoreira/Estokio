import type { IconProps } from './IconProps';

/** Sino -- item de navegação "Alertas" no painel da loja. */
export function IconAlertas({ size = 17, className, strokeWidth = 1.6 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <path d="M5 8.5 C5 5 7 3 10 3 C13 3 15 5 15 8.5 C15 12 16.5 13 16.5 13 H3.5 C3.5 13 5 12 5 8.5 Z" />
      <path d="M8.3 15.5 C8.3 16.4 9 17 10 17 C11 17 11.7 16.4 11.7 15.5" />
    </svg>
  );
}
