interface Props {
  /** Tamanho do quadrado em px -- os mockups usam 14 (login), 13 (topbar pública) e 11 (sidebar). */
  size?: number;
  /** Tamanho do texto "ESTOKIO" em Tailwind arbitrary value. */
  textClassName?: string;
  className?: string;
}

/** Marca "quadrado âmbar + ESTOKIO" repetida em todas as telas (login, vitrines, sidebars). */
export function BrandMark({ size = 13, textClassName = 'text-[15px]', className = '' }: Props) {
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <div className="shrink-0 bg-accent" style={{ width: size, height: size }} />
      <div className={`font-display font-extrabold tracking-[0.02em] ${textClassName}`}>ESTOKIO</div>
    </div>
  );
}
