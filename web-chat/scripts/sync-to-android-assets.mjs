import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, '..');
const distDir = resolve(projectRoot, 'dist');
const assetDir = resolve(projectRoot, '..', 'app', 'src', 'main', 'assets', 'web-chat');

// 预览站产物不进 APK：preview.html 与其入口 chunk/样式只服务于
// 本机 docker 部署，真机静态服务不暴露预览入口
const PREVIEW_EXCLUDE = [/^preview\.html$/i, /^preview-.*\.js$/i, /^preview-.*\.css$/i];

function isExcluded(relativePath) {
  const fileName = relativePath.split(/[\\/]/).pop() ?? relativePath;
  return PREVIEW_EXCLUDE.some((pattern) => pattern.test(fileName));
}

function copyDistTree(sourceDir, targetDir, baseDir) {
  mkdirSync(targetDir, { recursive: true });
  for (const entry of readdirSync(sourceDir)) {
    const sourcePath = join(sourceDir, entry);
    const relativePath = relative(baseDir, sourcePath);
    if (isExcluded(relativePath)) {
      excluded += 1;
      continue;
    }
    const targetPath = join(targetDir, entry);
    if (statSync(sourcePath).isDirectory()) {
      copyDistTree(sourcePath, targetPath, baseDir);
    } else {
      cpSync(sourcePath, targetPath, { force: true });
    }
  }
}

let excluded = 0;

if (!existsSync(distDir)) {
  console.error('Missing web-chat/dist. Run `npm --prefix web-chat run build` first.');
  process.exit(1);
}

rmSync(assetDir, { recursive: true, force: true });
mkdirSync(assetDir, { recursive: true });
copyDistTree(distDir, assetDir, distDir);
console.log(`Synced web-chat dist to android assets (excluded ${excluded} preview entries).`);
