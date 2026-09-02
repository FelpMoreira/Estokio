import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';

interface Props {
  icone: ReactNode;
  children: ReactNode;
  /** Só rotas que já existem na Fase 0 recebem `to` e viram link navegável de verdade. */
  to?: string;
}

const CLASSE_BASE = 'flex items-center gap-3 border-l-2 px-4 py-2.5 text-[13.5px]';

/**
 * Item de navegação da sidebar (painel da loja e da plataforma). Itens sem
 * `to` representam features que ainda não existem (Produtos, Estoque,
 * Pedidos, Alertas, Config, Lojas, Planos) -- ficam visíveis, como no
 * mockup, mas não navegam pra lugar nenhum: melhor não fingir que
 * funcionam do que criar uma rota morta.
 */
export function SidebarNavItem({ to, icone, children }: Props) {
  if (!to) {
    return (
      <div className={`${CLASSE_BASE} cursor-default border-transparent text-on-dark-soft/50`}>
        {icone}
        <span>{children}</span>
      </div>
    );
  }

  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `${CLASSE_BASE} ${isActive ? 'border-accent bg-panel-dark-soft text-on-dark' : 'border-transparent text-on-dark-soft'}`
      }
    >
      {icone}
      <span>{children}</span>
    </NavLink>
  );
}
