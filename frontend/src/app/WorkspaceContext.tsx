import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type WorkspaceDto } from '../api';

interface WorkspaceState {
  workspaces: WorkspaceDto[]; workspace?: WorkspaceDto; workspaceId: string;
  setWorkspaceId: (id: string) => void; loading: boolean; error?: Error;
  createWorkspace: (name: string, description: string) => Promise<void>;
}

const WorkspaceContext = createContext<WorkspaceState | null>(null);

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const [workspaceId, setWorkspaceIdState] = useState(() => localStorage.getItem('groundwork_workspace_id') || '');
  const query = useQuery({ queryKey: ['workspaces'], queryFn: () => api.workspaces() });
  const create = useMutation({ mutationFn: ({ name, description }: { name: string; description: string }) => api.createWorkspace(name, description) });
  const workspaces = query.data || [];
  useEffect(() => {
    if (!workspaces.length) return;
    if (!workspaces.some(item => item.id === workspaceId)) {
      setWorkspaceIdState(workspaces[0].id);
      localStorage.setItem('groundwork_workspace_id', workspaces[0].id);
    }
  }, [workspaces, workspaceId]);
  const setWorkspaceId = (id: string) => { setWorkspaceIdState(id); localStorage.setItem('groundwork_workspace_id', id) };
  const value = useMemo<WorkspaceState>(() => ({
    workspaces, workspaceId, workspace: workspaces.find(item => item.id === workspaceId),
    setWorkspaceId, loading: query.isLoading, error: query.error as Error | undefined,
    createWorkspace: async (name, description) => {
      const workspace = await create.mutateAsync({ name, description });
      await client.invalidateQueries({ queryKey: ['workspaces'] }); setWorkspaceId(workspace.id);
    }
  }), [workspaces, workspaceId, query.isLoading, query.error, create, client]);
  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace() {
  const value = useContext(WorkspaceContext);
  if (!value) throw new Error('Workspace context is unavailable');
  return value;
}
