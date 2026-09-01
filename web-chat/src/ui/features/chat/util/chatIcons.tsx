import type { ReactNode, SVGProps } from 'react';

export type IconProps = SVGProps<SVGSVGElement> & {
  size?: number;
};

function IconBase({
  size = 18,
  children,
  ...props
}: IconProps & { children: ReactNode }) {
  return (
    <svg
      fill="none"
      height={size}
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.8"
      viewBox="0 0 24 24"
      width={size}
      {...props}
    >
      {children}
    </svg>
  );
}

export function HistoryIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M3 12a9 9 0 1 0 3-6.7" />
      <path d="M3 4v5h5" />
      <path d="M12 7v5l3 2" />
    </IconBase>
  );
}

// --- App 壳层与设置页图标（Material 风格，延续手绘规范） ---

export function MenuIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M4 6h16" />
      <path d="M4 12h16" />
      <path d="M4 18h16" />
    </IconBase>
  );
}

export function ArrowBackIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M19 12H5" />
      <path d="m12 19-7-7 7-7" />
    </IconBase>
  );
}

export function TerminalIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m5 8 4 4-4 4" />
      <path d="M12 16h7" />
    </IconBase>
  );
}

export function CodeIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m9 8-4 4 4 4" />
      <path d="m15 8 4 4-4 4" />
    </IconBase>
  );
}

export function CodeOffIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m10 8-4 4 4 4" />
      <path d="M3 3l18 18" />
    </IconBase>
  );
}

export function WifiIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M2.5 9.5a15 15 0 0 1 19 0" />
      <path d="M5.5 13a10 10 0 0 1 13 0" />
      <path d="M8.5 16.5a5 5 0 0 1 7 0" />
      <circle cx="12" cy="19.5" r="0.8" fill="currentColor" stroke="none" />
    </IconBase>
  );
}

export function ExtensionIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M10 4a2 2 0 1 1 4 0v1h3a1 1 0 0 1 1 1v3h1a2 2 0 1 1 0 4h-1v3a1 1 0 0 1-1 1h-3v1a2 2 0 1 1-4 0v-1H7a1 1 0 0 1-1-1v-3H5a2 2 0 1 1 0-4h1V6a1 1 0 0 1 1-1h3Z" />
    </IconBase>
  );
}

export function BuildIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M14.5 4.5a4.5 4.5 0 0 1 5.4 6.1L12 18.5a3 3 0 0 1-4.2 0l-2.3-2.3a3 3 0 0 1 0-4.2l7.9-7.9a4.5 4.5 0 0 1 1.1-1.6Z" transform="translate(-1.5 1.2)" />
      <path d="m15 7 2 2" />
    </IconBase>
  );
}

export function HelpIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9.5a2.5 2.5 0 1 1 3.6 2.2c-.7.4-1.1 1-1.1 1.8" />
      <path d="M12 17h.01" />
    </IconBase>
  );
}

export function SettingsIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M12 8.5A3.5 3.5 0 1 0 12 15.5 3.5 3.5 0 0 0 12 8.5Z" />
      <path d="M19.4 13.5a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" />
    </IconBase>
  );
}

export function EmailIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="m3.5 7.5 8.5 6 8.5-6" />
    </IconBase>
  );
}

export function AppsIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      { [5, 12, 19].map((y) => [5, 12, 19].map((x) => <circle key={`${x}-${y}`} cx={x} cy={y} r="1.6" fill="currentColor" stroke="none" />)) }
    </IconBase>
  );
}

export function ChevronRightIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m9 6 6 6-6 6" />
    </IconBase>
  );
}

// --- 设置页图标 ---

export function FaceIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M8.5 10h.01" strokeWidth="2.4" />
      <path d="M15.5 10h.01" strokeWidth="2.4" />
      <path d="M8.5 15a5 5 0 0 1 7 0" />
    </IconBase>
  );
}

export function LanguageIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18" />
      <path d="M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18Z" />
    </IconBase>
  );
}

