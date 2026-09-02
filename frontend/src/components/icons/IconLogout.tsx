import type { IconProps } from './IconProps';

/** Porta com seta de saída -- botão de logout no rodapé da sidebar. */
export function IconLogout({ size = 15, className, strokeWidth = 1.6 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth={strokeWidth} className={className}>
      <path d="M8 4 L4 4 L4 16 L8 16" />
      <path d="M12 7 L16 10 L12 13" />
      <path d="M16 10 H7" />
    </svg>
  );
}
