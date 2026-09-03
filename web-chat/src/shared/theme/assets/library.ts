// 素材库：IndexedDB 持久化 + blob URL 注册表 + asset:// 引用解析。
// 测试注入内存实现（同接口）。

export interface AssetRecord {
  key: string;
  path: string;
  kind: AssetKind;
  mimeType: string;
  sha256: string;
  byteSize: number;
  blob: Blob;
}

export type AssetKind = 'BITMAP' | 'NINE_SLICE' | 'FONT' | 'PATH';

export interface AssetLibrary {
  put(record: AssetRecord): Promise<void>;
  get(key: string): Promise<AssetRecord | null>;
  list(): Promise<AssetRecord[]>;
  delete(key: string): Promise<boolean>;
  /** asset://key → blob URL（库内缓存的 URL 由库管理，删除时 revoke） */
  resolveUrl(uri: string): Promise<string | null>;
  has(key: string): Promise<boolean>;
}

export function parseAssetUri(uri: string): string | null {
  const prefix = 'asset://';
  if (!uri.startsWith(prefix)) {
    return null;
  }
  const key = uri.slice(prefix.length);
  return key.length > 0 ? key : null;
}

export function makeAssetUri(key: string): string {
  return `asset://${key}`;
}

const DB_NAME = 'operit-studio-assets';
const DB_VERSION = 1;
const STORE_NAME = 'assets';

export function createBrowserAssetLibrary(): AssetLibrary {
  let database: IDBDatabase | null = null;
  const urlCache = new Map<string, string>();

  async function openDb(): Promise<IDBDatabase> {
    if (database !== null) {
      return database;
    }
    const opened = await new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME, { keyPath: 'key' });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    database = opened;
    return opened;
  }

  function storeRequest<T>(request: IDBRequest<T>): Promise<T> {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  return {
    async put(record: AssetRecord) {
      const store = (await openDb()).transaction(STORE_NAME, 'readwrite').objectStore(STORE_NAME);
      await storeRequest(
        store.put(record as unknown as Record<string, unknown>)
      );
    },
    async get(key: string) {
      const store = (await openDb()).transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME);
      const result = await storeRequest(store.get(key));
      return (result as AssetRecord | undefined) ?? null;
    },
    async list() {
      const store = (await openDb()).transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME);
      const request = store.getAll();
      return (await storeRequest(request)) as AssetRecord[];
    },
    async delete(key: string) {
      const existing = await this.get(key);
      if (existing == null) {
        return false;
      }
      const store = (await openDb()).transaction(STORE_NAME, 'readwrite').objectStore(STORE_NAME);
      await storeRequest(store.delete(key));
      const url = urlCache.get(key);
      if (url != null) {
        URL.revokeObjectURL(url);
        urlCache.delete(key);
      }
      return true;
    },
    async resolveUrl(uri: string) {
      const key = parseAssetUri(uri);
      if (key == null) {
        return null;
      }
      const cached = urlCache.get(key);
      if (cached != null) {
        return cached;
      }
      const record = await this.get(key);
      if (record == null) {
        return null;
      }
      const url = URL.createObjectURL(record.blob);
      urlCache.set(key, url);
      return url;
    },
    async has(key: string) {
      return (await this.get(key)) != null;
    }
  };
}

export function createMemoryAssetLibrary(): AssetLibrary {
  const records = new Map<string, AssetRecord>();
  const urlCache = new Map<string, string>();

  return {
    async put(record: AssetRecord) {
      records.set(record.key, record);
    },
    async get(key: string) {
      return records.get(key) ?? null;
    },
    async list() {
      return Array.from(records.values());
    },
    async delete(key: string) {
      const existed = records.delete(key);
      const url = urlCache.get(key);
      if (url != null && typeof URL.revokeObjectURL === 'function') {
        URL.revokeObjectURL(url);
      }
      urlCache.delete(key);
      return existed;
    },
    async resolveUrl(uri: string) {
      const key = parseAssetUri(uri);
      if (key == null) {
        return null;
      }
      const cached = urlCache.get(key);
      if (cached != null) {
        return cached;
      }
      const record = records.get(key);
      if (record == null) {
        return null;
      }
      const url = typeof URL.createObjectURL === 'function' ? URL.createObjectURL(record.blob) : 'memory://';
      urlCache.set(key, url);
      return url;
    },
    async has(key: string) {
      return records.has(key);
    }
  };
}
