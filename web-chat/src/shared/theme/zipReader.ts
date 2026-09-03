// .otheme（ZIP）浏览器读取器：解析 EOCD/central directory，读取条目与 ZIP comment。
// 相比 toolpkgLoader 增加：comment-length 尊重、条目边界校验、entry 预算、路径安全。

export interface ThemeZipEntryInfo {
  name: string;
  compressedSize: number;
  uncompressedSize: number;
  compressionMethod: number;
}

export interface ThemeZipContents {
  comment: string;
  entries: Map<string, Uint8Array>;
  entryInfos: ThemeZipEntryInfo[];
  archiveBytes: number;
}

const EOCD_SIGNATURE = 0x06054b50;
const EOCD_MIN_SIZE = 22;
const CENTRAL_HEADER_SIGNATURE = 0x02014b50;
const LOCAL_HEADER_SIGNATURE = 0x04034b50;

export const THEME_ZIP_LIMITS = {
  maxEntries: 512,
  maxArchiveBytes: 128 * 1024 * 1024,
  maxSingleEntryBytes: 48 * 1024 * 1024,
  maxUncompressedBytes: 64 * 1024 * 1024,
  maxCompressionRatio: 100
} as const;

async function inflateRaw(bytes: Uint8Array): Promise<Uint8Array> {
  const stream = new Blob([bytes as BlobPart])
    .stream()
    .pipeThrough(new DecompressionStream('deflate-raw'));
  const buffer = await new Response(stream).arrayBuffer();
  return new Uint8Array(buffer);
}

function readU16(view: DataView, offset: number): number {
  return view.getUint16(offset, true);
}

function readU32(view: DataView, offset: number): number {
  return view.getUint32(offset, true);
}

function findEocd(view: DataView): { offset: number; commentLength: number } {
  const tailStart = Math.max(0, view.byteLength - 66_000);
  for (let offset = view.byteLength - EOCD_MIN_SIZE; offset >= tailStart; offset -= 1) {
    if (readU32(view, offset) !== EOCD_SIGNATURE) {
      continue;
    }
    const commentLength = readU16(view, offset + 20);
    // EOCD 之后只允许恰好 commentLength 字节，防止把随机数据误判为 comment
    if (offset + EOCD_MIN_SIZE + commentLength !== view.byteLength) {
      continue;
    }
    return { offset, commentLength };
  }
  throw new Error('otheme: 未找到合法的 ZIP 结束记录');
}

export function isPortableRelativePath(path: string): boolean {
  if (path.length === 0 || path.startsWith('/') || path.includes('\\') || path.includes(':')) {
    return false;
  }
  return !path.split('/').some((segment) => segment === '..' || segment.length === 0);
}

export async function readThemeZip(bytes: Uint8Array): Promise<ThemeZipContents> {
  if (bytes.byteLength === 0 || bytes.byteLength > THEME_ZIP_LIMITS.maxArchiveBytes) {
    throw new Error(`otheme: archive 大小超出限制（≤${THEME_ZIP_LIMITS.maxArchiveBytes} 字节）`);
  }
  const buffer = bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength
  ) as ArrayBuffer;
  const view = new DataView(buffer);
  const { offset: eocdOffset, commentLength } = findEocd(view);
  const comment = new TextDecoder().decode(
    new Uint8Array(buffer, eocdOffset + EOCD_MIN_SIZE, commentLength)
  );
  const entryCount = readU16(view, eocdOffset + 10);
  if (entryCount > THEME_ZIP_LIMITS.maxEntries) {
    throw new Error(`otheme: 条目数超出限制（≤${THEME_ZIP_LIMITS.maxEntries}）`);
  }
  const centralStart = readU32(view, eocdOffset + 16);
  const centralSize = readU32(view, eocdOffset + 12);
  if (centralStart + centralSize > eocdOffset) {
    throw new Error('otheme: 中央目录越界');
  }

  const entries = new Map<string, Uint8Array>();
  const entryInfos: ThemeZipEntryInfo[] = [];
  let cursor = centralStart;
  let totalUncompressed = 0;

  for (let index = 0; index < entryCount; index += 1) {
    if (cursor + 46 > eocdOffset || readU32(view, cursor) !== CENTRAL_HEADER_SIGNATURE) {
      throw new Error('otheme: 无效的 ZIP 中央目录条目');
    }
    const compressionMethod = readU16(view, cursor + 10);
    const compressedSize = readU32(view, cursor + 20);
    const uncompressedSize = readU32(view, cursor + 24);
    const nameLength = readU16(view, cursor + 28);
    const extraLength = readU16(view, cursor + 30);
    const entryCommentLength = readU16(view, cursor + 32);
    const localHeaderOffset = readU32(view, cursor + 42);
    const name = new TextDecoder().decode(new Uint8Array(buffer, cursor + 46, nameLength));

    if (uncompressedSize > THEME_ZIP_LIMITS.maxSingleEntryBytes) {
      throw new Error(`otheme: 条目 ${name} 解压后超出单条目限制`);
    }
    if (compressionMethod === 8 && uncompressedSize > compressedSize * THEME_ZIP_LIMITS.maxCompressionRatio) {
      throw new Error(`otheme: 条目 ${name} 压缩比超出限制`);
    }
    totalUncompressed += uncompressedSize;
    if (totalUncompressed > THEME_ZIP_LIMITS.maxUncompressedBytes) {
      throw new Error('otheme: 解压总量超出限制');
    }

    if (!name.endsWith('/')) {
      if (!isPortableRelativePath(name)) {
        throw new Error(`otheme: 条目路径不安全: ${name}`);
      }
      entries.set(name, await readEntry(buffer, localHeaderOffset, compressedSize, compressionMethod));
    }
    entryInfos.push({ name, compressedSize, uncompressedSize, compressionMethod });
    cursor += 46 + nameLength + extraLength + entryCommentLength;
  }

  return {
    comment,
    entries,
    entryInfos,
    archiveBytes: bytes.byteLength
  };
}

async function readEntry(
  buffer: ArrayBuffer,
  localHeaderOffset: number,
  compressedSize: number,
  compressionMethod: number
): Promise<Uint8Array> {
  const view = new DataView(buffer);
  if (readU32(view, localHeaderOffset) !== LOCAL_HEADER_SIGNATURE) {
    throw new Error('otheme: 无效的 ZIP 本地文件头');
  }
  const fileNameLength = readU16(view, localHeaderOffset + 26);
  const extraLength = readU16(view, localHeaderOffset + 28);
  const dataOffset = localHeaderOffset + 30 + fileNameLength + extraLength;
  if (dataOffset + compressedSize > view.byteLength) {
    throw new Error('otheme: 条目数据越界');
  }
  const compressed = new Uint8Array(buffer, dataOffset, compressedSize);
  if (compressionMethod === 0) {
    return compressed;
  }
  if (compressionMethod === 8) {
    return inflateRaw(compressed);
  }
  throw new Error(`otheme: 不支持的压缩方法 ${compressionMethod}`);
}
