import type { ReactElement } from 'react';
import type { IconProps } from '../../util/chatIcons';
import {
  AccountCircleIcon,
  AdminPanelSettingsIcon,
  AnalyticsIcon,
  ApiIcon,
  AspectRatioIcon,
  ChatBubbleIcon,
  CloudUploadIcon,
  DeleteSweepIcon,
  EmojiEmotionsIcon,
  FaceIcon,
  LanguageIcon,
  ManageHistoryIcon,
  MessageIcon,
  PaletteIcon,
  RecordVoiceOverIcon,
  SecurityIcon,
  SettingsEthernetIcon,
  TuneIcon,
  VisibilityIcon
} from '../../util/chatIcons';

// 设置主页分组与项，文案对齐 app SettingsScreen.kt + strings.xml（中文默认资源）
export type SettingsIcon = (props: IconProps) => ReactElement;

export interface SettingsItem {
  id: string;
  icon: SettingsIcon;
  title: string;
  subtitle: string;
}

export interface SettingsSection {
  id: string;
  icon: SettingsIcon;
  title: string;
  items: SettingsItem[];
}

export const SETTINGS_SECTIONS: SettingsSection[] = [
  {
    id: 'account',
    icon: AccountCircleIcon,
    title: '账号',
    items: [
      {
        id: 'github-account',
        icon: AccountCircleIcon,
        title: 'GitHub 账户',
        subtitle: '未登录'
      },
      {
        id: 'github-login',
        icon: AccountCircleIcon,
        title: '登录 GitHub',
        subtitle: '登录以启用云端备份与同步'
      }
    ]
  },
  {
    id: 'personalization',
    icon: FaceIcon,
    title: '个性化',
    items: [
      {
        id: 'user-preferences',
        icon: FaceIcon,
        title: '用户资料设置',
        subtitle: '管理用户资料和关联记忆库'
      },
      {
        id: 'language',
        icon: LanguageIcon,
        title: '语言设置',
        subtitle: '界面语言切换'
      },
      {
        id: 'theme',
        icon: PaletteIcon,
        title: '主题和外观',
        subtitle: '主题和外观定制'
      },
      {
        id: 'global-display',
        icon: VisibilityIcon,
        title: '显示与行为',
        subtitle: '管理显示样式、自动化行为与截图等全局选项'
      },
      {
        id: 'layout-adjustment',
        icon: AspectRatioIcon,
        title: '布局调整',
        subtitle: '调整界面元素位置和边距'
      }
    ]
  },
  {
    id: 'model',
    icon: TuneIcon,
    title: 'AI模型配置',
    items: [
      {
        id: 'model-config',
        icon: ApiIcon,
        title: '模型与参数配置',
        subtitle: '模型参数和API配置'
      },
      {
        id: 'functional-config',
        icon: TuneIcon,
        title: '功能模型配置',
        subtitle: '功能模型专项配置'
      },
      {
        id: 'speech-services',
        icon: RecordVoiceOverIcon,
        title: '语音服务配置',
        subtitle: 'TTS/STT服务设置'
      }
    ]
  },
  {
    id: 'prompts',
    icon: MessageIcon,
    title: '提示词配置',
    items: [
      {
        id: 'model-prompts',
        icon: ChatBubbleIcon,
        title: '角色卡编辑',
        subtitle: '系统和模型提示词'
      },
      {
        id: 'persona-card',
        icon: FaceIcon,
        title: '人设卡生成',
        subtitle: '进入与AI对话生成个性人设卡'
      },
      {
        id: 'waifu-mode',
        icon: EmojiEmotionsIcon,
        title: 'Waifu模式设置',
        subtitle: '配置AI回复分句发送模式'
      }
    ]
  },
  {
    id: 'context',
    icon: AnalyticsIcon,
    title: '上下文与总结',
    items: [
      {
        id: 'context-summary',
        icon: TuneIcon,
        title: '上下文与总结',
        subtitle: '配置上下文窗口与自动总结策略'
      }
    ]
  },
  {
    id: 'data',
    icon: SecurityIcon,
    title: '数据和权限',
    items: [
      {
        id: 'tool-permission',
        icon: AdminPanelSettingsIcon,
        title: '工具权限设置',
        subtitle: '工具权限和安全设置'
      },
      {
        id: 'chat-backup',
        icon: CloudUploadIcon,
        title: '数据备份与恢复',
        subtitle: '导入、导出或删除聊天记录和记忆库数据'
      },
      {
        id: 'chat-history',
        icon: ManageHistoryIcon,
        title: '聊天记录管理',
        subtitle: '查看统计并批量整理、导入导出聊天记录'
      },
      {
        id: 'token-usage',
        icon: AnalyticsIcon,
        title: 'Token使用统计',
        subtitle: '查看详细的Token使用统计和自定义定价'
      }
    ]
  },
  {
    id: 'privacy',
    icon: DeleteSweepIcon,
    title: '隐私与数据清理',
    items: [
      {
        id: 'clear-cookie',
        icon: DeleteSweepIcon,
        title: '清除 Cookie',
        subtitle: '清除搜索工具、Browser 包和内置浏览器保存的 Cookie'
      }
    ]
  },
  {
    id: 'external',
    icon: SettingsEthernetIcon,
    title: '外部调用',
    items: [
      {
        id: 'external-http',
        icon: SettingsEthernetIcon,
        title: '外部 HTTP 调用',
        subtitle: '监听 0.0.0.0 的 HTTP 聊天接口、端口和 Bearer Token'
      }
    ]
  }
];

export const SETTINGS_ITEM_TITLES: Record<string, string> = Object.fromEntries(
  SETTINGS_SECTIONS.flatMap((section) =>
    section.items.map((item) => [item.id, item.title])
  )
);
