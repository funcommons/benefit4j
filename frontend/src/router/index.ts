import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useBenefitAuthStore } from '@/store/benefitAuth'
import { isFeatureEnabled, type FeatureKey } from '@/config/features'
import { useEmbedParams } from '@/composables/useEmbedParams'
import i18n from '@/locales'
import { ElMessageBox } from 'element-plus'

const t = i18n.global.t

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: 'router.login', public: true, hideInMenu: true, layout: 'blank' }
  },
  {
    path: '/benefit/app/platform/login',
    name: 'BenefitPlatformLogin',
    component: () => import('@/views/benefit/PlatformLogin.vue'),
    meta: { title: 'router.benefit-platform-login', public: true, hideInMenu: true, layout: 'blank' }
  },
  {
    path: '/benefit/app/tenant/login',
    name: 'BenefitTenantLogin',
    component: () => import('@/views/benefit/TenantLogin.vue'),
    meta: { title: 'router.benefit-tenant-login', public: true, hideInMenu: true, layout: 'blank' }
  },
  {
    path: '/logout',
    name: 'Logout',
    component: () => import('@/views/auth/Logout.vue'),
    meta: { title: 'router.logout', public: true, hideInMenu: true, layout: 'blank' }
  },
  {
    path: '/',
    name: 'Portal',
    component: () => import('@/views/PortalHome.vue'),
    meta: { title: 'router.portal', public: true, hideInMenu: true, layout: 'blank' }
  },
  // —— /dev 演示页 (16 个子页 + 1 个索引) — AppLayout 侧栏, URL 保持 /dev/* ——
  {
    path: '/dev',
    component: () => import('@/layout/AppLayout.vue'),
    children: [
      {
        path: '',
        name: 'DevIndex',
        component: () => import('@/views/dev/index.vue'),
        meta: { title: 'router.dev-index', icon: 'Tools', feature: 'dev' }
      },
      {
        path: 'inspiration',
        name: 'DevInspiration',
        component: () => import('@/views/dev/InspirationDemo.vue'),
        meta: { title: 'router.dev-inspiration', hideInMenu: true }
      },
      {
        path: 'users',
        name: 'DevUsers',
        component: () => import('@/views/dev/UsersDemo.vue'),
        meta: { title: 'router.dev-users', hideInMenu: true }
      },
      {
        path: 'statistics',
        name: 'DevStatistics',
        component: () => import('@/views/dev/StatisticsDemo.vue'),
        meta: { title: 'router.dev-statistics', hideInMenu: true }
      },
      {
        path: 'workspace',
        name: 'DevWorkspace',
        component: () => import('@/views/dev/WorkspaceDemo.vue'),
        meta: { title: 'router.dev-workspace', hideInMenu: true }
      },
      {
        path: 'profile',
        name: 'DevProfile',
        component: () => import('@/views/dev/ProfileDemo.vue'),
        meta: { title: 'router.dev-profile', hideInMenu: true }
      },
      {
        path: 'creator',
        name: 'DevCreator',
        component: () => import('@/views/dev/CreatorDemo.vue'),
        meta: { title: 'router.dev-creator', hideInMenu: true }
      },
      {
        path: 'detail',
        name: 'DevDetail',
        component: () => import('@/views/dev/DetailDemo.vue'),
        meta: { title: 'router.dev-detail', hideInMenu: true }
      },
      {
        path: 'templates',
        name: 'DevTemplates',
        component: () => import('@/views/dev/TemplatesDemo.vue'),
        meta: { title: 'router.dev-templates', hideInMenu: true }
      },
      {
        path: 'chat',
        name: 'DevChat',
        component: () => import('@/views/dev/ChatDemo.vue'),
        meta: { title: 'router.dev-chat', hideInMenu: true }
      },
      {
        path: 'form',
        name: 'DevForm',
        component: () => import('@/views/dev/FormDemo.vue'),
        meta: { title: 'router.dev-form', hideInMenu: true }
      },
      {
        path: 'search',
        name: 'DevSearch',
        component: () => import('@/views/dev/SearchDemo.vue'),
        meta: { title: 'router.dev-search', hideInMenu: true }
      },
      {
        path: 'notifications',
        name: 'DevNotifications',
        component: () => import('@/views/dev/NotificationsDemo.vue'),
        meta: { title: 'router.dev-notifications', hideInMenu: true }
      },
      {
        path: 'settings',
        name: 'DevSettings',
        component: () => import('@/views/dev/SettingsDemo.vue'),
        meta: { title: 'router.dev-settings', hideInMenu: true }
      },
      {
        path: 'kanban',
        name: 'DevKanban',
        component: () => import('@/views/dev/KanbanDemo.vue'),
        meta: { title: 'router.dev-kanban', hideInMenu: true }
      },
      {
        path: 'timeline',
        name: 'DevTimeline',
        component: () => import('@/views/dev/TimelineDemo.vue'),
        meta: { title: 'router.dev-timeline', hideInMenu: true }
      },
      {
        path: 'ux-demo',
        name: 'DevUxDemo',
        component: () => import('@/views/dev/UxDemo.vue'),
        meta: { title: 'router.dev-ux-demo', hideInMenu: true }
      },
      {
        path: 'embed-test',
        name: 'DevEmbedTest',
        component: () => import('@/views/dev/EmbedTestDemo.vue'),
        meta: { title: 'router.dev-embed-test', hideInMenu: true }
      }
    ]
  },
  // —— Benefit4j Platform ——
  {
    path: '/benefit/platform/app',
    component: () => import('@/layout/BenefitLayout.vue'),
    meta: { benefitAuth: true },
    redirect: '/benefit/platform/app/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'BenefitPlatformDashboard',
        component: () => import('@/views/benefit/PlatformDashboard.vue'),
        meta: { title: 'router.benefit-platform-dashboard', icon: 'Odometer' }
      },
      {
        path: 'apps',
        name: 'BenefitApps',
        component: () => import('@/views/benefit/Apps.vue'),
        meta: { title: 'router.benefit-apps', icon: 'Setting' }
      },
      {
        path: 'items',
        name: 'BenefitPlatformItems',
        component: () => import('@/views/benefit/PlatformItems.vue'),
        meta: { title: 'router.benefit-platform-items', icon: 'Goods' }
      },
      {
        path: 'templates/set',
        name: 'BenefitPlatformTemplatesSet',
        component: () => import('@/views/benefit/SetTemplates.vue'),
        meta: { title: 'router.benefit-platform-templates-set', icon: 'Document' }
      },
      {
        path: 'templates/item',
        name: 'BenefitPlatformTemplatesItem',
        component: () => import('@/views/benefit/ItemTemplates.vue'),
        meta: { title: 'router.benefit-platform-templates-item', icon: 'PriceTag' }
      },
      {
        path: 'sets',
        name: 'BenefitPlatformSets',
        component: () => import('@/views/benefit/Sets.vue'),
        meta: { title: 'router.benefit-platform-sets', icon: 'Collection' }
      },
      {
        path: 'subscriptions',
        name: 'BenefitPlatformSubscriptions',
        component: () => import('@/views/benefit/Subscriptions.vue'),
        meta: { title: 'router.benefit-platform-subscriptions', icon: 'Tickets' }
      },
      {
        path: 'consumptions',
        name: 'BenefitPlatformConsumptions',
        component: () => import('@/views/benefit/Consumptions.vue'),
        meta: { title: 'router.benefit-platform-consumptions', icon: 'List' }
      },
      {
        path: 'compensations',
        name: 'BenefitPlatformCompensations',
        component: () => import('@/views/benefit/Compensations.vue'),
        meta: { title: 'router.benefit-platform-compensations', icon: 'GoldMedal' }
      },
      {
        path: 'consume-direct',
        name: 'BenefitPlatformConsumeDirect',
        component: () => import('@/views/benefit/ConsumeDirect.vue'),
        meta: { title: 'router.benefit-platform-consume-direct', icon: 'Coin' }
      },
      {
        path: 'dev/embed-test',
        name: 'BenefitPlatformEmbedTest',
        component: () => import('@/views/dev/EmbedTestDemo.vue'),
        meta: { title: 'router.dev-embed-test', icon: 'Tools' }
      },
      {
        path: 'dev/embed-docs',
        name: 'BenefitPlatformEmbedDocs',
        component: () => import('@/views/benefit/EmbedDocs.vue'),
        meta: { title: 'router.dev-embed-docs', icon: 'Document' }
      }
    ]
  },
  // —— Benefit4j Tenant ——
  {
    path: '/benefit/tenant/app',
    component: () => import('@/layout/BenefitLayout.vue'),
    meta: { benefitAuth: true },
    redirect: '/benefit/tenant/app/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'BenefitTenantDashboard',
        component: () => import('@/views/benefit/TenantDashboard.vue'),
        meta: { title: 'router.benefit-tenant-dashboard', icon: 'Odometer' }
      },
      {
        path: 'items',
        name: 'BenefitItems',
        component: () => import('@/views/benefit/Items.vue'),
        meta: { title: 'router.benefit-items', icon: 'Goods' }
      },
      {
        path: 'templates',
        name: 'BenefitTenantTemplates',
        component: () => import('@/views/benefit/TenantTemplates.vue'),
        meta: { title: 'router.benefit-tenant-templates', icon: 'Document' }
      },
      {
        path: 'sets',
        name: 'BenefitSets',
        component: () => import('@/views/benefit/Sets.vue'),
        meta: { title: 'router.benefit-sets', icon: 'Collection' }
      },
      {
        path: 'subscriptions',
        name: 'BenefitSubscriptions',
        component: () => import('@/views/benefit/Subscriptions.vue'),
        meta: { title: 'router.benefit-subscriptions', icon: 'Tickets' }
      },
      {
        path: 'consumptions',
        name: 'BenefitConsumptions',
        component: () => import('@/views/benefit/Consumptions.vue'),
        meta: { title: 'router.benefit-consumptions', icon: 'List' }
      },
      {
        path: 'compensations',
        name: 'BenefitCompensations',
        component: () => import('@/views/benefit/Compensations.vue'),
        meta: { title: 'router.benefit-compensations', icon: 'GoldMedal' }
      },
      {
        path: 'consume-direct',
        name: 'BenefitConsumeDirect',
        component: () => import('@/views/benefit/ConsumeDirect.vue'),
        meta: { title: 'router.benefit-consume-direct', icon: 'Coin' }
      }
    ]
  },
  // —— Benefit4j Platform (嵌入单页, 无侧栏) ——
  {
    path: '/benefit/platform/page',
    component: () => import('@/layout/PageLayout.vue'),
    meta: { benefitAuth: true, layout: 'page' },
    redirect: '/benefit/platform/page/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'BenefitPlatformPageDashboard',
        component: () => import('@/views/benefit/PlatformDashboard.vue'),
        meta: { title: 'router.benefit-platform-dashboard', icon: 'Odometer', hideInMenu: true }
      },
      {
        path: 'apps',
        name: 'BenefitPageApps',
        component: () => import('@/views/benefit/Apps.vue'),
        meta: { title: 'router.benefit-apps', icon: 'Setting', hideInMenu: true }
      },
      {
        path: 'items',
        name: 'BenefitPlatformPageItems',
        component: () => import('@/views/benefit/PlatformItems.vue'),
        meta: { title: 'router.benefit-platform-items', icon: 'Goods', hideInMenu: true }
      },
      {
        path: 'templates/set',
        name: 'BenefitPlatformPageTemplatesSet',
        component: () => import('@/views/benefit/SetTemplates.vue'),
        meta: { title: 'router.benefit-platform-templates-set', icon: 'Document', hideInMenu: true }
      },
      {
        path: 'templates/item',
        name: 'BenefitPlatformPageTemplatesItem',
        component: () => import('@/views/benefit/ItemTemplates.vue'),
        meta: { title: 'router.benefit-platform-templates-item', icon: 'PriceTag', hideInMenu: true }
      },
      {
        path: 'sets',
        name: 'BenefitPlatformPageSets',
        component: () => import('@/views/benefit/Sets.vue'),
        meta: { title: 'router.benefit-platform-sets', icon: 'Collection', hideInMenu: true }
      },
      {
        path: 'subscriptions',
        name: 'BenefitPlatformPageSubscriptions',
        component: () => import('@/views/benefit/Subscriptions.vue'),
        meta: { title: 'router.benefit-platform-subscriptions', icon: 'Tickets', hideInMenu: true }
      },
      {
        path: 'consumptions',
        name: 'BenefitPlatformPageConsumptions',
        component: () => import('@/views/benefit/Consumptions.vue'),
        meta: { title: 'router.benefit-platform-consumptions', icon: 'List', hideInMenu: true }
      }
    ]
  },
  // —— Benefit4j Tenant (嵌入单页, 无侧栏) ——
  {
    path: '/benefit/tenant/page',
    component: () => import('@/layout/PageLayout.vue'),
    meta: { benefitAuth: true, layout: 'page' },
    redirect: '/benefit/tenant/page/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'BenefitTenantPageDashboard',
        component: () => import('@/views/benefit/TenantDashboard.vue'),
        meta: { title: 'router.benefit-tenant-dashboard', icon: 'Odometer', hideInMenu: true }
      },
      {
        path: 'items',
        name: 'BenefitPageItems',
        component: () => import('@/views/benefit/Items.vue'),
        meta: { title: 'router.benefit-items', icon: 'Goods', hideInMenu: true }
      },
      {
        path: 'templates',
        name: 'BenefitTenantPageTemplates',
        component: () => import('@/views/benefit/TenantTemplates.vue'),
        meta: { title: 'router.benefit-tenant-templates', icon: 'Document', hideInMenu: true }
      },
      {
        path: 'sets',
        name: 'BenefitTenantPageSets',
        component: () => import('@/views/benefit/Sets.vue'),
        meta: { title: 'router.benefit-sets', icon: 'Collection', hideInMenu: true }
      },
      {
        path: 'subscriptions',
        name: 'BenefitTenantPageSubscriptions',
        component: () => import('@/views/benefit/Subscriptions.vue'),
        meta: { title: 'router.benefit-subscriptions', icon: 'Tickets', hideInMenu: true }
      },
      {
        path: 'consumptions',
        name: 'BenefitTenantPageConsumptions',
        component: () => import('@/views/benefit/Consumptions.vue'),
        meta: { title: 'router.benefit-consumptions', icon: 'List', hideInMenu: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, _from) => {
  const title = to.meta.title as string
  if (title) {
    document.title = `${t(title)} - ${t('app.name')}`
  }

  // 嵌入模式: 解析外观参数 (brand/mode/language), 即时生效不持久化
  const embed = useEmbedParams()
  embed.applyFromRoute(to)

  // 嵌入模式 (基础级): URL 带 access_token → 写入 auth store
  const urlToken = to.query.access_token
  if (typeof urlToken === 'string' && urlToken.length > 0) {
    const benefitAuth = useBenefitAuthStore()
    benefitAuth.setToken(urlToken, undefined)
  }

  // 功能开关: 关闭的功能直接重定向到 /dev
  const feature = to.meta.feature as FeatureKey | undefined
  if (feature && !isFeatureEnabled(feature)) {
    return { path: '/dev', replace: true }
  }

  // /dev 路由需要 ops 权限
  const isOpsRoute = to.path === '/dev' || to.path?.startsWith('/dev/')
  if (isOpsRoute) {
    const userStore = useUserStore()
    if (!userStore.userInfo) {
      try {
        await userStore.fetchUserInfo()
      } catch {
        // 拉取失败由 401 拦截器处理
      }
    }
    if (!userStore.userInfo?.ops) {
      return { path: '/login', replace: true }
    }
  }

  // benefit4j 路由需要 benefit4j auth (含 /app/* 和 /page/*)
  const isBenefitRoute =
    to.path.startsWith('/benefit/platform/app') ||
    to.path.startsWith('/benefit/tenant/app') ||
    to.path.startsWith('/benefit/platform/page') ||
    to.path.startsWith('/benefit/tenant/page')
  if (isBenefitRoute) {
    const benefitAuth = useBenefitAuthStore()
    if (!benefitAuth.isLoggedIn) {
      const isPlatform = to.path.startsWith('/benefit/platform/')
      const loginPath = isPlatform
        ? '/benefit/app/platform/login'
        : '/benefit/app/tenant/login'
      return { path: loginPath, replace: true }
    }
  }

  return true
})

