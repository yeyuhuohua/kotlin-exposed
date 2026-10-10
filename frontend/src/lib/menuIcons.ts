import type { Component } from 'vue'
import {
  Activity,
  BookOpen,
  Bot,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  ContactRound,
  Database,
  File,
  FolderClock,
  Globe2,
  LayoutDashboard,
  MapPin,
  Newspaper,
  PanelsTopLeft,
  ScrollText,
  Settings,
  ShieldCheck,
  SquareMenu,
  Star,
  Users,
} from '@lucide/vue'

/**
 * 侧栏图标注册表：菜单表的 icon 列存这里的名字，界面按名字取组件。
 * 未注册或空名字回退到默认图标，保证后端任何取值都能渲染。
 */
const registry: Record<string, Component> = {
  Activity,
  BookOpen,
  Bot,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  ContactRound,
  Database,
  FolderClock,
  Globe2,
  LayoutDashboard,
  MapPin,
  Newspaper,
  PanelsTopLeft,
  ScrollText,
  Settings,
  ShieldCheck,
  SquareMenu,
  Star,
  Users,
}

/** 菜单管理表单的可选图标清单。 */
export const menuIconNames = Object.keys(registry).sort()

export function menuIcon(name: string | null | undefined): Component {
  return (name && registry[name]) || File
}
