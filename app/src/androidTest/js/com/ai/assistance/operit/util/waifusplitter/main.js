'use strict';

const { assert, assertEq, test, runTests } = require('../../../../../../lib/harness');

function getWaifuMessageProcessorInstance() {
  return Java.type('com.ai.assistance.operit.util.WaifuMessageProcessor').INSTANCE;
}

function getStreamingSessionClass() {
  return Java.type('com.ai.assistance.operit.util.WaifuMessageProcessor$StreamingSession');
}

function normalizeList(value) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item));
  }
  if (value == null) {
    return [];
  }
  if (typeof value.size === 'function' && typeof value.get === 'function') {
    const size = Number(value.size());
    const out = [];
    for (let i = 0; i < size; i += 1) {
      out.push(String(value.get(i)));
    }
    return out;
  }
  return [String(value)];
}

function assertListEq(actual, expected, message) {
  assertEq(JSON.stringify(normalizeList(actual)), JSON.stringify(expected), message);
}

function createKotlinAdapter() {
  const processor = getWaifuMessageProcessorInstance();
  const StreamingSession = getStreamingSessionClass();

  return {
    splitMessageBySentences(content, removePunctuation) {
      return normalizeList(processor.splitMessageBySentences(content, !!removePunctuation));
    },
    splitStableMessageSegments(content, removePunctuation) {
      return normalizeList(processor.splitStableMessageSegments(content, !!removePunctuation));
    },
    createStreamingSession(removePunctuation) {
      const session = new StreamingSession(!!removePunctuation);
      return {
        collectStableSegments(content, tailSegmentOpen) {
          return normalizeList(session.collectStableSegments(content, !!tailSegmentOpen));
        },
        collectFinalSegments(content) {
          return normalizeList(session.collectFinalSegments(content));
        },
      };
    },
    cleanContentForWaifu(content) {
      return String(processor.cleanContentForWaifu(content));
    },
  };
}

const SENTENCE_END_RE = /(?:[。！？~～.!?…]|\.\.\.)\s*$/;
const URL_CHAR_CLASS = "[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]";
const URL_RE = new RegExp(`https?://${URL_CHAR_CLASS}+`);
const DOMAIN_URL_RE = new RegExp(
  `(?:www\\.)?(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?::\\d+)?(?:[/?#]${URL_CHAR_CLASS}*)?`
);
const EMAIL_RE = /[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}/;

