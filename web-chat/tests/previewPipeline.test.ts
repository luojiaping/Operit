import { describe, expect, it } from 'vitest';
import { createStoredZip } from '../src/preview/zipWriter';
import { findFileBySuffix, readToolpkgZip } from '../src/preview/toolpkgLoader';
import { runToolPkgMain, resolveSlotContent } from '../src/preview/slotRunner';
import { SLOT_TEMPLATES, buildToolpkgSources } from '../src/preview/templates';

function fileFromBytes(bytes: Uint8Array) {
  return new File([new Uint8Array(bytes)], 'test.toolpkg', { type: 'application/zip' });
}

describe('zipWriter 与 toolpkgLoader 闭环', () => {
  it('store zip 写入后能按名字读回全部条目', async () => {
    const encoder = new TextEncoder();
    const blob = createStoredZip([
      { name: 'manifest.json', bytes: encoder.encode('{"main":"dist/main.js"}') },
      { name: 'dist/main.js', bytes: encoder.encode('// main') },
      { name: 'dist/ui/preview/index.ui.js', bytes: encoder.encode('// screen') }
    ]);
    const files = await readToolpkgZip(fileFromBytes(new Uint8Array(await blob.arrayBuffer())));
    expect(files.size).toBe(3);
    expect(new TextDecoder().decode(files.get('manifest.json') ?? new Uint8Array())).toBe(
      '{"main":"dist/main.js"}'
    );
    const bySuffix = findFileBySuffix(files, 'index.ui.js');
    expect(bySuffix?.name).toBe('dist/ui/preview/index.ui.js');
  });

  it('deflate 条目经 DecompressionStream 解压读回', async () => {
    const compressed = new Response(
      new Blob(['plain text payload']).stream().pipeThrough(
        new CompressionStream('deflate-raw')
      )
    ).arrayBuffer();
    const deflated = new Uint8Array(await compressed);
    const encoder = new TextEncoder();
    const blob = createStoredZipWithDeflate([
      { name: 'entry.txt', bytes: encoder.encode('plain text payload') },
      { name: 'deflated.bin', bytes: deflated, method: 8 }
    ]);
    const files = await readToolpkgZip(fileFromBytes(new Uint8Array(await blob.arrayBuffer())));
    expect(new TextDecoder().decode(files.get('deflated.bin') ?? new Uint8Array())).toBe(
      'plain text payload'
    );
  });
});

// 构造含 deflate 方法标记的 zip（复用 writer 结构但把方法位写成 8）
function createStoredZipWithDeflate(entries: { name: string; bytes: Uint8Array; method?: number }[]) {
  const encoder = new TextEncoder();
  const parts: BlobPart[] = [];
  const records: { name: Uint8Array; size: number; offset: number; method: number }[] = [];
  let offset = 0;
  const u16 = (v: number) => {
    const b = new ArrayBuffer(2);
    new DataView(b).setUint16(0, v, true);
    return new Uint8Array(b);
  };
  const u32 = (v: number) => {
    const b = new ArrayBuffer(4);
    new DataView(b).setUint32(0, v, true);
    return new Uint8Array(b);
  };
  for (const entry of entries) {
    const method = entry.method ?? 0;
    const name = encoder.encode(entry.name);
    const local = [
      u32(0x04034b50),
      u16(20),
      u16(0x0800),
      u16(method),
      u16(0),
      u16(0x21),
      u32(0),
      u32(entry.bytes.length),
      u32(entry.bytes.length),
      u16(name.length),
      u16(0)
    ];
    records.push({ name, size: entry.bytes.length, offset, method });
    parts.push(...local, new Uint8Array(name), entry.bytes);
    const localLength = local.reduce((sum, part) => sum + part.length, 0);
    offset += localLength + name.length + entry.bytes.length;
  }
  const centralStart = offset;
  for (const record of records) {
    const central = [
      u32(0x02014b50),
      u16(20),
      u16(20),
      u16(0x0800),
      u16(record.method),
      u16(0),
      u16(0x21),
      u32(0),
      u32(record.size),
      u32(record.size),
      u16(record.name.length),
      u16(0),
      u16(0),
      u16(0),
      u16(0),
      u32(0),
      u32(record.offset)
    ];
    parts.push(...central, new Uint8Array(record.name));
    offset += central.reduce((sum, part) => sum + part.length, 0) + record.name.length;
  }
  const eocd = [
    u32(0x06054b50),
    u16(0),
    u16(0),
    u16(records.length),
    u16(records.length),
    u32(offset - centralStart),
    u32(centralStart),
    u16(0)
  ];
  parts.push(...eocd);
  return new Blob(parts, { type: 'application/zip' });
}

