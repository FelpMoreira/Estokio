import type { IconProps } from './IconProps';

/** Seta pra baixo -- seletor de loja na sidebar e filtro de período no dashboard. */
export function IconChevronDown({ size = 11, className, strokeWidth = 1.8 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <path d="M5 8 L10 13 L15 8" />
    </svg>
  );
}
