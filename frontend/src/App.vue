<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ApiError, FlowMeshApi } from './api';
import {
  taskLabels,
  taskOrder,
  taskRoles,
  type ApplicationResponse,
  type UserSession,
  type WorkflowInstanceResponse,
} from './types';

type View = 'overview' | 'submit' | 'approval';

const api = new FlowMeshApi();
const view = ref<View>('overview');
const session = ref<UserSession | null>(null);
const application = ref<ApplicationResponse | null>(null);
const workflow = ref<WorkflowInstanceResponse | null>(null);
const applicationId = ref(localStorage.getItem('flowmesh.applicationId') ?? '');
const isBusy = ref(false);
const errorMessage = ref('');
const noticeMessage = ref('');

const loginForm = reactive({ tenantId: 'tenant-a', username: 'applicant-a', password: 'password123' });
const applicationForm = reactive({ supplierName: '' });

const isLoggedIn = computed(() => session.value !== null);
const currentTaskIndex = computed(() => {
  if (!workflow.value?.currentTask) return workflow.value?.status === 'COMPLETED' ? taskOrder.length : 0;
  return taskOrder.indexOf(workflow.value.currentTask);
});
const progress = computed(() => Math.round((currentTaskIndex.value / taskOrder.length) * 100));
const currentTaskLabel = computed(() =>
  workflow.value?.currentTask ? taskLabels[workflow.value.currentTask] : '等待流程投影',
);
const requiredRole = computed(() =>
  workflow.value?.currentTask ? taskRoles[workflow.value.currentTask] : '',
);
const canComplete = computed(() => Boolean(requiredRole.value && session.value?.roles.includes(requiredRole.value)));
const pageTitle = computed(() => {
  if (view.value === 'submit') return '新建申请';
  if (view.value === 'approval') return '审批工作台';
  return '概览';
});
const pageDescription = computed(() => {
  if (view.value === 'submit') return '录入供应商基础信息，发起一条可追踪的准入流程。';
  if (view.value === 'approval') return '按角色处理当前节点，系统会自动推进后续审批。';
  return '查看当前申请、流程进度和最近的系统活动。';
});

const demoAccounts = [
  { username: 'applicant-a', role: 'APPLICANT', label: '申请人' },
  { username: 'purchaser-a', role: 'PURCHASER', label: '采购' },
  { username: 'legal-a', role: 'LEGAL', label: '法务' },
  { username: 'finance-a', role: 'FINANCE', label: '财务' },
  { username: 'operations', role: 'OPERATIONS', label: '运营' },
];

function useDemoAccount(username: string): void {
  loginForm.username = username;
  loginForm.password = 'password123';
}

async function login(): Promise<void> {
  errorMessage.value = '';
  isBusy.value = true;
  try {
    session.value = await api.login(loginForm.tenantId, loginForm.username, loginForm.password);
    view.value = 'overview';
    await loadState();
    noticeMessage.value = `已进入 ${loginForm.tenantId} 的工作台`;
  } catch (error) {
    showError(error);
  } finally {
    isBusy.value = false;
  }
}

async function logout(): Promise<void> {
  await api.logout();
  session.value = null;
  application.value = null;
  workflow.value = null;
  noticeMessage.value = '';
  errorMessage.value = '';
}

async function createApplication(): Promise<void> {
  if (!applicationForm.supplierName.trim()) return;
  errorMessage.value = '';
  isBusy.value = true;
  try {
    const created = await api.createApplication(applicationForm.supplierName.trim());
    applicationId.value = created.id;
    localStorage.setItem('flowmesh.applicationId', created.id);
    application.value = created;
    workflow.value = null;
    applicationForm.supplierName = '';
    view.value = 'overview';
    noticeMessage.value = '申请已提交，正在等待审批流程创建';
  } catch (error) {
    showError(error);
  } finally {
    isBusy.value = false;
  }
}