export function PaletteIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M12 3a9 9 0 1 0 0 18c1.2 0 2-.8 2-2 0-.6-.2-1-.6-1.4-.4-.4-.6-.8-.6-1.4 0-1.2.8-2 2-2h2.2A4 4 0 0 0 21 10c0-3.9-4-7-9-7Z" />
      <circle cx="7.5" cy="11" r="0.9" fill="currentColor" stroke="none" />
      <circle cx="10.5" cy="7.5" r="0.9" fill="currentColor" stroke="none" />
      <circle cx="15" cy="7.5" r="0.9" fill="currentColor" stroke="none" />
    </IconBase>
  );
}

export function VisibilityIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="3" />
    </IconBase>
  );
}

export function AspectRatioIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect x="3" y="6" width="18" height="12" rx="2" />
      <path d="M7 10v4M5 12h4" />
      <path d="M15 10h4" />
    </IconBase>
  );
}

export function ApiIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="6" cy="12" r="2.2" />
      <circle cx="18" cy="6" r="2.2" />
      <circle cx="18" cy="18" r="2.2" />
      <path d="m8 11 8-4M8 13l8 4" />
    </IconBase>
  );
}

export function RecordVoiceOverIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <ellipse cx="12" cy="8" rx="4" ry="5" />
      <path d="M5 20a7 7 0 0 1 14 0" />
      <path d="m17.5 6.5 3-2M20.5 10.5l3-1.5" transform="translate(-2 0)" />
    </IconBase>
  );
}

export function ChatBubbleIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M4 6a2.5 2.5 0 0 1 2.5-2.5h11A2.5 2.5 0 0 1 20 6v8a2.5 2.5 0 0 1-2.5 2.5H9L4.5 20V6Z" />
    </IconBase>
  );
}

export function EmojiEmotionsIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M8 14a4.5 4.5 0 0 0 8 0" />
      <path d="M8.5 9.5h.01" strokeWidth="2.4" />
      <path d="M15.5 9.5h.01" strokeWidth="2.4" />
    </IconBase>
  );
}

export function AnalyticsIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
    </IconBase>
  );
}

export function AdminPanelSettingsIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M12 3 5 5.5v5c0 4.5 3 8.2 7 9.5 4-1.3 7-5 7-9.5v-5L12 3Z" />
      <circle cx="12" cy="11" r="1.8" />
      <path d="M12 12.8v2.2" />
    </IconBase>
  );
}

export function CloudUploadIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M7 18a4.5 4.5 0 0 1-.4-9A6 6 0 0 1 18.3 9.6 3.8 3.8 0 0 1 17.5 18H7Z" />
      <path d="M12 13v6M9.5 15.5 12 13l2.5 2.5" />
    </IconBase>
  );
}

export function ManageHistoryIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M3.5 12a8.5 8.5 0 1 0 3-6.5" />
      <path d="M3.5 4.5v4h4" />
      <path d="M12 7.5V12l3.2 1.8" />
    </IconBase>
  );
}

export function DeleteSweepIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M4 7h11" />
      <path d="M9 7V4h4v3" />
      <path d="m6 7 .8 13h8.4L16 7" />
      <path d="M18.5 10.5h3M18.5 14.5h3M18.5 18.5h3" />
    </IconBase>
  );
}

export function SettingsEthernetIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m7 8-4 4 4 4M17 8l4 4-4 4" />
      <path d="M10 7 8 17M14 7l-2 10" transform="translate(2 0)" />
    </IconBase>
  );
}

export function SecurityIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M12 3 5 5.5v5c0 4.5 3 8.2 7 9.5 4-1.3 7-5 7-9.5v-5L12 3Z" />
    </IconBase>
  );
}

export function MessageIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M4 5.5h16v11H9L4 20V5.5Z" />
    </IconBase>
  );
}

export function AccountCircleIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="10" r="3.2" />
      <path d="M6.3 18.4a6.5 6.5 0 0 1 11.4 0" />
    </IconBase>
  );
}

export function LinkIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M10 13a5 5 0 0 1 0-7l1.2-1.2a5 5 0 0 1 7.1 7.1L17 13" />
      <path d="M14 11a5 5 0 0 1 0 7l-1.2 1.2a5 5 0 0 1-7.1-7.1L7 11" />
    </IconBase>
  );
}

