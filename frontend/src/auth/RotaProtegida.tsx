import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import type { Papel } from '../api/types';
import { rotaDeCasaParaPapel } from './rotas';
import { useAuth } from './useAuth';

interface Props {
  papeisPermitidos: Papel[];
  children: ReactNode;
}

/**
 * Guarda de rota por papel: sem sessão válida → /login; com sessão de papel
 * incompatível com a rota → bounce para a rota de casa do papel (nunca deixa
 * o painel errado renderizar).
 */
export function RotaProtegida({ papeisPermitidos, children }: Props) {
  const { claims, restaurandoSessao } = useAuth();

  if (restaurandoSessao) {
    return null;
  }

  if (!claims) {
    return <Navigate to="/login" replace />;
  }

  if (!papeisPermitidos.includes(claims.papel)) {
    return <Navigate to={rotaDeCasaParaPapel(claims.papel)} replace />;
  }

  return <>{children}</>;
}
