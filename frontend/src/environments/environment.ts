const contextPath = '/attendance-system';

export const environment = {
  production: false,
  contextPath,
  apiBaseUrl: `${contextPath}/api`,
  wsUrl: `${typeof window !== 'undefined' ? window.location.origin.replace(/^http/, 'ws') : 'ws://localhost:4200'}${contextPath}/ws/notifications`,
};