export function SaveIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M5 4h11l3 3v13H5z" />
      <path d="M9 4v6h6V4" />
      <path d="M9 18h6" />
    </IconBase>
  );
}

export function KeyIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="8" cy="15" r="4" />
      <path d="M11 15h10" />
      <path d="M17 15v-3" />
      <path d="M20 15v-2" />
    </IconBase>
  );
}

export function PictureInPictureIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect height="14" rx="2.5" width="18" x="3" y="5" />
      <rect height="5" rx="1.4" width="6" x="12" y="11" />
    </IconBase>
  );
}

export function AttachIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M21.4 11.2 12.8 19.8a5 5 0 0 1-7.1-7.1L14 4.4a3.5 3.5 0 0 1 5 5l-8.3 8.3a2 2 0 1 1-2.8-2.8l7-7" />
    </IconBase>
  );
}

export function SendIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m22 2-7 20-4-9-9-4Z" />
      <path d="M22 2 11 13" />
    </IconBase>
  );
}

export function StopIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect height="12" rx="2.5" width="12" x="6" y="6" />
    </IconBase>
  );
}

export function MicIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect height="10" rx="4" width="8" x="8" y="3" />
      <path d="M5 10a7 7 0 0 0 14 0" />
      <path d="M12 17v4" />
    </IconBase>
  );
}

export function FullscreenIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M8 3H3v5" />
      <path d="M16 3h5v5" />
      <path d="M8 21H3v-5" />
      <path d="M16 21h5v-5" />
    </IconBase>
  );
}

export function SearchIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </IconBase>
  );
}

export function SearchOffIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
      <path d="M4 4 20 20" />
    </IconBase>
  );
}

export function PencilIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m4 20 4.2-1L19 8.2 15.8 5 5 15.8 4 20Z" />
      <path d="m13.5 6.5 4 4" />
    </IconBase>
  );
}

export function TrashIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M4 7h16" />
      <path d="M9 7V4h6v3" />
      <path d="m7 7 1 13h8l1-13" />
    </IconBase>
  );
}

export function ChevronDownIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m6 9 6 6 6-6" />
    </IconBase>
  );
}

export function ChevronUpIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m6 15 6-6 6 6" />
    </IconBase>
  );
}

export function PlusIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </IconBase>
  );
}

export function AddCircleIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v8" />
      <path d="M8 12h8" />
    </IconBase>
  );
}

export function DragHandleIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M6 8h12" />
      <path d="M6 12h12" />
      <path d="M6 16h12" />
    </IconBase>
  );
}

export function TuneIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M4 7h10" />
      <path d="M18 7h2" />
      <path d="M14 7a2 2 0 1 0 0 0Z" />
      <path d="M4 17h4" />
      <path d="M12 17h8" />
      <path d="M10 17a2 2 0 1 0 0 0Z" />
    </IconBase>
  );
}

export function InfoIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 10v6" />
      <path d="M12 7.5h.01" />
    </IconBase>
  );
}

export function DataObjectIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M7 6.5h10" />
      <path d="M6 10.5h12" />
      <path d="M7 14.5h10" />
      <path d="M9 18h6" />
      <rect x="4" y="4" width="16" height="16" rx="3" />
    </IconBase>
  );
}

export function SortIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M7 6h10" />
      <path d="m14 3 3 3-3 3" />
      <path d="M17 18H7" />
      <path d="m10 15-3 3 3 3" />
    </IconBase>
  );
}

export function CopyIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect height="12" rx="2" width="10" x="9" y="9" />
      <path d="M6 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1" />
    </IconBase>
  );
}

export function ReplyIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M9 17 4 12l5-5" />
      <path d="M4 12h9a7 7 0 0 1 7 7" />
    </IconBase>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m6 6 12 12" />
      <path d="m18 6-12 12" />
    </IconBase>
  );
}

export function BackIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M19 12H5" />
      <path d="m12 19-7-7 7-7" />
    </IconBase>
  );
}

export function FolderIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H9l2 2h7.5A2.5 2.5 0 0 1 21 9.5v7A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5Z" />
    </IconBase>
  );
}

