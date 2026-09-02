import type { IconProps } from './IconProps';

/** Escudo com check -- item de navegação "Planos" no painel da plataforma. */
export function IconPlanos({ size = 17, className, strokeWidth = 1.6 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <path d="M10 3 L17 6.5 V11 C17 14.5 14 16.7 10 17.5 C6 16.7 3 14.5 3 11 V6.5 Z" />
      <path d="M7.3 10 L9.2 11.9 L12.7 8.4" />
    </svg>
  );
}
