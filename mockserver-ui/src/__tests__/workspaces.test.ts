import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { useDashboardStore, type Workspace } from '../store';

/**
 * Workspace initialisation (like view/search persistence) is resolved when the
 * store module is created, so init tests set up localStorage first and then
 * import a fresh copy of the module.
 */
async function freshStore() {
  vi.resetModules();
  const mod = await import('../store');
  return mod.useDashboardStore;
}

function workspace(id: string, name: string, overrides: Partial<Workspace> = {}): Workspace {
  return {
    id,
    name,
    view: 'get-started',
    logSearch: '',
    expectationSearch: '',
    receivedSearch: '',
    proxiedSearch: '',
    trafficSearch: '',
    connection: null,
    ...overrides,
  };
}

/** Reset the singleton store to a clean single-workspace baseline. */
function resetStore(): void {
  useDashboardStore.setState({
    view: 'get-started',
    logSearch: '',
    expectationSearch: '',
    receivedSearch: '',
    proxiedSearch: '',
    trafficSearch: '',
    selectedTrafficKey: null,
    workspaces: [workspace('workspace-1', 'Workspace 1')],
    activeWorkspaceId: 'workspace-1',
  });
}

describe('workspaces — initialisation and migration', () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    globalThis.location.hash = '';
  });

  afterEach(() => {
    globalThis.localStorage.clear();
    globalThis.location.hash = '';
    vi.resetModules();
  });

  it('starts with exactly one workspace on a genuine first visit', async () => {
    const store = await freshStore();
    const state = store.getState();
    expect(state.workspaces).toHaveLength(1);
    expect(state.activeWorkspaceId).toBe(state.workspaces[0]!.id);
    expect(state.view).toBe('get-started');
    expect(state.workspaces[0]!.view).toBe('get-started');
  });

  it('migrates an existing user: the persisted view and searches become workspace 1', async () => {
    // An upgrading user has these two keys and NO workspaces key. Neither value
    // may be silently dropped.
    globalThis.localStorage.setItem('mockserver-view', 'contract');
    globalThis.localStorage.setItem(
      'mockserver-search',
      JSON.stringify({ logSearch: 'boom', expectationSearch: '', receivedSearch: 'r', proxiedSearch: '', trafficSearch: 'status:500' }),
    );

    const store = await freshStore();
    const state = store.getState();

    expect(state.workspaces).toHaveLength(1);
    expect(state.view).toBe('contract');
    expect(state.logSearch).toBe('boom');
    expect(state.trafficSearch).toBe('status:500');
    expect(state.workspaces[0]).toMatchObject({
      view: 'contract',
      logSearch: 'boom',
      receivedSearch: 'r',
      trafficSearch: 'status:500',
    });
  });

  it('does not write the workspaces key while merely loading', async () => {
    globalThis.localStorage.setItem('mockserver-view', 'traffic');
    await freshStore();
    expect(globalThis.localStorage.getItem('mockserver-workspaces')).toBeNull();
  });

  it('restores several workspaces, with the legacy keys authoritative for the active one', async () => {
    globalThis.localStorage.setItem('mockserver-view', 'metrics');
    globalThis.localStorage.setItem(
      'mockserver-search',
      JSON.stringify({ logSearch: 'live', expectationSearch: '', receivedSearch: '', proxiedSearch: '', trafficSearch: '' }),
    );
    globalThis.localStorage.setItem('mockserver-workspaces', JSON.stringify({
      activeWorkspaceId: 'a',
      workspaces: [
        // Deliberately stale: the live keys above are what the user actually had.
        workspace('a', 'Alpha', { view: 'traffic', logSearch: 'stale' }),
        workspace('b', 'Beta', { view: 'chaos', trafficSearch: 'method:POST' }),
      ],
    }));

    const store = await freshStore();
    const state = store.getState();

    expect(state.workspaces.map((w) => w.name)).toEqual(['Alpha', 'Beta']);
    expect(state.activeWorkspaceId).toBe('a');
    // Active workspace: live keys win over the stale snapshot.
    expect(state.view).toBe('metrics');
    expect(state.logSearch).toBe('live');
    expect(state.workspaces[0]).toMatchObject({ view: 'metrics', logSearch: 'live' });
    // Inactive workspace keeps its own stored state untouched.
    expect(state.workspaces[1]).toMatchObject({ view: 'chaos', trafficSearch: 'method:POST' });
  });

  it('falls back to a single workspace carrying the legacy values when the blob is malformed', async () => {
    globalThis.localStorage.setItem('mockserver-view', 'drift');
    globalThis.localStorage.setItem('mockserver-workspaces', '{not json');

    const store = await freshStore();
    const state = store.getState();

    expect(state.workspaces).toHaveLength(1);
    expect(state.view).toBe('drift');
    expect(state.workspaces[0]!.view).toBe('drift');
  });

  it('drops unusable entries and coerces an invalid stored view', async () => {
    globalThis.localStorage.setItem('mockserver-workspaces', JSON.stringify({
      activeWorkspaceId: 'nope',
      workspaces: [
        { id: 'a', name: 'Alpha', view: 'traffic' },
        { name: 'no id' },
        { id: 'a', name: 'duplicate id' },
        { id: 'c', name: 'Gamma', view: 'totally-bogus', logSearch: 42 },
      ],
    }));

    const store = await freshStore();
    const state = store.getState();

    expect(state.workspaces.map((w) => w.id)).toEqual(['a', 'c']);
    // Unknown activeWorkspaceId falls back to the first workspace.
    expect(state.activeWorkspaceId).toBe('a');
    // An unknown view is replaced by the new-workspace default, never persisted as-is.
    expect(state.workspaces[1]!.view).toBe('dashboard');
    expect(state.workspaces[1]!.logSearch).toBe('');
  });

  it('applies the legacy view migration inside a stored workspace', async () => {
    globalThis.localStorage.setItem('mockserver-workspaces', JSON.stringify({
      activeWorkspaceId: 'b',
      workspaces: [workspace('a', 'Alpha', { view: 'mcp-tools' as never }), workspace('b', 'Beta')],
    }));
    const store = await freshStore();
    expect(store.getState().workspaces[0]!.view).toBe('composer');
  });
});

