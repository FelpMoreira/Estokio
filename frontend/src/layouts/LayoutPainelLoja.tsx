import { Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { rotuloPapel } from '../auth/apresentacao';
import { BrandMark } from '../components/BrandMark';
import { SidebarNavItem } from '../components/SidebarNavItem';
import { SidebarUserFooter } from '../components/SidebarUserFooter';
import { IconAlertas } from '../components/icons/IconAlertas';
import { IconChevronDown } from '../components/icons/IconChevronDown';
import { IconConfiguracoes } from '../components/icons/IconConfiguracoes';
import { IconDashboard } from '../components/icons/IconDashboard';
import { IconEstoque } from '../components/icons/IconEstoque';
import { IconPedidos } from '../components/icons/IconPedidos';
import { IconProdutos } from '../components/icons/IconProdutos';

/** Casca do painel da loja (Admin/Operador): sidebar escura + área principal. */
export function LayoutPainelLoja() {
  const { claims, email, logout } = useAuth();
  const navigate = useNavigate();

  function sair() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-[250px] shrink-0 flex-col justify-between bg-panel-dark text-on-dark">
        <div>
          <div className="flex flex-col gap-3.5 border-b border-line-dark px-5 py-5">
            <BrandMark size={11} textClassName="text-[15px] text-on-dark" />
            <div className="flex items-center justify-between border border-line-dark px-2.5 py-2">
              <span className="tag text-on-dark">
                TENANT {claims?.tenant_id ? claims.tenant_id.slice(0, 8).toUpperCase() : '—'}
              </span>
              <IconChevronDown className="text-on-dark-soft" />
            </div>
          </div>

          <nav className="flex flex-col gap-0.5 px-3 py-4">
            <SidebarNavItem to="/loja" icone={<IconDashboard />}>
              Dashboard
            </SidebarNavItem>
            <SidebarNavItem to="/loja/produtos" icone={<IconProdutos />}>
              Produtos
            </SidebarNavItem>
            <SidebarNavItem to="/loja/estoque" icone={<IconEstoque />}>
              Estoque
            </SidebarNavItem>
            <SidebarNavItem icone={<IconPedidos />}>Pedidos</SidebarNavItem>
            <SidebarNavItem icone={<IconAlertas />}>Alertas</SidebarNavItem>
            <SidebarNavItem icone={<IconConfiguracoes />}>Configurações</SidebarNavItem>
          </nav>
        </div>

        <SidebarUserFooter
          email={email}
          papelLabel={claims ? rotuloPapel(claims.papel) : ''}
          onLogout={sair}
        />
      </aside>

      <main className="flex flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  );
}