describe('slotRunner 契约解析', () => {
  const MAIN = `exports.registerToolPkg = function () {
    ToolPkg.registerInputSlotPlugin({
      id: "t1", slot: "above_input",
      function: function () { return { handled: true, text: "hi" }; }
    });
    ToolPkg.registerInputSlotPlugin({
      id: "t2", slot: "input_drawer",
      function: function () { return "drawer"; }
    });
    return true;
  };`;

  it('收集 registerInputSlotPlugin 注册', () => {
    const registrations = runToolPkgMain(MAIN);
    expect(registrations.map((item) => item.id)).toEqual(['t1', 't2']);
  });

  it('缺少 registerToolPkg 导出时报显式错误', () => {
    expect(() => runToolPkgMain('exports.x = 1;')).toThrow('registerToolPkg');
  });

  it('text / 字符串 / handled:false 三种返回形态解析正确', () => {
    expect(resolveSlotContent('直接字符串', () => '')).toEqual({ kind: 'text', text: '直接字符串' });
    expect(resolveSlotContent({ handled: false }, () => '')).toBeNull();
    expect(resolveSlotContent({ handled: true, text: 'ok' }, () => '')).toEqual({
      kind: 'text',
      text: 'ok'
    });
  });

  it('composeDsl 返回经 screen 解析器取得源码与 state', () => {
    const content = resolveSlotContent(
      { handled: true, composeDsl: { screen: 'dist/ui/x.js', state: { a: 1 } } },
      (path) => `screen:${path}`
    );
    expect(content).toEqual({ kind: 'dsl', screenSource: 'screen:dist/ui/x.js', state: { a: 1 } });
  });
});

describe('模板生成物', () => {
  it('生成包的 main.js 可被 slotRunner 执行且 screen 可运行时执行', () => {
    const template = SLOT_TEMPLATES[0];
    const sources = buildToolpkgSources({
      template,
      slot: 'above_input',
      pluginId: 'test_gen',
      displayName: '测试',
      state: template.buildState({ title: 'T', subtitle: 'S', showProgress: true, progress: 60 })
    });
    const registrations = runToolPkgMain(sources.mainSource);
    expect(registrations).toHaveLength(1);
    expect(registrations[0].slot).toBe('above_input');
    const content = resolveSlotContent(
      registrations[0].render({ eventPayload: {} }),
      () => sources.screenSource
    );
    expect(content?.kind).toBe('dsl');
    // 执行生成的 screen（提供最小 ctx），应返回状态卡 Card 节点
    const ctx = {
      UI: new Proxy(
        {},
        {
          get: (_target, type: string) => (props: unknown, children: unknown) => ({
            type,
            props: props ?? {},
            children: Array.isArray(children) ? children : children ? [children] : []
          })
        }
      ) as Record<string, (props?: unknown, children?: unknown) => unknown>,
      useState: (key: string, initial: unknown) => [
        { title: 'T', subtitle: 'S', accent: '#8ca9ff', showProgress: true, progress: 0.6 }[
          key
        ] ?? initial,
        () => {}
      ]
    };
    const exports_: Record<string, unknown> = {};
    new Function('ctx', 'exports', sources.screenSource)(ctx, exports_);
    const node = (exports_.default as (c: unknown) => { type: string })(ctx);
    expect(node.type).toBe('Card');
  });

  it('manifest 与 demo 包同构', () => {
    const template = SLOT_TEMPLATES[2];
    const sources = buildToolpkgSources({
      template,
      slot: 'input_drawer',
      pluginId: 'note_test',
      displayName: '记事板',
      state: template.buildState({})
    });
    const manifest = JSON.parse(sources.manifestJson);
    expect(manifest.schema_version).toBe(1);
    expect(manifest.main).toBe('dist/main.js');
    expect(manifest.display_name.zh).toBe('记事板');
  });
});
