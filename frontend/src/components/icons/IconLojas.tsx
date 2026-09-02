import type { IconProps } from './IconProps';

/** Duas fachadas -- item de navegação "Lojas" no painel da plataforma. */
export function IconLojas({ size = 17, className, strokeWidth = 1.6 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <rect x="2.5" y="8" width="6" height="9" />
      <rect x="11.5" y="4" width="6" height="13" />
      <path d="M4 11 H7 M4 13.5 H7 M12.5 7 H16 M12.5 9.5 H16 M12.5 12 H16" />
    </svg>
  );
}