function getLastVisibleLine(content) {
  const lines = String(content || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  return lines.length > 0 ? lines[lines.length - 1] : '';
}

// allowBlockPrefixExemption=false 对应 Kotlin 开放尾段（尾部块未闭合）：
// 尾行即使带有块前缀（"1. 今"、"## 标"）该行也尚未完结，块前缀不能作为
// "无句尾也可发射"的稳定依据，否则编号/列表/标题行被逐 chunk 切成 1-2 字碎片。
// URL/邮箱行豁免不受此开关影响。
function lineAllowsStableWithoutSentenceEnding(line, allowBlockPrefixExemption) {
  const trimmed = String(line || '').trim();
  if (!trimmed) {
    return false;
  }
  if (allowBlockPrefixExemption === undefined || allowBlockPrefixExemption) {
    if (/^(?:```|\|)/.test(trimmed) || /^\$\$/.test(trimmed)) {
      return true;
    }
    if (/^(?:#+\s*|>\s*|[-*+]\s+|\d+\.\s+)/.test(trimmed)) {
      return true;
    }
  }

  const cleaned = trimmed
    .replace(/^\*+|\*+$/g, '')
    .replace(/^_+|_+$/g, '')
    .replace(/^~+|~+$/g, '')
    .replace(/^#+\s*/, '')
    .replace(/^>\s*/, '')
    .replace(/^(?:[-*+]\s+|\d+\.\s+)/, '')
    .trim();

  return URL_RE.test(cleaned) || DOMAIN_URL_RE.test(cleaned) || EMAIL_RE.test(cleaned);
}

function isUrlOrEmailLine(line) {
  const trimmed = String(line || '').trim();
  if (!trimmed) {
    return false;
  }
  return URL_RE.test(trimmed) || DOMAIN_URL_RE.test(trimmed) || EMAIL_RE.test(trimmed);
}

function cleanStructuredMarkdownLine(line) {
  return String(line || '')
    .trim()
    .replace(/^#+\s*/, '')
    .replace(/^>\s*/, '')
    .replace(/^(?:[-*+]\s+|\d+\.\s+)/, '')
    .replace(/^\*\*(.+)\*\*$/u, '$1')
    .replace(/^__(.+)__$/u, '$1')
    .replace(/^~~(.+)~~$/u, '$1')
    .trim();
}

function shouldUseStructuredLineFallback(content, fullSegments) {
  if (fullSegments.length !== 1) {
    return false;
  }

  const nonEmptyLines = String(content || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  if (nonEmptyLines.length < 2) {
    return false;
  }

  return nonEmptyLines.some((line) => isUrlOrEmailLine(cleanStructuredMarkdownLine(line)));
}

function splitStructuredMarkdownLines(kotlin, content, removePunctuation) {
  const out = [];
  const lines = String(content || '').split(/\r?\n/);

  for (const rawLine of lines) {
    const trimmed = rawLine.trim();
    if (!trimmed || /^[-_*]{3,}$/.test(trimmed)) {
      continue;
    }

    const cleanedLine = cleanStructuredMarkdownLine(trimmed);
    if (!cleanedLine) {
      continue;
    }

    out.push(...kotlin.splitMessageBySentences(cleanedLine, removePunctuation));
  }

  return out;
}

function splitStableSegmentsWithJsHeuristics(fullSegments, content) {
  if (fullSegments.length === 0) {
    return [];
  }

  const trimmedContent = String(content || '').trimEnd();
  if (SENTENCE_END_RE.test(trimmedContent)) {
    return fullSegments;
  }

  if (lineAllowsStableWithoutSentenceEnding(getLastVisibleLine(trimmedContent))) {
    return fullSegments;
  }

  return fullSegments.slice(0, -1);
}

class JsPrototypeStreamingSession {
  constructor(adapter, removePunctuation) {
    this.adapter = adapter;
    this.removePunctuation = !!removePunctuation;
    // 已发射内容的非空白字符视图（与 Kotlin StreamingSession.emittedCompact 一致）
    this.emittedCompact = '';
  }

  collectSegments(segments) {
    if (segments.length === 0) {
      return [];
    }
    // 与 Kotlin StreamingSession.collectSegments 保持一致的空白弹性前缀对齐：
    // clean 的空白折叠与段级 trim 会改变空白归属，空白不属于用户内容，按非空白
    // 字符序列对齐；非空白字符被改写才视为内容回滚并整体丢弃。
    const currentText = segments.join('');
    let cursor = 0;
    let emittedIndex = 0;
    let prefixMatches = true;
    while (emittedIndex < this.emittedCompact.length) {
      while (cursor < currentText.length && /\s/.test(currentText[cursor])) {
        cursor += 1;
      }
      if (cursor >= currentText.length || currentText[cursor] !== this.emittedCompact[emittedIndex]) {
        prefixMatches = false;
        break;
      }
      cursor += 1;
      emittedIndex += 1;
    }
    if (!prefixMatches) {
      return [];
    }

    const countNonWhitespace = (text) => {
      let count = 0;
      for (const ch of text) {
        if (!/\s/.test(ch)) {
          count += 1;
        }
      }
      return count;
    };
    const appendNonWhitespace = (text) => {
      for (const ch of text) {
        if (!/\s/.test(ch)) {
          this.emittedCompact += ch;
        }
      }
    };

    const newSegments = [];
    let compactSeen = 0;
    let segmentIndex = 0;
    while (segmentIndex < segments.length) {
      const segment = segments[segmentIndex];
      const nonWhitespaceInSegment = countNonWhitespace(segment);
      if (compactSeen + nonWhitespaceInSegment > this.emittedCompact.length) {
        if (compactSeen < this.emittedCompact.length) {
          let remaining = this.emittedCompact.length - compactSeen;
          let cut = 0;
          while (cut < segment.length && remaining > 0) {
            if (!/\s/.test(segment[cut])) {
              remaining -= 1;
            }
            cut += 1;
          }
          const remainder = segment.substring(cut).trim();
          if (remainder.length > 0) {
            newSegments.push(remainder);
            appendNonWhitespace(remainder);
          }
          const following = segments.slice(segmentIndex + 1);
          for (const next of following) {
            const trimmed = next.trim();
            if (trimmed.length > 0) {
              newSegments.push(trimmed);
              appendNonWhitespace(trimmed);
            }
          }
        } else {
          // 发射点与分段边界对齐（含首次发射）：当前段起全部为新增
          const following = segments.slice(segmentIndex);
          for (const next of following) {
            const trimmed = next.trim();
            if (trimmed.length > 0) {
              newSegments.push(trimmed);
              appendNonWhitespace(trimmed);
            }
          }
        }
        return newSegments;
      }
      compactSeen += nonWhitespaceInSegment;
      segmentIndex += 1;
    }
    return newSegments;
  }

  collectStableSegments(content, tailSegmentOpen) {
    // 与 Kotlin StreamingSession 的 tailSegmentOpen 语义保持一致：
    // 尾部块未闭合时，最后一段若无稳定句尾则扣留（URL/邮件行除外），
    // 防止流式标题行被逐 chunk 切成单字/单标点碎片段；块前缀行（"1. 今"、
    // "## 标"）同样扣留——行仍在增长不是完整行，此前仅凭前缀即判稳定，
    // 编号行回复被逐词切成 1-2 字碎片段；
    // 另对"数字."悬空形态扣留——句点后无字符时可能是句尾也可能是
    // 正在形成的有序列表前缀（后随空白即被 clean 删除），先发射后删除
    // 会触发前缀回滚
    let segments = this.adapter.splitStableMessageSegments(content, this.removePunctuation);
    if (tailSegmentOpen && segments.length > 0) {
      const last = segments[segments.length - 1];
      const hasStableEnding = SENTENCE_END_RE.test(last);
      const stableWithoutEnding =
        !hasStableEnding &&
        lineAllowsStableWithoutSentenceEnding(getLastVisibleLine(content), false);
      const danglingOrderedListMarker = /^\d+\.$/.test(last);
      if ((!hasStableEnding && !stableWithoutEnding) || danglingOrderedListMarker) {
        segments = segments.slice(0, -1);
      }
    }
    return this.collectSegments(segments);
  }

  collectFinalSegments(content) {
    return this.collectSegments(
      this.adapter.splitMessageBySentences(content, this.removePunctuation)
    );
  }
}

function createJsPrototypeAdapter() {
  const kotlin = createKotlinAdapter();
  const adapter = {
    splitMessageBySentences(content, removePunctuation) {
      const fullSegments = kotlin.splitMessageBySentences(content, removePunctuation);
      if (shouldUseStructuredLineFallback(content, fullSegments)) {
        return splitStructuredMarkdownLines(kotlin, content, removePunctuation);
      }
      return fullSegments;
    },
    splitStableMessageSegments(content, removePunctuation) {
      const fullSegments = adapter.splitMessageBySentences(content, removePunctuation);
      return splitStableSegmentsWithJsHeuristics(fullSegments, content);
    },
    createStreamingSession(removePunctuation) {
      return new JsPrototypeStreamingSession(adapter, removePunctuation);
    },
    cleanContentForWaifu(content) {
      return kotlin.cleanContentForWaifu(content);
    },
  };
  return adapter;
}

function buildScreenshotCaseInput() {
  return (
    '哈哈等等，让我看看现在是什么时候了……\n' +
    '> 工具调用（1）\n' +
    '哦！现在是 5月9日（周六） 上午 10:18 ☀️\n\n' +
    '看错了看错了！通知里写的是 5月10日（周日）截止，也就是明天，不是今天！😂\n\n' +
    '所以你的时间线是：\n' +
    '- 今天 5月9日（周六） → 还有一整天准备 📝\n' +
    '- 明天 5月10日（周日） → 晚上 23:00前 填腾讯表格，24:00前 提交材料\n' +
    '还有 整整一天多的时间，不用慌！😊\n\n' +
    '不过话说……这个通知跟你有关吗？你是参赛学生还是只是被群通知到的？🤔'
  );
}

function buildInlineBoldScreenshotInput() {
  return '放心，这次只收到 **1条"?"** 了，没有重复！✅ 看来软件今天表现正常了😊';
}

function buildSelfCorrectionScreenshotInput() {
  return (
    '等等……我仔细看了看，其实你只发了 **1条**！是我自己没输出内容，结果生成了6个空白回复 😂😂😂\n\n' +
    '**是我菜，不是软件的问题！！！** 🧎‍♂️🤦‍♂️\n\n' +
    '抱歉抱歉，虚惊一场～这波我的我的！🫡😄'
  );
}

function buildTests(adapter) {
  return [
    test('plain split: decimal number is not split by dot', () => {
      const out = adapter.splitMessageBySentences('价格是 12.25 元。', false);
      assertListEq(out, ['价格是 12.25 元。']);
    }),
    test('plain split: version number is not split by dot', () => {
      const out = adapter.splitMessageBySentences('当前版本 v1.2 已发布。', false);
      assertListEq(out, ['当前版本 v1.2 已发布。']);
    }),
    test('plain split: bare url is not split by dots inside the domain', () => {
      const out = adapter.splitMessageBySentences(
        '链接如下：https://waifu.example.test/sheet/demo-link。下一句。',
        false
      );
      assertListEq(
        out,
        ['链接如下：https://waifu.example.test/sheet/demo-link。', '下一句。']
      );
    }),
    test('plain split: schemeless domain url is not split by dots inside the domain', () => {
      const out = adapter.splitMessageBySentences(
        '链接如下：waifu.example.test/sheet/demo-link。下一句。',
        false
      );
      assertListEq(
        out,
        ['链接如下：waifu.example.test/sheet/demo-link。', '下一句。']
      );
    }),
    test('plain split: email address is not split by dots inside the domain', () => {
      const out = adapter.splitMessageBySentences(
        '发送至：submit-team@waifu.example.test。下一句。',
        false
      );
      assertListEq(out, ['发送至：submit-team@waifu.example.test。', '下一句。']);
    }),
    test('markdown split: heading emphasis and link keep sentence boundaries', () => {
      const out = adapter.splitMessageBySentences(
        '# 标题\n**第一句。** [继续阅读](https://example.com/docs) 第二句！',
        false
      );
      assertListEq(
        out,
        ['标题', '第一句。', '[继续阅读](https://example.com/docs) 第二句！']
      );
    }),
    test('markdown split: quote and list markers are removed before split', () => {
      const out = adapter.splitMessageBySentences(
        '> 引用第一句。\n- 列表第二句！\n1. 第三句？',
        false
      );
      assertListEq(out, ['引用第一句。', '列表第二句！', '第三句？']);
    }),
    test('markdown split: link placeholder protects url dots and decimals', () => {
      const out = adapter.splitMessageBySentences(
        '参考 [v1.2 说明](https://example.com/v1.2?q=3.14)。下一句。',
        false
      );
      assertListEq(
        out,
        ['参考 [v1.2 说明](https://example.com/v1.2?q=3.14)。', '下一句。']
      );
    }),
    test('markdown split: fenced code block stays as a standalone protected segment', () => {
      const out = normalizeList(
        adapter.splitMessageBySentences(
          "前言。\n```js\nconst price = 12.25;\nconsole.log('hi!');\n```\n结尾。",
          false
        )
      );
      assertEq(out.length, 3, 'expected intro, code block, outro');
      assertEq(out[0], '前言。');
      assert(out[1].indexOf('const price = 12.25;') >= 0, 'code block should keep decimal content');
      assert(out[1].indexOf("console.log('hi!');") >= 0, 'code block should keep punctuation content');
      assertEq(out[2], '结尾。');
    }),
    test('tts clean: fenced code block content is removed', () => {
      const out = adapter.cleanContentForWaifu(
        "前面可以朗读。\n```kotlin\nval secret = \"不该朗读\"\nprintln(secret)\n```\n后面继续朗读。"
      );
      assertEq(out, '前面可以朗读。 后面继续朗读。');
    }),
    test('tts clean: unclosed fenced code block content is removed', () => {
      const out = adapter.cleanContentForWaifu(
        "前面可以朗读。\n```ts\nconst leaked = \"不该朗读\""
      );
      assertEq(out, '前面可以朗读。');
    }),
    test('markdown split: table stays as a standalone protected segment', () => {
      const out = normalizeList(
        adapter.splitMessageBySentences(
          '说明。\n\n| 列1 | 列2 |\n| --- | --- |\n| 1.2 | 完成！ |\n\n收尾。',
          false
        )
      );
      assertEq(out.length, 3, 'expected intro, table, outro');
      assertEq(out[0], '说明。');
      assert(out[1].indexOf('| 1.2 | 完成！ |') >= 0, 'table segment should stay intact');
      assertEq(out[2], '收尾。');
    }),
    test('markdown split: horizontal rule is removed without creating empty segments', () => {
      const out = adapter.splitMessageBySentences(
        '第一段。\n\n---\n\n第二段。',
        false
      );
      assertListEq(out, ['第一段。', '第二段。']);
    }),
    test('markdown split: image block remains visible and keeps following sentence together', () => {
      const out = adapter.splitMessageBySentences(
        '开头。\n![猫咪](https://example.com/cat.png)\n结尾。',
        false
      );
      assertListEq(out, ['开头。', '![猫咪](https://example.com/cat.png) 结尾。']);
    }),
    test('markdown split: block latex becomes its own visible segment', () => {
      const out = adapter.splitMessageBySentences(
        '前言。\n\n$$E=mc^2$$\n\n结尾。',
        false
      );
      assertListEq(out, ['前言。', 'E=mc^2', '结尾。']);
    }),
    test('markdown split: ordered list markers are removed and each item becomes a segment', () => {
      const out = adapter.splitMessageBySentences(
        '步骤：\n1. 下载\n2. 安装\n3. 完成。',
        false
      );
      assertListEq(out, ['步骤：', '下载', '安装', '完成。']);
    }),
    test('markdown split: xml think and tool blocks are excluded from visible output', () => {
      const out = adapter.splitMessageBySentences(
        '可见一。<think>隐藏推理</think><tool name="demo">忽略</tool>可见二！',
        false
      );
      assertListEq(out, ['可见一。', '可见二！']);
    }),
    test('stable split: trailing incomplete markdown sentence is withheld', () => {
      const out = adapter.splitStableMessageSegments('**第一句。** 第二', false);
      assertListEq(out, ['第一句。']);
    }),
    test('streaming session: inline bold count waits for full sentence before emitting', () => {
      const session = adapter.createStreamingSession(false);
      const partialBold = session.collectStableSegments('放心，这次只收到 **1条"?"**');
      const firstSentence = session.collectStableSegments('放心，这次只收到 **1条"?"** 了，没有重复！');
      const fullStable = session.collectStableSegments(buildInlineBoldScreenshotInput());
      const finalPart = session.collectFinalSegments(buildInlineBoldScreenshotInput());
      assertListEq(partialBold, []);
      assertListEq(firstSentence, ['放心，这次只收到 1条"?" 了，没有重复！']);
      assertListEq(fullStable, []);
      assertListEq(finalPart, ['✅ 看来软件今天表现正常了😊']);
    }),
    test('streaming session: unclosed header line is withheld instead of emitted per character', () => {
      // 回归用例：复现流式标题行被逐 chunk 切成单字/单标点碎片段的问题。
      // 旧实现中尾部未闭合块的类型（HEADER 等）被当作"无句尾也可发射"的稳定依据，
      // 导致 `## "雍正与乔引娣"故事简介` 在流式过程中发射为 "、雍、正、与、乔… 碎片。
      // 修复后：块内增量以 tailSegmentOpen=true 重算（扣留无句尾尾段），
      // 换行（块闭合）后以 tailSegmentOpen=false 重算（整行发射）。
      const input =
        '让我为你梳理一下这个故事：\n' +
        '## "雍正与乔引娣"故事简介\n' +
        '### 📖 人物背景\n' +
        '乔引娣是二月河小说中虚构的民间女子，被皇十四子救下。\n' +
        '### 🌹 故事脉络\n' +
        '太后乌雅氏病逝归葬景陵时，身份低微的她为雍正奉茶。\n' +
        '这剧情确实离谱。';
      const finalSegments = adapter.splitMessageBySentences(input, false);
      const expectedText = finalSegments.join('');

      const session = adapter.createStreamingSession(false);
      const emitted = [];
      for (let i = 0; i < input.length; i += 1) {
        const prefix = input.substring(0, i + 1);
        // 块内字符增量：尾部块未闭合
        session.collectStableSegments(prefix, true).forEach((segment) => emitted.push(segment));
        // 换行到达即该块闭合（与 nativeMarkdownSplitByBlock 的 flush 时机一致）
        if (input[i] === '\n') {
          session.collectStableSegments(prefix, false).forEach((segment) => emitted.push(segment));
        }
      }
      session.collectFinalSegments(input).forEach((segment) => emitted.push(segment));

      assertEq(
        emitted.join('').replace(/\s+/g, ''),
        expectedText.replace(/\s+/g, ''),
        'header streaming emitted text mismatch'
      );
      const fragments = emitted.filter((segment) => segment.replace(/\s/g, '').length <= 2);
      assertEq(fragments.length, 0, 'unexpected tiny fragments: ' + JSON.stringify(fragments));
    }),
    test('streaming session: dangling ordered list marker is withheld until disambiguated', () => {
      // 回归用例：复现编号行回复整条只剩 "1." 的截断问题。
      // 流式缓冲以 "1." 结尾时句点被当作稳定句尾发射；下一字符为空白后
      // clean 的有序列表前缀规则（^\d+\.\s+）把 "1. " 删除，已发射内容被改写，
      // 触发前缀回滚并丢弃其后全部增量。修复后悬空 "数字." 被扣留到下一字符定性。
      const input =
        '1. 今天天气真不错。\n' +
        '2. 我们出去散步吧。\n' +
        '3. 路边的花都开了。\n' +
        '4. 空气里有青草味。\n' +
        '5. 就这么说定啦。';
      const finalSegments = adapter.splitMessageBySentences(input, false);
      const expectedText = finalSegments.join('');

      const session = adapter.createStreamingSession(false);
      const emitted = [];
      const originalCollect = session.collectStableSegments.bind(session);
      for (let i = 0; i < input.length; i += 1) {
        const prefix = input.substring(0, i + 1);
        // 块内字符增量：尾部块未闭合（悬空标记在此被扣留）
        originalCollect(prefix, true).forEach((segment) => emitted.push(segment));
        if (input[i] === '\n') {
          originalCollect(prefix, false).forEach((segment) => emitted.push(segment));
        }
      }
      session.collectFinalSegments(input).forEach((segment) => emitted.push(segment));

      // 最终分句不含悬空 "数字."（完整文本中列表前缀被 clean 删除）
      assertEq(
        finalSegments.some((segment) => /^\d+\.$/.test(segment)),
        false,
        'final segments should not contain dangling markers'
      );
      assertEq(
        emitted.join('').replace(/\s+/g, ''),
        expectedText.replace(/\s+/g, ''),
        'ordered-list replay emitted text mismatch'
      );
      // 悬空标记从未被发射（否则 "1." 会作为碎片段出现在输出中）
      assertEq(
        emitted.some((segment) => /^\d+\.$/.test(segment)),
        false,
        'dangling marker must not be emitted'
      );
    }),
    test('streaming session: open-tail prefixed lines emit whole lines, not per-word fragments', () => {
      // 回归用例：复现编号行回复被逐词切成 1-2 字消息的碎片化问题（真机
      // WaifuStreamDiag：buffer="1. 今天" 即发射 [今天]，随后 [天气][真][不错][。]，
      // 每个词一条消息入库、污染会话历史）。
      // 根因：尾部块未闭合时行前缀（^\d+\.\s+ 等）仍被当作"无句尾也可发射"
      // 的稳定依据——前缀 "1. " 一出现，其后每个增量 chunk 都被判稳定发射。
      // 修复后：开放尾段的前缀行被扣留，句尾（。）到达或块闭合时整行发射。
      const input =
        '1. 今天天气真不错。\n' +
        '2. 我们出去散步吧。\n' +
        '3. 路边的花都开了。\n' +
        '4. 空气里有青草味。\n' +
        '5. 你最近过得好吗。\n' +
        '6. 我昨天看了场电影。\n' +
        '7. 结局特别让人意外。\n' +
        '8. 下次一起去看吧。\n' +
        '9. 我请你喝奶茶哦。\n' +
        '10. 就这么说定啦。';
      const finalSegments = adapter.splitMessageBySentences(input, false);
      const expectedText = finalSegments.join('');

      // 真机 delta 序列还原：每行 piece 边界与 WaifuStreamDiag 日志一致
      // （"1"|"."|" "|1-2 字分词|…|"。\n"）
      const pieces = [];
      input.split('\n').forEach((line) => {
        const markerMatch = line.match(/^(\d+)(\.)(\s)([\s\S]*)$/);
        pieces.push(markerMatch[1], markerMatch[2], markerMatch[3]);
        const words = markerMatch[4].match(/.{1,2}/g) || [];
        words.forEach((word, index) => {
          pieces.push(index === words.length - 1 ? word + '\n' : word);
        });
      });

      const session = adapter.createStreamingSession(false);
      const emitted = [];
      let buffer = '';
      for (const piece of pieces) {
        buffer += piece;
        // 块内 piece 到达：尾部块未闭合
        session.collectStableSegments(buffer, true).forEach((segment) => emitted.push(segment));
        // 换行即该块闭合（与 nativeMarkdownSplitByBlock 的 flush 时机一致）
        if (piece.endsWith('\n')) {
          session.collectStableSegments(buffer, false).forEach((segment) => emitted.push(segment));
        }
      }
      session.collectFinalSegments(input).forEach((segment) => emitted.push(segment));

      assertEq(
        emitted.join('').replace(/\s+/g, ''),
        expectedText.replace(/\s+/g, ''),
        'ordered-list piece replay emitted text mismatch'
      );
      const fragments = emitted.filter((segment) => segment.replace(/\s/g, '').length <= 2);
      assertEq(fragments.length, 0, 'unexpected tiny fragments: ' + JSON.stringify(fragments));
      // 每行恰一个整行段：发射序列与最终分段完全一致（无逐词增量）
      assertListEq(emitted, finalSegments);
    }),
    test('streaming session: markdown document replay never drops incremental text', () => {
      // 回归用例：复现 Markdown 长文档流式回复被截断的问题。
      // 旧实现按"分段列表逐项全等"判定前缀，流式重算因标点合并/未闭合标记暂扣而
      // 重新分组时误判"前缀变化"，从该 chunk 起永久吞掉全部增量。
      // 修复后按字符级前缀对齐，任意 chunk 粒度下发射总拼接必须等于全文最终分段拼接。
      const input =
        '好的，这是为你生成的说明文档：\n' +
        '---\n' +
        '## 幸福者退让原则\n' +
        '**一、什么是幸福者退让**\n' +
        '"幸福者退让"是一种生活哲学，核心是主动退让。这一原则的出发点并非软弱！\n' +
        '**二、核心理念**\n' +
        '幸福的人面对争执会选择退让一步，避免事态升级，不值得用意气去冒险。\n' +
        '---\n' +
        '全文约480字，随时告诉我！';
      const finalSegments = adapter.splitMessageBySentences(input, false);
      const expectedText = finalSegments.join('');
      // 覆盖 1/3/7/32 字符步长：粗粒度触发暂扣/释放重组，细粒度触发逐字符追加
      [1, 3, 7, 32].forEach((step) => {
        const session = adapter.createStreamingSession(false);
        const emitted = [];
        for (let offset = 0; offset < input.length; offset += step) {
          // 逐步扩大前缀重放（与 renderableBuffer 语义一致）
          session.collectStableSegments(input.substring(0, Math.min(offset + step, input.length)))
            .forEach((segment) => emitted.push(segment));
        }
        session.collectFinalSegments(input).forEach((segment) => emitted.push(segment));
        // 段级 trim 会在段边界丢弃空白，按非空白字符比较内容完整性
        assertEq(
          emitted.join('').replace(/\s+/g, ''),
          expectedText.replace(/\s+/g, ''),
          'chunk step=' + step + ' emitted text mismatch'
        );
      });
    }),
    test('markdown split: inline bold count keeps screenshot sentence boundaries', () => {
      const out = adapter.splitMessageBySentences(buildInlineBoldScreenshotInput(), false);
      assertListEq(out, [
        '放心，这次只收到 1条"?" 了，没有重复！',
        '✅ 看来软件今天表现正常了😊',
      ]);
    }),
    test('markdown split: self-correction screenshot keeps bold sentence and emoji tails', () => {
      const out = adapter.splitMessageBySentences(buildSelfCorrectionScreenshotInput(), false);
      assertListEq(out, [
        '等等……',
        '我仔细看了看，其实你只发了 1条！',
        '是我自己没输出内容，结果生成了6个空白回复 😂😂😂 是我菜，不是软件的问题！！！',
        '🧎‍♂️🤦‍♂️ 抱歉抱歉，虚惊一场～',
        '这波我的我的！',
        '🫡😄',
      ]);
    }),
    test('streaming session: self-correction screenshot stays incremental', () => {
      const session = adapter.createStreamingSession(false);
      const input = buildSelfCorrectionScreenshotInput();
      const stable = session.collectStableSegments(input);
      const finalPart = session.collectFinalSegments(input);
      assertListEq(stable, [
        '等等……',
        '我仔细看了看，其实你只发了 1条！',
        '是我自己没输出内容，结果生成了6个空白回复 😂😂😂 是我菜，不是软件的问题！！！',
        '🧎‍♂️🤦‍♂️ 抱歉抱歉，虚惊一场～',
        '这波我的我的！',
      ]);
      assertListEq(finalPart, ['🫡😄']);
    }),
    test('streaming session: self-correction incomplete bold is withheld until closed', () => {
      const session = adapter.createStreamingSession(false);
      const partialInput =
        '等等……我仔细看了看，其实你只发了 **1条**！' +
        '是我自己没输出内容，结果生成了6个空白回复 😂😂😂\n\n' +
        '**是我菜，不是软件的问题！！！';
      const partialStable = session.collectStableSegments(partialInput);
      const closedBoldStable = session.collectStableSegments(partialInput + '**');
      const fullStable = session.collectStableSegments(buildSelfCorrectionScreenshotInput());
      const finalPart = session.collectFinalSegments(buildSelfCorrectionScreenshotInput());
      assertListEq(partialStable, [
        '等等……',
        '我仔细看了看，其实你只发了 1条！',
      ]);
      assertListEq(closedBoldStable, [
        '是我自己没输出内容，结果生成了6个空白回复 😂😂😂 是我菜，不是软件的问题！！！',
      ]);
      assertListEq(fullStable, [
        '🧎‍♂️🤦‍♂️ 抱歉抱歉，虚惊一场～',
        '这波我的我的！',
      ]);
      assertListEq(finalPart, ['🫡😄']);
    }),
    test('streaming session: stable and final markdown emissions stay incremental', () => {
      const session = adapter.createStreamingSession(false);
      const stable = session.collectStableSegments('# 标题\n第一句。第二');
      const finalPart = session.collectFinalSegments('# 标题\n第一句。第二');
      assertListEq(stable, ['标题', '第一句。']);
      assertListEq(finalPart, ['第二']);
    }),
    test('streaming session: ordered list tail is emitted as stable at markdown block boundary', () => {
      const session = adapter.createStreamingSession(false);
      const stable = session.collectStableSegments('步骤：\n1. 下载\n2. 安装\n3. 等待');
      const finalPart = session.collectFinalSegments('步骤：\n1. 下载\n2. 安装\n3. 等待');
      assertListEq(stable, ['步骤：', '下载', '安装', '等待']);
      assertListEq(finalPart, []);
    }),
    test('streaming session: screenshot style bare url and email stay intact', () => {
      const session = adapter.createStreamingSession(false);
      const input =
        '**⏰ 23:00前截止 → 填腾讯表格**\n' +
        '👉 https://waifu.example.test/sheet/demo-link\n\n' +
        '**⏰ 24:00前截止 → 发邮件提交材料**\n' +
        '✉️ 发送至：submit-team@waifu.example.test';
      const stable = session.collectStableSegments(input);
      const finalPart = session.collectFinalSegments(input);
      assertListEq(stable, [
        '⏰ 23:00前截止 → 填腾讯表格',
        '👉 https://waifu.example.test/sheet/demo-link',
        '⏰ 24:00前截止 → 发邮件提交材料',
        '✉️ 发送至：submit-team@waifu.example.test',
      ]);
      assertListEq(finalPart, []);
    }),
    test('streaming session: screenshot style schemeless url stays intact', () => {
      const session = adapter.createStreamingSession(false);
      const input =
        '**⏰ 23:00前截止 → 填表**\n' +
        '👉 waifu.example.test/sheet/demo-link\n\n' +
        '记得按时提交。';
      const stable = session.collectStableSegments(input);
      const finalPart = session.collectFinalSegments(input);
      assertListEq(stable, [
        '⏰ 23:00前截止 → 填表',
        '👉 waifu.example.test/sheet/demo-link',
        '记得按时提交。',
      ]);
      assertListEq(finalPart, []);
    }),
    test('streaming session: screenshot case keeps block-boundary segments and only delays final emoji', () => {
      const session = adapter.createStreamingSession(false);
      const input = buildScreenshotCaseInput();
      const stable = session.collectStableSegments(input);
      const finalPart = session.collectFinalSegments(input);
      assertListEq(stable, [
        '哈哈等等，让我看看现在是什么时候了……',
        '工具调用（1）',
        '哦！',
        '现在是 5月9日（周六） 上午 10:18 ☀️ 看错了看错了！',
        '通知里写的是 5月10日（周日）截止，也就是明天，不是今天！',
        '😂 所以你的时间线是：',
        '今天 5月9日（周六） → 还有一整天准备 📝',
        '明天 5月10日（周日） → 晚上 23:00前 填腾讯表格，24:00前 提交材料',
        '还有 整整一天多的时间，不用慌！',
        '😊 不过话说……',
        '这个通知跟你有关吗？',
        '你是参赛学生还是只是被群通知到的？',
      ]);
      assertListEq(finalPart, ['🤔']);
    }),
    test('markdown split: removePunctuation keeps markdown entity and trims sentence endings', () => {
      const out = adapter.splitMessageBySentences(
        '[参考](https://example.com/docs)。第二句！',
        true
      );
      assertListEq(out, ['[参考](https://example.com/docs)', '第二句']);
    }),
  ];
}