// 全局兜底: 任意路由变化都过一遍 dirty form registry (#2)
router.beforeEach(async (_to, _from) => {
  const dirty = dirtyFormRegistry.findDirty()
  if (dirty) {
    try {
      await ElMessageBox.confirm(
        i18n.global.t('ux.dirty-form.message'),
        i18n.global.t('ux.dirty-form.title'),
        {
          confirmButtonText: i18n.global.t('ux.dirty-form.discard'),
          cancelButtonText: i18n.global.t('ux.dirty-form.cancel'),
          type: 'warning',
        },
      )
      dirty.discard()
      return true
    } catch {
      return false
    }
  }
  return true
})

/**
 * 脏表单全局注册表 (#2 表单未保存提示 — 路由级兜底).
 * useDirtyForm 自动 register/unregister, 此处提供 findDirty 给 router guard 用.
 */
export const dirtyFormRegistry = {
  _forms: new Set<{ isDirty: () => boolean; discard: () => void }>(),

  register(form: { isDirty: () => boolean; discard: () => void }) {
    this._forms.add(form)
  },

  unregister(form: { isDirty: () => boolean; discard: () => void }) {
    this._forms.delete(form)
  },

  findDirty() {
    for (const f of this._forms) {
      if (f.isDirty()) return f
    }
    return null
  },
}

export default router