describe('workspaces — per-workspace state isolation', () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    globalThis.location.hash = '';
    resetStore();
  });

  afterEach(() => {
    globalThis.localStorage.clear();
    globalThis.location.hash = '';
  });

  // The named failure mode for this feature: "a botched refactor silently resets
  // filter state on view switch". Guard it directly.
  it('switching view never clears the search terms', () => {
    const { setLogSearch, setTrafficSearch, setReceivedSearch, setView } = useDashboardStore.getState();
    setLogSearch('error');
    setTrafficSearch('status:500');
    setReceivedSearch('/api');

    setView('traffic');
    setView('metrics');
    setView('dashboard');

    const state = useDashboardStore.getState();
    expect(state.view).toBe('dashboard');
    expect(state.logSearch).toBe('error');
    expect(state.trafficSearch).toBe('status:500');
    expect(state.receivedSearch).toBe('/api');
  });

  it('keeps each workspace’s view and searches across a switch and back', () => {
    const store = useDashboardStore;
    store.getState().setLogSearch('first-workspace');
    store.getState().setView('traffic');
    const firstId = store.getState().activeWorkspaceId;

    store.getState().addWorkspace();
    const secondId = store.getState().activeWorkspaceId;
    expect(secondId).not.toBe(firstId);
    // A brand-new workspace starts clean — it must NOT inherit the other's filters.
    expect(store.getState().logSearch).toBe('');
    expect(store.getState().view).toBe('dashboard');

    store.getState().setLogSearch('second-workspace');
    store.getState().setView('chaos');

    store.getState().switchWorkspace(firstId);
    expect(store.getState().view).toBe('traffic');
    expect(store.getState().logSearch).toBe('first-workspace');

    store.getState().switchWorkspace(secondId);
    expect(store.getState().view).toBe('chaos');
    expect(store.getState().logSearch).toBe('second-workspace');
  });

  it('does not leak a search term from the active workspace into the others', () => {
    const store = useDashboardStore;
    const firstId = store.getState().activeWorkspaceId;
    store.getState().addWorkspace();
    store.getState().setTrafficSearch('only-here');

    const other = store.getState().workspaces.find((w) => w.id === firstId)!;
    expect(other.trafficSearch).toBe('');
  });

  // A server reset clears server data and the ACTIVE workspace's view/searches.
  // It must not delete the user's other workspaces, nor leave a stale snapshot
  // that a later switch would resurrect.
  it('a server reset clears only the active workspace, keeping the others', () => {
    const store = useDashboardStore;
    store.getState().setLogSearch('first');
    store.getState().setView('traffic');
    const firstId = store.getState().activeWorkspaceId;

    store.getState().addWorkspace();
    const secondId = store.getState().activeWorkspaceId;
    store.getState().setLogSearch('second');
    store.getState().setView('chaos');

    store.getState().clearUI();

    expect(store.getState().workspaces).toHaveLength(2);
    expect(store.getState().view).toBe('get-started');
    expect(store.getState().logSearch).toBe('');

    // The untouched workspace is intact...
    store.getState().switchWorkspace(firstId);
    expect(store.getState().view).toBe('traffic');
    expect(store.getState().logSearch).toBe('first');

    // ...and the reset workspace does not resurrect its pre-reset state.
    store.getState().switchWorkspace(secondId);
    expect(store.getState().view).toBe('get-started');
    expect(store.getState().logSearch).toBe('');
  });

  it('ignores a switch to the active workspace or to an unknown id', () => {
    const store = useDashboardStore;
    store.getState().setLogSearch('kept');
    const before = store.getState();

    store.getState().switchWorkspace(before.activeWorkspaceId);
    store.getState().switchWorkspace('does-not-exist');

    expect(store.getState().logSearch).toBe('kept');
    expect(store.getState().activeWorkspaceId).toBe(before.activeWorkspaceId);
    expect(store.getState().workspaces).toHaveLength(1);
  });
});

