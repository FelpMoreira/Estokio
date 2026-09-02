import { Navigate, Route, Routes } from 'react-router-dom';
import { RotaProtegida } from './auth/RotaProtegida';
import { LayoutPainelLoja } from './layouts/LayoutPainelLoja';
import { LayoutPainelPlataforma } from './layouts/LayoutPainelPlataforma';
import { LoginPage } from './pages/LoginPage';
import { VitrineLojaPage } from './pages/VitrineLojaPage';
import { VitrinePlataformaPage } from './pages/VitrinePlataformaPage';
import { DashboardPage as DashboardLojaPage } from './pages/loja/DashboardPage';
import { DashboardPage as DashboardPlataformaPage } from './pages/plataforma/DashboardPage';

function App() {
  return (
    <Routes>
      <Route path="/" element={<VitrinePlataformaPage />} />
      <Route path="/l/:slug" element={<VitrineLojaPage />} />
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/loja"
        element={
          <RotaProtegida papeisPermitidos={['ADMIN_LOJA', 'OPERADOR']}>
            <LayoutPainelLoja />
          </RotaProtegida>
        }
      >
        <Route index element={<DashboardLojaPage />} />
      </Route>

      <Route
        path="/plataforma"
        element={
          <RotaProtegida papeisPermitidos={['SUPER_ADMIN']}>
            <LayoutPainelPlataforma />
          </RotaProtegida>
        }
      >
        <Route index element={<DashboardPlataformaPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
