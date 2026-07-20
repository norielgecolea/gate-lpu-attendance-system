const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';
const contextPath = process.env.BACKEND_CONTEXT_PATH || '/attendance-system';

module.exports = {
  [`${contextPath}/api`]: {
    target: backendUrl,
    secure: false,
    changeOrigin: true,
  },
  [`${contextPath}/ws`]: {
    target: backendUrl,
    secure: false,
    changeOrigin: true,
    ws: true,
  },
  [`${contextPath}/pictures`]: {
    target: backendUrl,
    secure: false,
    changeOrigin: true,
  },
};