describe('workspaces — add, close, rename', () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    globalThis.location.hash = '';
    resetStore();
  });

  afterEach(() => {
    globalThis.localStorage.clear();
    globalThis.location.hash = '';
  });

  it('adds a uniquely named workspace and makes it active', () => {
    const store = useDashboardStore;
    store.getState().addWorkspace();
    store.getState().addWorkspace();

    const state = store.getState();
    expect(state.workspaces).toHaveLength(3);
    expect(new Set(state.workspaces.map((w) => w.id)).size).toBe(3);
    expect(new Set(state.workspaces.map((w) => w.name)).size).toBe(3);
    expect(state.activeWorkspaceId).toBe(state.workspaces[2]!.id);
  });

  it('closing the active workspace activates the left neighbour and loads its state', () => {
    const store = useDashboardStore;
    store.getState().setView('traffic');
    store.getState().setLogSearch('left');
    const firstId = store.getState().activeWorkspaceId;

    store.getState().addWorkspace();
    const secondId = store.getState().activeWorkspaceId;
    store.getState().setLogSearch('right');

    store.getState().closeWorkspace(secondId);

    const state = store.getState();
    expect(state.workspaces.map((w) => w.id)).toEqual([firstId]);
    expect(state.activeWorkspaceId).toBe(firstId);
    expect(state.view).toBe('traffic');
    expect(state.logSearch).toBe('left');
  });

  it('closing the first (active) workspace activates the new first workspace', () => {
    const store = useDashboardStore;
    const firstId = store.getState().activeWorkspaceId;
    store.getState().addWorkspace();
    store.getState().setLogSearch('survivor');
    const secondId = store.getState().activeWorkspaceId;

    store.getState().switchWorkspace(firstId);
    store.getState().closeWorkspace(firstId);

    const state = store.getState();
    expect(state.activeWorkspaceId).toBe(secondId);
    expect(state.logSearch).toBe('survivor');
  });

  it('closing an inactive workspace leaves the live state alone', () => {
    const store = useDashboardStore;
    const firstId = store.getState().activeWorkspaceId;
    store.getState().addWorkspace();
    store.getState().setView('chaos');
    store.getState().setLogSearch('untouched');
    const secondId = store.getState().activeWorkspaceId;

    store.getState().closeWorkspace(firstId);

    const state = store.getState();
    expect(state.activeWorkspaceId).toBe(secondId);
    expect(state.view).toBe('chaos');
    expect(state.logSearch).toBe('untouched');
    expect(state.workspaces).toHaveLength(1);
  });

  it('refuses to close the last workspace', () => {
    const store = useDashboardStore;
    const onlyId = store.getState().activeWorkspaceId;
    store.getState().closeWorkspace(onlyId);
    expect(store.getState().workspaces).toHaveLength(1);
    expect(store.getState().activeWorkspaceId).toBe(onlyId);
  });

  it('ignores closing an unknown workspace', () => {
    const store = useDashboardStore;
    store.getState().addWorkspace();
    store.getState().closeWorkspace('does-not-exist');
    expect(store.getState().workspaces).toHaveLength(2);
  });

  it('renames a workspace, trimming and truncating, and rejects a blank name', () => {
    const store = useDashboardStore;
    const id = store.getState().activeWorkspaceId;

    store.getState().renameWorkspace(id, '  Payments  ');
    expect(store.getState().workspaces[0]!.name).toBe('Payments');

    store.getState().renameWorkspace(id, '   ');
    expect(store.getState().workspaces[0]!.name).toBe('Payments');

    store.getState().renameWorkspace(id, 'x'.repeat(200));
    expect(store.getState().workspaces[0]!.name).toHaveLength(40);
  });
});