exports.run = async function run() {
  const result = await runTests(buildTests(createKotlinAdapter()));
  assert(result.passed + result.failed > 0, 'no tests executed');
  return result;
};

exports.runPrototype = async function runPrototype() {
  const result = await runTests(buildTests(createJsPrototypeAdapter()));
  assert(result.passed + result.failed > 0, 'no tests executed');
  return result;
};

exports.inspectScreenshotCase = function inspectScreenshotCase() {
  const adapter = createKotlinAdapter();
  const session = adapter.createStreamingSession(false);
  const input = buildScreenshotCaseInput();

  return {
    input,
    cleanContent: adapter.cleanContentForWaifu(input),
    split: adapter.splitMessageBySentences(input, false),
    stableSplit: adapter.splitStableMessageSegments(input, false),
    streamStableThenFinal: {
      stable: session.collectStableSegments(input),
      final: session.collectFinalSegments(input),
    },
    splitRemovePunctuation: adapter.splitMessageBySentences(input, true),
  };
};

exports.inspectPrototypeUrlCases = function inspectPrototypeUrlCases() {
  const kotlin = createKotlinAdapter();
  const prototype = createJsPrototypeAdapter();

  const cases = {
    bareUrlAndEmail:
      '**⏰ 23:00前截止 → 填腾讯表格**\n' +
      '👉 https://waifu.example.test/sheet/demo-link\n\n' +
      '**⏰ 24:00前截止 → 发邮件提交材料**\n' +
      '✉️ 发送至：submit-team@waifu.example.test',
    schemelessUrl:
      '**⏰ 23:00前截止 → 填表**\n' +
      '👉 waifu.example.test/sheet/demo-link\n\n' +
      '记得按时提交。',
  };

  function inspect(adapter, input) {
    const session = adapter.createStreamingSession(false);
    return {
      split: adapter.splitMessageBySentences(input, false),
      stableSplit: adapter.splitStableMessageSegments(input, false),
      streamStableThenFinal: {
        stable: session.collectStableSegments(input),
        final: session.collectFinalSegments(input),
      },
    };
  }

  return {
    bareUrlAndEmail: {
      kotlin: inspect(kotlin, cases.bareUrlAndEmail),
      prototype: inspect(prototype, cases.bareUrlAndEmail),
    },
    schemelessUrl: {
      kotlin: inspect(kotlin, cases.schemelessUrl),
      prototype: inspect(prototype, cases.schemelessUrl),
    },
  };
};

