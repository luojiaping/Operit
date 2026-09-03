// ARGB/web 颜色互转：与 app 侧 0xAARRGGBB 语义一致（alpha 恒 0xFF 的 UI 展示）。

export function argbToHex(argb: number): string {
  const value = (argb & 0xffffffff) >>> 0;
  const hex = value.toString(16).padStart(8, '0');
  // 面板取色器只处理不透明色，去掉 alpha 前导
  return `#${hex.slice(2)}`;
}

export function hexToArgb(hex: string): number {
  const normalized = hex.replace('#', '');
  const value = Number.parseInt(normalized.padStart(6, '0').slice(0, 6), 16);
  if (Number.isNaN(value)) {
    throw new Error(`无法解析颜色: ${hex}`);
  }
  return 0xff000000 | value;
}

export function argbAlpha(argb: number): number {
  return (argb >>> 24) & 0xff;
}