describe('workspaces — persistence', () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    globalThis.location.hash = '';
    resetStore();
  });

  afterEach(() => {
    globalThis.localStorage.clear();
    globalThis.location.hash = '';
  });

  it('persists the newly active workspace through the same view/search keys', () => {
    const store = useDashboardStore;
    store.getState().setView('traffic');
    store.getState().setLogSearch('alpha');
    const firstId = store.getState().activeWorkspaceId;

    store.getState().addWorkspace();
    store.getState().setLogSearch('beta');

    expect(globalThis.localStorage.getItem('mockserver-view')).toBe('dashboard');
    expect(JSON.parse(globalThis.localStorage.getItem('mockserver-search')!)).toMatchObject({ logSearch: 'beta' });

    store.getState().switchWorkspace(firstId);

    expect(globalThis.localStorage.getItem('mockserver-view')).toBe('traffic');
    expect(JSON.parse(globalThis.localStorage.getItem('mockserver-search')!)).toMatchObject({ logSearch: 'alpha' });
  });

  it('persists every workspace, including the outgoing one’s snapshot', () => {
    const store = useDashboardStore;
    store.getState().setView('traffic');
    store.getState().setTrafficSearch('status:500');
    const firstId = store.getState().activeWorkspaceId;

    store.getState().addWorkspace();

    const persisted = JSON.parse(globalThis.localStorage.getItem('mockserver-workspaces')!) as {
      activeWorkspaceId: string;
      workspaces: Workspace[];
    };
    expect(persisted.workspaces).toHaveLength(2);
    expect(persisted.activeWorkspaceId).toBe(store.getState().activeWorkspaceId);
    const outgoing = persisted.workspaces.find((w) => w.id === firstId)!;
    expect(outgoing).toMatchObject({ view: 'traffic', trafficSearch: 'status:500' });
  });

  it('a reload after a switch restores the same workspaces and live state', async () => {
    const store = useDashboardStore;
    store.getState().setView('traffic');
    store.getState().setLogSearch('alpha');
    store.getState().addWorkspace();
    store.getState().setView('chaos');
    store.getState().setLogSearch('beta');

    // Re-create the store module from what is on disk — i.e. a page reload.
    const reloaded = await freshStore();
    const state = reloaded.getState();

    expect(state.workspaces).toHaveLength(2);
    expect(state.view).toBe('chaos');
    expect(state.logSearch).toBe('beta');
    expect(state.workspaces[0]).toMatchObject({ view: 'traffic', logSearch: 'alpha' });
  });
});
