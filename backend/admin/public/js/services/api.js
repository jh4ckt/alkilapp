// ==========================================================================
// AlkilApp Admin - API Service
// ==========================================================================

class ApiError extends Error {
  constructor(status, message, data) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

class ApiClient {
  constructor(baseUrl = '/api') {
    this.baseUrl = baseUrl;
    this.cache = new Map();
    this.cacheTTL = 30000; // 30 seconds
  }

  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;
    const config = {
      headers: { 'Content-Type': 'application/json', ...options.headers },
      credentials: 'include',
      ...options,
    };

    if (config.body && !(config.body instanceof FormData)) {
      config.body = JSON.stringify(config.body);
    }

    try {
      const response = await fetch(url, config);

      if (response.status === 401) {
        window.location.href = '/login';
        throw new ApiError(401, 'No autenticado');
      }

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new ApiError(response.status, data.error || 'Error en la petición', data);
      }

      return data;
    } catch (e) {
      if (e instanceof ApiError) throw e;
      throw new ApiError(0, 'Error de conexión: ' + e.message);
    }
  }

  get(endpoint, params = {}) {
    const url = new URL(endpoint, window.location.origin);
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
    });
    return this.request(url.pathname + url.search);
  }

  post(endpoint, body) {
    return this.request(endpoint, { method: 'POST', body });
  }

  put(endpoint, body) {
    return this.request(endpoint, { method: 'PUT', body });
  }

  delete(endpoint) {
    return this.request(endpoint, { method: 'DELETE' });
  }

  // Cache helpers
  async getCached(endpoint, params = {}) {
    const key = endpoint + JSON.stringify(params);
    const cached = this.cache.get(key);
    if (cached && Date.now() - cached.time < this.cacheTTL) {
      return cached.data;
    }
    const data = await this.get(endpoint, params);
    this.cache.set(key, { data, time: Date.now() });
    return data;
  }

  invalidate(endpoint) {
    const keysToDelete = [];
    this.cache.forEach((_, key) => {
      if (key.startsWith(endpoint)) keysToDelete.push(key);
    });
    keysToDelete.forEach(k => this.cache.delete(k));
  }

  clearCache() { this.cache.clear(); }
}

const api = new ApiClient();

// Export for modules
export { api, ApiError };