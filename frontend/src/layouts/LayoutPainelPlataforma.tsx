import { Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { rotuloPapel } from '../auth/apresentacao';
import { BrandMark } from '../components/BrandMark';
import { SidebarNavItem } from '../components/SidebarNavItem';
import { SidebarUserFooter } from '../components/SidebarUserFooter';
import { IconDashboard } from '../components/icons/IconDashboard';
import { IconLojas } from '../components/icons/IconLojas';
import { IconPlanos } from '../components/icons/IconPlanos';

/** Casca do painel da plataforma (Super Admin): sidebar escura + área principal. */
export function LayoutPainelPlataforma() {
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
            <div className="tag py-0.5 text-on-dark-soft">PLATAFORMA</div>
          </div>

          <nav className="flex flex-col gap-0.5 px-3 py-4">
            <SidebarNavItem to="/plataforma" icone={<IconDashboard />}>
              Dashboard
            </SidebarNavItem>
            <SidebarNavItem icone={<IconLojas />}>Lojas</SidebarNavItem>
            <SidebarNavItem icone={<IconPlanos />}>Planos</SidebarNavItem>
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
