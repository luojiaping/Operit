// store（不压缩）ZIP 写入器：主题插件包的负载是文本与已压缩资源，
// store 方式与 DreamSkin Studio 的做法一致且实现可审计

export interface ZipEntry {
  name: string;
  bytes: Uint8Array;
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(bytes: Uint8Array) {
  let crc = 0xffffffff;
  for (let index = 0; index < bytes.length; index += 1) {
    crc = CRC_TABLE[(crc ^ bytes[index]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

// 拷贝构造把 Uint8Array<ArrayBufferLike> 收窄成 Uint8Array<ArrayBuffer>，
// 满足 BlobPart 的类型要求
function asPart(bytes: Uint8Array): Uint8Array<ArrayBuffer> {
  return new Uint8Array(bytes);
}

function writeU16(view: DataView, offset: number, value: number) {
  view.setUint16(offset, value, true);
}

function writeU32(view: DataView, offset: number, value: number) {
  view.setUint32(offset, value, true);
}

export function createStoredZip(entries: ZipEntry[], comment = ''): Blob {
  const encoder = new TextEncoder();
  const parts: BlobPart[] = [];
  const centralRecords: { name: Uint8Array; crc: number; size: number; offset: number }[] = [];
  let offset = 0;

  for (const entry of entries) {
    const name = encoder.encode(entry.name);
    const crc = crc32(entry.bytes);
    const localHeaderBuffer = new ArrayBuffer(30);
    const localHeader = new DataView(localHeaderBuffer);
    writeU32(localHeader, 0, 0x04034b50);
    writeU16(localHeader, 4, 20);
    writeU16(localHeader, 6, 0x0800);
    writeU16(localHeader, 8, 0);
    writeU16(localHeader, 10, 0);
    writeU16(localHeader, 12, 0x21);
    writeU32(localHeader, 14, crc);
    writeU32(localHeader, 18, entry.bytes.length);
    writeU32(localHeader, 22, entry.bytes.length);
    writeU16(localHeader, 26, name.length);
    writeU16(localHeader, 28, 0);

    centralRecords.push({ name, crc, size: entry.bytes.length, offset });
    parts.push(new Uint8Array(localHeaderBuffer), asPart(name), asPart(entry.bytes));
    offset += 30 + name.length + entry.bytes.length;
  }

  const centralStart = offset;
  for (const record of centralRecords) {
    const headerBuffer = new ArrayBuffer(46);
    const header = new DataView(headerBuffer);
    writeU32(header, 0, 0x02014b50);
    writeU16(header, 4, 20);
    writeU16(header, 6, 20);
    writeU16(header, 8, 0x0800);
    writeU16(header, 10, 0);
    writeU16(header, 12, 0);
    writeU16(header, 14, 0x21);
    writeU32(header, 16, record.crc);
    writeU32(header, 20, record.size);
    writeU32(header, 24, record.size);
    writeU16(header, 28, record.name.length);
    writeU16(header, 30, 0);
    writeU16(header, 32, 0);
    writeU16(header, 34, 0);
    writeU16(header, 36, 0);
    writeU32(header, 38, 0);
    writeU32(header, 42, record.offset);
    parts.push(new Uint8Array(headerBuffer), asPart(record.name));
    offset += 46 + record.name.length;
  }

  const eocdBuffer = new ArrayBuffer(22);
  const eocd = new DataView(eocdBuffer);
  writeU32(eocd, 0, 0x06054b50);
  writeU16(eocd, 4, 0);
  writeU16(eocd, 6, 0);
  writeU16(eocd, 8, centralRecords.length);
  writeU16(eocd, 10, centralRecords.length);
  writeU32(eocd, 12, offset - centralStart);
  writeU32(eocd, 16, centralStart);
  writeU16(eocd, 20, comment.length);
  parts.push(new Uint8Array(eocdBuffer));
  if (comment.length > 0) {
    parts.push(asPart(encoder.encode(comment)));
  }

  return new Blob(parts, { type: 'application/zip' });
}
