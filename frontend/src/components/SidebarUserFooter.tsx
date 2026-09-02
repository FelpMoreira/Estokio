import { iniciaisEmail } from '../auth/apresentacao';
import { IconLogout } from './icons/IconLogout';

interface Props {
  /** Email da sessão -- pode ser null logo após o F5, antes da restauração de sessão terminar. */
  email: string | null;
  papelLabel: string;
  onLogout: () => void;
}

/** Rodapé da sidebar dos painéis internos: identidade do usuário logado + botão de logout. */
export function SidebarUserFooter({ email, papelLabel, onLogout }: Props) {
  return (
    <div className="flex flex-col gap-3 border-t border-line-dark px-5 py-4">
      <div className="flex items-center gap-2.5">
        <div className="flex h-[30px] w-[30px] shrink-0 items-center justify-center bg-accent-soft font-display text-xs font-bold text-accent-strong">
          {email ? iniciaisEmail(email) : '··'}
        </div>
        <div className="flex flex-1 flex-col gap-px overflow-hidden">
          <div className="truncate text-[13px] text-on-dark">{email ?? 'Carregando…'}</div>
          <div className="tag text-on-dark-soft">{papelLabel}</div>
        </div>
        <button
          type="button"
          onClick={onLogout}
          aria-label="Sair da conta"
          title="Sair"
          className="shrink-0 text-on-dark-soft transition-colors hover:text-on-dark"
        >
          <IconLogout />
        </button>
      </div>
    </div>
  );
}
