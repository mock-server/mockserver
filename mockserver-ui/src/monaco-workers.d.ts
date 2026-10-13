// Ambient types for the Vite `?worker` imports in monacoSetup.ts. Vite
// resolves a `?worker` import to the default export of a
// zero-argument Worker subclass constructor; declare that shape so the strict
// TypeScript build (which has no `vite/client` types referenced) can type them.
declare module '*?worker' {
  const WorkerFactory: { new (): Worker };
  export default WorkerFactory;
}