async function loadState(): Promise<void> {
  if (!applicationId.value || !session.value) return;
  errorMessage.value = '';
  isBusy.value = true;
  try {
    application.value = await api.getApplication(applicationId.value);
    try {
      workflow.value = await api.getWorkflow(applicationId.value);
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 404) throw error;
      workflow.value = null;
    }
  } catch (error) {
    application.value = null;
    workflow.value = null;
    showError(error);
  } finally {
    isBusy.value = false;
  }
}

async function completeCurrentTask(): Promise<void> {
  if (!workflow.value?.currentTask || !applicationId.value || !canComplete.value) return;
  const completedTaskLabel = currentTaskLabel.value;
  errorMessage.value = '';
  isBusy.value = true;
  try {
    workflow.value = await api.completeTask(applicationId.value, workflow.value.currentTask);
    await refreshApplication();
    noticeMessage.value = workflow.value.status === 'COMPLETED'
      ? '审批链已完成，供应商已进入启用状态'
      : `${completedTaskLabel} 已完成，流程继续推进`;
  } catch (error) {
    showError(error);
  } finally {
    isBusy.value = false;
  }
}

async function refreshApplication(): Promise<void> {
  if (!applicationId.value || !session.value) return;
  try {
    application.value = await api.getApplication(applicationId.value);
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 404) throw error;
  }
}

function selectApplication(): void {
  const normalized = applicationId.value.trim();
  applicationId.value = normalized;
  if (normalized) {
    localStorage.setItem('flowmesh.applicationId', normalized);
    void loadState();
  }
}

function showError(error: unknown): void {
  errorMessage.value = error instanceof Error ? error.message : '操作失败，请检查服务状态';
}

