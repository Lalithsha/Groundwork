import { useState } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { api } from './api';
import { AuthPage } from './pages/AuthPage';
import { WorkspaceProvider } from './app/WorkspaceContext';
import { Layout } from './components/Layout';
import { DashboardPage } from './pages/DashboardPage';
import { ChangesPage } from './pages/ChangesPage';
import { ChangeDetailPage } from './pages/ChangeDetailPage';
import { EvidencePage } from './pages/EvidencePage';
import { ConnectionsPage } from './pages/ConnectionsPage';
import { PoliciesPage } from './pages/PoliciesPage';
import { ReleasesPage } from './pages/ReleasesPage';
import { SourcesPage } from './pages/SourcesPage';

export function App() {
  const [authenticated, setAuthenticated] = useState(api.isAuthenticated());
  if (!authenticated) return <AuthPage onAuthenticated={() => setAuthenticated(true)} />;
  return (
    <WorkspaceProvider>
      <Routes>
        <Route element={<Layout onSignOut={() => { api.clearSession(); setAuthenticated(false); }} />}>
          <Route index element={<DashboardPage />} />
          <Route path="changes" element={<ChangesPage />} />
          <Route path="changes/:changeId" element={<ChangeDetailPage />} />
          <Route path="evidence" element={<EvidencePage />} />
          <Route path="connections" element={<ConnectionsPage />} />
          <Route path="policies" element={<PoliciesPage />} />
          <Route path="releases" element={<ReleasesPage />} />
          <Route path="sources" element={<SourcesPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </WorkspaceProvider>
  );
}
