export function resolveApiBase(value = '') {
  const base = value.trim().replace(/\/+$/, '') || '/api';
  if (!base.startsWith('/') && !/^https?:\/\//.test(base)) throw new Error('VITE_API_BASE_URL must be an absolute HTTP(S) URL or a root-relative path.');
  if (base.startsWith('//') || /[?#]/.test(base)) throw new Error('API base cannot contain a query, fragment or protocol-relative URL.');
  return base;
}