exports.inspectSelfCorrectionCase = function inspectSelfCorrectionCase() {
  const adapter = createKotlinAdapter();
  const session = adapter.createStreamingSession(false);
  const input = buildSelfCorrectionScreenshotInput();

  return {
    input,
    cleanContent: adapter.cleanContentForWaifu(input),
    split: adapter.splitMessageBySentences(input, false),
    stableSplit: adapter.splitStableMessageSegments(input, false),
    streamStableThenFinal: {
      stable: session.collectStableSegments(input),
      final: session.collectFinalSegments(input),
    },
    splitRemovePunctuation: adapter.splitMessageBySentences(input, true),
  };
};

exports.inspectSelfCorrectionStreamingSteps = function inspectSelfCorrectionStreamingSteps() {
  const adapter = createKotlinAdapter();
  const session = adapter.createStreamingSession(false);
  const partialInput =
    '等等……我仔细看了看，其实你只发了 **1条**！' +
    '是我自己没输出内容，结果生成了6个空白回复 😂😂😂\n\n' +
    '**是我菜，不是软件的问题！！！';
  const closedBoldInput = partialInput + '**';
  const fullInput = buildSelfCorrectionScreenshotInput();

  return {
    partialInput,
    closedBoldInput,
    fullInput,
    partialSplit: adapter.splitMessageBySentences(partialInput, false),
    partialStableSplit: adapter.splitStableMessageSegments(partialInput, false),
    closedBoldSplit: adapter.splitMessageBySentences(closedBoldInput, false),
    closedBoldStableSplit: adapter.splitStableMessageSegments(closedBoldInput, false),
    streamSteps: {
      partialStable: session.collectStableSegments(partialInput),
      closedBoldStable: session.collectStableSegments(closedBoldInput),
      fullStable: session.collectStableSegments(fullInput),
      final: session.collectFinalSegments(fullInput),
    },
  };
};