export function ImageAttachmentIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect x="4" y="5" width="16" height="14" rx="2.5" />
      <circle cx="9" cy="10" r="1.6" />
      <path d="m7 16 3.5-3.5 2.6 2.6 2.4-2.4L17 14.2" />
    </IconBase>
  );
}

export function AudioAttachmentIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M11 7 8.2 9.8H5v4.4h3.2L11 17Z" />
      <path d="M15 9.5a4 4 0 0 1 0 5" />
      <path d="M17.8 7.2a7 7 0 0 1 0 9.6" />
    </IconBase>
  );
}

export function VideoAttachmentIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect x="4" y="6" width="12" height="12" rx="2.5" />
      <path d="m16 10 4-2.2v8.4L16 14" />
    </IconBase>
  );
}

export function ScreenshotMonitorIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect x="4" y="5" width="16" height="11" rx="2.4" />
      <path d="M12 16v3" />
      <path d="M8.5 20h7" />
      <path d="m10 9 4 3-4 3Z" />
    </IconBase>
  );
}

export function CodeAttachmentIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m9 8-4 4 4 4" />
      <path d="m15 8 4 4-4 4" />
      <path d="m13 6-2 12" />
    </IconBase>
  );
}

export function DescriptionAttachmentIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M8 4.5h6l4 4V19a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V6.5a2 2 0 0 1 2-2Z" />
      <path d="M14 4.5V9h4" />
      <path d="M9 13h6" />
      <path d="M9 16h5" />
    </IconBase>
  );
}

export function PersonIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="12" cy="8" r="3.2" />
      <path d="M5.5 19a6.5 6.5 0 0 1 13 0" />
    </IconBase>
  );
}

// 对齐 app 侧 Icons.Default.Assistant 的机器人头像形态
export function AssistantIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect height="12" rx="3" width="14" x="5" y="6" />
      <path d="M12 6V3.5" />
      <circle cx="9.3" cy="12" r="1.1" fill="currentColor" stroke="none" />
      <circle cx="14.7" cy="12" r="1.1" fill="currentColor" stroke="none" />
      <path d="M9.5 15h5" />
      <path d="M5 12H3.2" />
      <path d="M20.8 12H19" />
    </IconBase>
  );
}

export function GroupIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <circle cx="9" cy="9" r="2.5" />
      <circle cx="16" cy="10" r="2.2" />
      <path d="M4.5 19a5 5 0 0 1 9 0" />
      <path d="M13.5 18.5a4.2 4.2 0 0 1 6 0" />
    </IconBase>
  );
}

export function StarOutlineIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m12 3.5 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1L3.2 9.9l6.1-.9Z" />
    </IconBase>
  );
}

export function StarFilledIcon(props: IconProps) {
  return (
    <svg
      fill="currentColor"
      height={props.size ?? 18}
      viewBox="0 0 24 24"
      width={props.size ?? 18}
      {...props}
    >
      <path d="m12 3.5 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1L3.2 9.9l6.1-.9Z" />
    </svg>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="m5 12 4.2 4.2L19 6.5" />
    </IconBase>
  );
}

export function SwapIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <path d="M7 7h12" />
      <path d="m15 4 4 3-4 3" />
      <path d="M17 17H5" />
      <path d="m9 14-4 3 4 3" />
    </IconBase>
  );
}

export function LockIcon(props: IconProps) {
  return (
    <IconBase {...props}>
      <rect x="5" y="10" width="14" height="10" rx="2.5" />
      <path d="M8 10V7.8A4 4 0 0 1 12 4a4 4 0 0 1 4 3.8V10" />
    </IconBase>
  );
}

export function BranchIcon(props: IconProps) {  return (
    <IconBase {...props}>
      <circle cx="7" cy="6" r="2" />
      <circle cx="17" cy="18" r="2" />
      <circle cx="17" cy="6" r="2" />
      <path d="M9 6h6" />
      <path d="M17 8v8" />
      <path d="M9 6v7a5 5 0 0 0 5 5h1" />
    </IconBase>
  );
}

// 工作流快捷卡的树形图标（对齐 Material AccountTree）
export const AccountTreeIcon = BranchIcon;
