// .toolpkg（ZIP）的浏览器读取器：解析 central directory 定位条目，
// deflate 条目用 DecompressionStream('deflate-raw') 解压。
// 仅支持读取，覆盖工具链产出的 stored 与 deflate 两种常见压缩

export interface ToolpkgFile {
  name: string;
  bytes: Uint8Array;
}

const EOCD_SIGNATURE = 0x06054b50;
const CENTRAL_HEADER_SIGNATURE = 0x02014b50;
const LOCAL_HEADER_SIGNATURE = 0x04034b50;

async function inflateRaw(bytes: Uint8Array): Promise<Uint8Array> {
  const stream = new Blob([bytes as BlobPart]).stream().pipeThrough(
    new DecompressionStream('deflate-raw')
  );
  const buffer = await new Response(stream).arrayBuffer();
  return new Uint8Array(buffer);
}

function readU16(view: DataView, offset: number) {
  return view.getUint16(offset, true);
}

function readU32(view: DataView, offset: number) {
  return view.getUint32(offset, true);
}

async function readEntryAt(
  buffer: ArrayBuffer,
  localHeaderOffset: number,
  compressedSize: number,
  compressionMethod: number
): Promise<Uint8Array> {
  const view = new DataView(buffer);
  if (readU32(view, localHeaderOffset) !== LOCAL_HEADER_SIGNATURE) {
    throw new Error('toolpkg: 无效的 ZIP 本地文件头');
  }
  const fileNameLength = readU16(view, localHeaderOffset + 26);
  const extraLength = readU16(view, localHeaderOffset + 28);
  const dataOffset = localHeaderOffset + 30 + fileNameLength + extraLength;
  const compressed = new Uint8Array(buffer, dataOffset, compressedSize);
  if (compressionMethod === 0) {
    return compressed;
  }
  if (compressionMethod === 8) {
    return inflateRaw(compressed);
  }
  throw new Error(`toolpkg: 不支持的压缩方法 ${compressionMethod}`);
}

export async function readToolpkgZip(file: File): Promise<Map<string, Uint8Array>> {
  const buffer = await file.arrayBuffer();
  const view = new DataView(buffer);
  const tailStart = Math.max(0, buffer.byteLength - 66_000);
  let eocdOffset = -1;
  for (let offset = buffer.byteLength - 22; offset >= tailStart; offset -= 1) {
    if (readU32(view, offset) === EOCD_SIGNATURE) {
      eocdOffset = offset;
      break;
    }
  }
  if (eocdOffset < 0) {
    throw new Error('toolpkg: 未找到 ZIP 结束记录');
  }
  const entryCount = readU16(view, eocdOffset + 10);
  let cursor = readU32(view, eocdOffset + 16);

  const files = new Map<string, Uint8Array>();
  for (let index = 0; index < entryCount; index += 1) {
    if (readU32(view, cursor) !== CENTRAL_HEADER_SIGNATURE) {
      throw new Error('toolpkg: 无效的 ZIP 中央目录条目');
    }
    const compressionMethod = readU16(view, cursor + 10);
    const compressedSize = readU32(view, cursor + 20);
    const nameLength = readU16(view, cursor + 28);
    const extraLength = readU16(view, cursor + 30);
    const commentLength = readU16(view, cursor + 32);
    const localHeaderOffset = readU32(view, cursor + 42);
    const name = new TextDecoder().decode(
      new Uint8Array(buffer, cursor + 46, nameLength)
    );
    if (!name.endsWith('/')) {
      files.set(name, await readEntryAt(buffer, localHeaderOffset, compressedSize, compressionMethod));
    }
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return files;
}

export function findFileBySuffix(files: Map<string, Uint8Array>, suffix: string) {
  for (const [name, bytes] of files) {
    if (name === suffix || name.endsWith(`/${suffix}`)) {
      return { name, bytes };
    }
  }
  return null;
}
