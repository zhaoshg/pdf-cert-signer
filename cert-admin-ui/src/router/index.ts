import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/login/Login.vue')
    },
    {
      path: '/',
      redirect: '/certificate'
    },
    {
      path: '/certificate',
      name: 'Certificate',
      component: () => import('../views/certificate/CertList.vue')
    },
    {
      path: '/signing',
      name: 'Signing',
      component: () => import('../views/signing/SignCanvas.vue')
    },
    {
      path: '/sign-page',
      name: 'SignPage',
      component: () => import('../views/signing/SignPage.vue')
    },
    {
      path: '/audit',
      name: 'Audit',
      component: () => import('../views/audit/AuditLog.vue')
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.path === '/sign-page') {
    next()
  } else if (to.path !== '/login' && !auth.token) {
    next('/login')
  } else {
    next()
  }
})

export default router
