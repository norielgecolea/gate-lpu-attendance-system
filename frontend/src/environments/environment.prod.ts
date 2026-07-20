const contextPath = '/attendance-system';

export const environment = {
  production: true,
  contextPath,
  apiBaseUrl: `${contextPath}/api`,
  wsUrl: `${typeof window !== 'undefined' ? window.location.origin.replace(/^http/, 'ws') : ''}${contextPath}/ws/notifications`,
};
