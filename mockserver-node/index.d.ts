export interface StartServerOptions {
  serverPort: number;
  jvmOptions?: string[] | string;
  artifactoryHost?: string;
  artifactoryPath?: string;
  mockServerVersion?: string;
  initializationJsonPath?: string;
  trace?: boolean;
  verbose?: boolean;
  startupRetries?: number;
  javaDebugPort?: number;
  proxyRemotePort?: number;
  proxyRemoteHost?: string;
  runForked?: boolean;
}

export interface StopServerOptions {
  serverPort: number;
  verbose?: boolean;
}

declare const mockserverNode: {
  start_mockserver: (options: StartServerOptions) => Promise<void>,
  stop_mockserver: (options: StopServerOptions) => Promise<void>,
};

export default mockserverNode;