function formatTime(value?: string): string {
  if (!value) return '未记录';
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

onMounted(() => {
  if (applicationId.value) localStorage.setItem('flowmesh.applicationId', applicationId.value);
});
</script>

<template>
  <main class="app-shell" :class="{ 'is-login': !isLoggedIn }">
    <section v-if="!isLoggedIn" class="login-layout">
      <div class="login-brand-column">
        <header class="brand-lockup">
          <span class="brand-mark">FM</span>
          <span class="brand-copy"><strong>FlowMesh</strong><small>供应商准入工作台</small></span>
        </header>
        <div class="login-intro">
          <span class="product-tag">供应商准入控制台</span>
          <h1>把申请交给流程，<br /><em>把结果交给团队。</em></h1>
          <p>统一管理供应商申请、角色审批与状态追踪，让每一次准入都能回溯。</p>
          <div class="login-capabilities">
            <div><strong>01</strong><span>统一身份</span><small>按租户和角色进入工作区</small></div>
            <div><strong>02</strong><span>事件驱动</span><small>申请状态沿消息链路推进</small></div>
            <div><strong>03</strong><span>全程留痕</span><small>每次审批都保留状态版本</small></div>
          </div>
        </div>
        <footer class="login-footer"><span class="status-indicator"></span><span>本地开发环境</span><span class="footer-divider"></span><span>IAM / Supplier / Workflow</span></footer>
      </div>

      <div class="login-panel">
        <div class="login-card-header"><span class="section-overline">登录</span><span class="environment-badge">LOCAL</span></div>
        <h2>进入工作台</h2>
        <p class="muted">使用 IAM 服务签发的身份进入对应工作区。</p>
        <form class="login-form" @submit.prevent="login">
          <label>
            <span>租户</span>
            <select v-model="loginForm.tenantId">
              <option value="tenant-a">tenant-a · 采购方</option>
              <option value="tenant-b">tenant-b · 供应商方</option>
            </select>
          </label>
          <label>
            <span>用户名</span>
            <input v-model="loginForm.username" autocomplete="username" placeholder="例如 applicant-a" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" type="password" autocomplete="current-password" />
          </label>
          <button class="primary-button full-width" type="submit" :disabled="isBusy">
            <span>{{ isBusy ? '正在验证' : '进入工作台' }}</span><span class="button-arrow">→</span>
          </button>
        </form>
        <div class="demo-section">
          <div class="demo-heading"><strong>快速体验</strong><span>密码统一为 password123</span></div>
          <div class="demo-grid">
            <button v-for="account in demoAccounts" :key="account.username" class="demo-chip" type="button" @click="useDemoAccount(account.username)">
              <span><strong>{{ account.label }}</strong><small>{{ account.role }}</small></span><span class="demo-arrow">→</span>
            </button>
          </div>
        </div>
        <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>
      </div>
    </section>

    <section v-else class="console-layout">
      <aside class="sidebar">
        <header class="brand-lockup sidebar-brand">
          <span class="brand-mark">FM</span>
          <span class="brand-copy"><strong>FlowMesh</strong><small>运营工作台</small></span>
        </header>
        <div class="tenant-card"><span>当前租户</span><strong>{{ session?.tenantId }}</strong><small>本地演示空间</small></div>
        <nav class="nav-list" aria-label="主导航">
          <button :class="{ active: view === 'overview' }" type="button" @click="view = 'overview'">
            <span class="nav-icon">01</span><span><strong>概览</strong><small>申请与服务状态</small></span><span class="nav-chevron">›</span>
          </button>
          <button :class="{ active: view === 'submit' }" type="button" @click="view = 'submit'">
            <span class="nav-icon">02</span><span><strong>新建申请</strong><small>发起供应商准入</small></span><span class="nav-chevron">›</span>
          </button>
          <button :class="{ active: view === 'approval' }" type="button" @click="view = 'approval'">
            <span class="nav-icon">03</span><span><strong>审批工作台</strong><small>处理当前待办节点</small></span><span class="nav-chevron">›</span>
          </button>
        </nav>
        <div class="sidebar-bottom">
          <div class="service-status"><span class="status-indicator"></span><div><strong>服务运行正常</strong><small>IAM / Supplier / Workflow</small></div><span class="service-ready">READY</span></div>
          <button class="user-card" type="button" @click="logout">
            <span class="avatar">{{ session?.username.slice(0, 2).toUpperCase() }}</span>
            <span class="user-meta"><strong>{{ session?.username }}</strong><small>{{ session?.roles.join(' / ') }}</small></span>
            <span class="logout-label">退出</span>
          </button>
        </div>
      </aside>

      <div class="content-area">
        <header class="topbar">
          <div class="breadcrumb"><span>FlowMesh</span><span>/</span><strong>{{ pageTitle }}</strong></div>
          <div class="topbar-actions"><span class="connection-state"><span class="status-indicator"></span>服务已连接</span><span class="topbar-divider"></span><span class="session-label">{{ session?.username }}</span><button class="refresh-button" type="button" :disabled="isBusy" @click="loadState"><span class="refresh-icon">↻</span>刷新</button></div>
        </header>

        <div class="content-scroll">
          <div class="page-heading">
            <div><span class="page-kicker">{{ view === 'approval' ? '审批流程' : view === 'submit' ? '供应商管理' : '工作区总览' }}</span><h1>{{ pageTitle }}</h1><p>{{ pageDescription }}</p></div>
            <button v-if="view === 'overview'" class="primary-button heading-action" type="button" @click="view = 'submit'"><span>新建申请</span><span class="button-arrow">→</span></button>
          </div>

          <p v-if="noticeMessage" class="notice-banner"><span class="notice-icon">✓</span>{{ noticeMessage }}</p>
          <p v-if="errorMessage" class="error-banner workspace-error">{{ errorMessage }}</p>

          <template v-if="view === 'overview'">
            <section class="metric-row" aria-label="申请摘要">
              <article class="metric-card metric-primary"><span class="metric-label">当前申请</span><strong>{{ application ? application.status : '未选择' }}</strong><small>{{ application ? application.supplierName : '创建申请后在此查看' }}</small></article>
              <article class="metric-card"><span class="metric-label">流程进度</span><strong>{{ progress }}<sup>%</sup></strong><small>{{ workflow ? currentTaskLabel : '等待事件投影' }}</small></article>
              <article class="metric-card"><span class="metric-label">状态版本</span><strong>{{ application?.stateVersion ?? '未记录' }}</strong><small>用于并发更新控制</small></article>
            </section>

            <div class="workspace-grid">
              <article class="surface application-surface">
                <div class="surface-heading"><div><span class="section-overline">当前申请</span><h2>申请详情</h2></div><span v-if="application" class="status-pill" :class="`status-${application.status.toLowerCase()}`">{{ application.status }}</span></div>
                <div v-if="!application" class="application-empty">
                  <div class="empty-icon">＋</div><h3>还没有正在处理的申请</h3><p>创建一份供应商申请，系统会自动生成流程实例并进入审批队列。</p><button class="secondary-button" type="button" @click="view = 'submit'">创建第一份申请 <span>→</span></button>
                </div>
                <div v-else class="application-detail">
                  <div class="id-line"><span>申请编号</span><code>{{ application.id }}</code></div>
                  <div class="detail-title"><span class="supplier-avatar">{{ application.supplierName.slice(0, 1) }}</span><div><h3>{{ application.supplierName }}</h3><p>供应商准入申请</p></div></div>
                  <div class="detail-grid"><div><span>当前状态</span><strong>{{ application.status }}</strong></div><div><span>流程实例</span><strong>{{ workflow ? '已创建' : '等待创建' }}</strong></div><div><span>状态版本</span><strong>v{{ application.stateVersion }}</strong></div></div>
                  <button class="text-button" type="button" @click="view = 'approval'">查看审批进度 <span>→</span></button>
                </div>
              </article>
              <article class="surface activity-surface"><div class="surface-heading"><div><span class="section-overline">系统状态</span><h2>最近活动</h2></div><span class="stream-state"><span class="status-indicator"></span>已连接</span></div><div class="event-list"><div class="event-item"><span class="event-state success"></span><div><strong>会话已建立</strong><small>已通过 IAM 完成身份验证</small></div><time>刚刚</time></div><div class="event-item"><span class="event-state info"></span><div><strong>服务已就绪</strong><small>IAM / Supplier / Workflow</small></div><time>正常</time></div><div class="event-item"><span class="event-state"></span><div><strong>等待业务事件</strong><small>新申请提交后将显示在这里</small></div><time>等待</time></div></div></article>
            </div>
            <div class="application-selector"><label><span>载入已有申请</span><input v-model="applicationId" placeholder="粘贴申请 UUID 后回车" @keyup.enter="selectApplication" /></label><button class="secondary-button" type="button" @click="selectApplication">载入申请</button></div>
          </template>

          <template v-else-if="view === 'submit'">
            <div class="form-layout"><article class="surface form-surface"><div class="surface-heading"><div><span class="section-overline">申请信息</span><h2>供应商基本信息</h2></div><span class="step-badge">开始</span></div><form @submit.prevent="createApplication"><label class="large-label"><span>供应商名称</span><input v-model="applicationForm.supplierName" autofocus placeholder="例如：北岸精密制造有限公司" required /><small>提交后会创建申请记录，并发送 ApplicationSubmitted 事件。</small></label><div class="form-actions"><button class="secondary-button" type="button" @click="view = 'overview'">取消</button><button class="primary-button" type="submit" :disabled="isBusy || !applicationForm.supplierName.trim()"><span>{{ isBusy ? '正在提交' : '提交准入申请' }}</span><span class="button-arrow">→</span></button></div></form></article><aside class="journey-panel"><span class="journey-kicker">提交后</span><h3>从申请到启用，<br /><em>每一步都有记录。</em></h3><ul class="journey-list"><li><span>01</span><div><strong>保存申请</strong><small>写入 supplier-service</small></div></li><li><span>02</span><div><strong>创建流程</strong><small>由 RocketMQ 触发投影</small></div></li><li><span>03</span><div><strong>角色审批</strong><small>按节点权限顺序推进</small></div></li></ul></aside></div>
          </template>

          <template v-else>
            <section class="approval-summary"><div class="case-summary"><span class="supplier-avatar">{{ application?.supplierName?.slice(0, 1) ?? '?' }}</span><div><span>当前申请</span><strong>{{ application?.supplierName ?? '未选择申请' }}</strong><small>{{ application?.id ?? '请先从概览载入申请' }}</small></div></div><div class="summary-status"><span>申请状态</span><strong>{{ application?.status ?? '未开始' }}</strong></div><div class="progress-block"><div><span>流程完成度</span><strong>{{ progress }}%</strong></div><div class="progress-track"><span :style="{ width: `${progress}%` }"></span></div></div></section>
            <div class="approval-layout"><article class="surface lane-surface"><div class="surface-heading"><div><span class="section-overline">审批流程</span><h2>供应商准入轨道</h2></div><span class="workflow-id">{{ workflow?.id ? workflow.id.slice(0, 8) : '未创建' }}</span></div><div v-if="!workflow" class="workflow-empty"><div class="empty-icon">⌁</div><h3>流程实例尚未创建</h3><p>提交事件后，workflow-service 会通过 RocketMQ 创建流程投影。稍等片刻再刷新。</p><button class="secondary-button" type="button" @click="loadState">重新检查</button></div><div v-else class="lane"><div v-for="(task, index) in taskOrder" :key="task" class="lane-step" :class="{ current: workflow.currentTask === task, done: index < currentTaskIndex, last: index === taskOrder.length - 1 }"><div class="lane-rail"><span class="lane-node">{{ index < currentTaskIndex ? '✓' : String(index + 1).padStart(2, '0') }}</span></div><div class="lane-copy"><div class="lane-topline"><strong>{{ taskLabels[task] }}</strong><span>{{ taskRoles[task] }}</span></div><p>{{ index < currentTaskIndex ? '节点已完成' : workflow.currentTask === task ? '等待当前角色处理' : '等待前置节点完成' }}</p></div><span v-if="workflow.currentTask === task" class="current-tag">当前待办</span></div></div></article><aside class="surface action-surface"><span class="section-overline">下一步操作</span><h2>{{ currentTaskLabel }}</h2><p v-if="workflow?.currentTask">当前会话角色为 <strong>{{ session?.roles.join(' / ') }}</strong>，此节点需要 <strong>{{ requiredRole }}</strong> 角色。</p><p v-else>流程完成后，供应商申请会进入最终启用状态。</p><div v-if="workflow?.currentTask" class="action-decision" :class="{ allowed: canComplete }"><span class="decision-icon">{{ canComplete ? '✓' : '!' }}</span><div><strong>{{ canComplete ? '你可以处理此节点' : '等待对应角色' }}</strong><small>{{ canComplete ? '确认信息后提交审批结果' : `需要 ${requiredRole} 角色登录` }}</small></div></div><button v-if="workflow?.currentTask" class="primary-button full-width" type="button" :disabled="isBusy || !canComplete" @click="completeCurrentTask"><span>{{ canComplete ? `完成${currentTaskLabel}` : '当前角色不可操作' }}</span><span class="button-arrow">→</span></button><div v-if="workflow?.status === 'COMPLETED'" class="completed-stamp"><span>✓</span><strong>供应商已启用</strong><small>审批链已完成</small></div><div class="action-meta"><div><span>流程状态</span><strong>{{ workflow?.status ?? '未开始' }}</strong></div><div><span>流程版本</span><strong>v{{ workflow?.version ?? '未记录' }}</strong></div><div><span>创建时间</span><strong>{{ formatTime(workflow?.createdAt) }}</strong></div></div></aside></div>
          </template>
        </div>
      </div>
    </section>
  </main>
</template>
