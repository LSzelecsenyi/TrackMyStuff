export const environment = {
  production: true,
  apiBaseUrl: '/api',
  /**
   * Public Google web client id. Must match the backend STRICT_GOOGLE_CLIENT_ID audience.
   * CI writes it with admin/scripts/write-google-client-id.mjs. Leave this empty in git.
   */
  googleClientId: '',
};
