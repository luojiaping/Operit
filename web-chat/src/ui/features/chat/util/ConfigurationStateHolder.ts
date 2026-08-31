const TOKEN_KEY = 'operit-web-chat-token';
const TOKEN_HASH_PREFIX = '#token=';

export function readStoredToken() {
  if (typeof window === 'undefined') {
    return '';
  }
  return window.localStorage.getItem(TOKEN_KEY) ?? '';
}

export function writeStoredToken(token: string) {
  if (typeof window === 'undefined') {
    return;
  }

  if (!token.trim()) {
    window.localStorage.removeItem(TOKEN_KEY);
    return;
  }

  window.localStorage.setItem(TOKEN_KEY, token.trim());
}

export function clearStoredToken() {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.removeItem(TOKEN_KEY);
}

// 从地址 hash（#token=xxx）读取 Token：hash 不会被发送到服务器，
// 不进反代访问日志；读取后立即写入存储并从地址栏清除
export function readTokenFromLocationHash() {
  if (typeof window === 'undefined') {
    return '';
  }
  const hash = window.location.hash;
  if (!hash.startsWith(TOKEN_HASH_PREFIX)) {
    return '';
  }
  const token = decodeURIComponent(hash.slice(TOKEN_HASH_PREFIX.length)).trim();
  if (!token) {
    return '';
  }
  writeStoredToken(token);
  window.history.replaceState(null, '', window.location.pathname + window.location.search);
  return token;
}
