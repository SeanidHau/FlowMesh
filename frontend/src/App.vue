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
const hasApplication = computed(() => Boolean(applicationId.value));

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
    noticeMessage.value = '申请已写入 Outbox，等待 workflow 投影';
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
  errorMessage.value = '';
  isBusy.value = true;
  try {
    workflow.value = await api.completeTask(applicationId.value, workflow.value.currentTask);
    await refreshApplication();
    noticeMessage.value = workflow.value.status === 'COMPLETED'
      ? '审批链已完成，供应商已进入启用状态'
      : `${currentTaskLabel.value} 已完成，流程继续推进`;
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
  if (!value) return '—';
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

onMounted(() => {
  if (applicationId.value) localStorage.setItem('flowmesh.applicationId', applicationId.value);
});
</script>

<template>
  <main class="app-shell" :class="{ 'is-login': !isLoggedIn }">
    <section v-if="!isLoggedIn" class="login-layout">
      <div class="login-hero">
        <div class="brand-lockup">
          <span class="brand-mark">FM</span>
          <span>FLOWMESH</span>
        </div>
        <div class="hero-copy">
          <p class="eyebrow">SUPPLIER ONBOARDING / CONTROL ROOM</p>
          <h1>让每一次<br /><em>准入</em>都有迹可循。</h1>
          <p class="hero-note">面向多租户采购团队的供应商准入与审批控制台。事件、权限和状态，保持在同一条线上。</p>
        </div>
        <div class="hero-footer">
          <span class="signal-dot"></span>
          <span>LOCAL CONTROL PLANE</span>
          <span class="hero-version">v0.1 / DESKTOP</span>
        </div>
      </div>

      <div class="login-panel">
        <div class="panel-kicker">IDENTITY GATEWAY <span>01</span></div>
        <h2>进入工作台</h2>
        <p class="muted">使用 IAM 服务签发的 Access Token 开始本地演示。</p>
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
            {{ isBusy ? '正在验证…' : '进入控制室' }} <span>↗</span>
          </button>
        </form>
        <div class="demo-section">
          <div class="section-label">演示身份 · 密码统一为 password123</div>
          <div class="demo-grid">
            <button v-for="account in demoAccounts" :key="account.username" class="demo-chip" type="button" @click="useDemoAccount(account.username)">
              <span>{{ account.label }}</span><strong>{{ account.username }}</strong>
            </button>
          </div>
        </div>
        <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>
      </div>
    </section>

    <section v-else class="console-layout">
      <aside class="sidebar">
        <div class="brand-lockup sidebar-brand"><span class="brand-mark">FM</span><span>FLOWMESH</span></div>
        <div class="sidebar-rule"></div>
        <div class="workspace-label">WORKSPACE <span>LOCAL</span></div>
        <nav class="nav-list" aria-label="主导航">
          <button :class="{ active: view === 'overview' }" type="button" @click="view = 'overview'">
            <span class="nav-index">01</span><span>控制台</span><span class="nav-arrow">↗</span>
          </button>
          <button :class="{ active: view === 'submit' }" type="button" @click="view = 'submit'">
            <span class="nav-index">02</span><span>新建申请</span><span class="nav-arrow">↗</span>
          </button>
          <button :class="{ active: view === 'approval' }" type="button" @click="view = 'approval'">
            <span class="nav-index">03</span><span>审批轨道</span><span class="nav-arrow">↗</span>
          </button>
        </nav>
        <div class="sidebar-bottom">
          <div class="service-status"><span class="signal-dot"></span><span>API SERVICES</span><strong>READY</strong></div>
          <button class="user-card" type="button" @click="logout">
            <span class="avatar">{{ session?.username.slice(0, 2).toUpperCase() }}</span>
            <span class="user-meta"><strong>{{ session?.username }}</strong><small>{{ session?.tenantId }}</small></span>
            <span class="logout-icon">⇥</span>
          </button>
        </div>
      </aside>

      <div class="content-area">
        <header class="topbar">
          <div><span class="live-badge"><i></i> LIVE SESSION</span><span class="topbar-separator">/</span><span class="topbar-context">{{ session?.roles.join(' · ') }}</span></div>
          <div class="topbar-meta"><span>30 AUG 2026</span><span class="topbar-separator">·</span><span>SHANGHAI / CN</span></div>
        </header>

        <div class="content-scroll">
          <div class="page-heading">
            <div>
              <p class="eyebrow">{{ view === 'approval' ? 'WORKFLOW / APPROVAL LANE' : view === 'submit' ? 'SUPPLIER / NEW INTAKE' : 'OPERATIONS / OVERVIEW' }}</p>
              <h1>{{ view === 'approval' ? '审批轨道' : view === 'submit' ? '发起一份申请' : '早上好，' + session?.username }}</h1>
            </div>
            <button class="refresh-button" type="button" :disabled="isBusy" @click="loadState">↻ <span>刷新状态</span></button>
          </div>

          <p v-if="noticeMessage" class="notice-banner"><span>✦</span>{{ noticeMessage }}</p>
          <p v-if="errorMessage" class="error-banner workspace-error">{{ errorMessage }}</p>

          <template v-if="view === 'overview'">
            <div class="metric-row">
              <article class="metric-card accent-card"><span class="metric-label">CURRENT APPLICATION</span><strong>{{ application ? application.status : '—' }}</strong><small>{{ application ? '申请状态' : '尚未选择申请' }}</small></article>
              <article class="metric-card"><span class="metric-label">WORKFLOW PROGRESS</span><strong>{{ progress }}<sup>%</sup></strong><small>{{ workflow ? currentTaskLabel : '等待事件投影' }}</small></article>
              <article class="metric-card"><span class="metric-label">STATE VERSION</span><strong>{{ application?.stateVersion ?? '—' }}</strong><small>持久化乐观锁</small></article>
            </div>
            <div class="workspace-grid">
              <article class="surface application-surface">
                <div class="surface-heading"><div><span class="panel-kicker">SELECTED CASE <span>↘</span></span><h2>申请详情</h2></div><span v-if="application" class="status-pill">{{ application.status }}</span></div>
                <div class="application-empty" v-if="!application">
                  <div class="empty-mark">＋</div><h3>还没有正在处理的申请</h3><p>创建一份供应商申请，观察它如何穿过 Outbox 和审批轨道。</p><button class="primary-button" type="button" @click="view = 'submit'">创建第一份申请 <span>↗</span></button>
                </div>
                <div v-else class="application-detail">
                  <div class="id-line"><span>APPLICATION ID</span><code>{{ application.id }}</code></div>
                  <div class="detail-title"><span class="supplier-avatar">{{ application.supplierName.slice(0, 1) }}</span><div><h3>{{ application.supplierName }}</h3><p>供应商准入申请</p></div></div>
                  <div class="detail-grid"><div><span>当前状态</span><strong>{{ application.status }}</strong></div><div><span>流程实例</span><strong>{{ workflow ? '已投影' : '等待投影' }}</strong></div><div><span>状态版本</span><strong>v{{ application.stateVersion }}</strong></div></div>
                  <button class="text-button" type="button" @click="view = 'approval'">打开审批轨道 <span>→</span></button>
                </div>
              </article>
              <article class="surface event-surface"><div class="surface-heading"><div><span class="panel-kicker">EVENT STREAM <span>03</span></span><h2>最近动作</h2></div><span class="stream-live"><i></i> CONNECTED</span></div><div class="event-list"><div class="event-item"><span class="event-dot lime"></span><div><strong>桌面会话已建立</strong><small>Access Token · {{ session?.tenantId }}</small></div><time>NOW</time></div><div class="event-item"><span class="event-dot cyan"></span><div><strong>跨服务 API 已就绪</strong><small>IAM / SUPPLIER / WORKFLOW</small></div><time>READY</time></div><div class="event-item muted-event"><span class="event-dot"></span><div><strong>等待下一条业务事件</strong><small>RocketMQ consumer 状态由服务端决定</small></div><time>—</time></div></div></article>
            </div>
            <div class="application-selector"><label><span>已有申请 ID</span><input v-model="applicationId" placeholder="粘贴 UUID 后回车载入" @keyup.enter="selectApplication" /></label><button class="secondary-button" type="button" @click="selectApplication">载入申请</button></div>
          </template>

          <template v-else-if="view === 'submit'">
            <div class="form-layout"><article class="surface form-surface"><div class="surface-heading"><div><span class="panel-kicker">INTAKE FORM <span>02</span></span><h2>供应商基本信息</h2></div><span class="form-step">01 / 01</span></div><form @submit.prevent="createApplication"><label class="large-label"><span>供应商名称</span><input v-model="applicationForm.supplierName" autofocus placeholder="例如：北岸精密制造有限公司" required /><small>此字段会作为 ApplicationSubmitted 事件的聚合数据写入 Outbox。</small></label><div class="form-actions"><button class="secondary-button" type="button" @click="view = 'overview'">取消</button><button class="primary-button" type="submit" :disabled="isBusy || !applicationForm.supplierName.trim()">{{ isBusy ? '正在提交…' : '提交准入申请' }} <span>↗</span></button></div></form></article><aside class="form-aside"><span class="aside-number">01</span><h3>一次提交，<br /><em>全程可见。</em></h3><p>申请在 supplier-service 中落库，同时生成持久化幂等记录和 RocketMQ Outbox 事件。</p><div class="aside-line"></div><span class="aside-caption">IDEMPOTENCY / OUTBOX / RLS</span></aside></div>
          </template>

          <template v-else>
            <div class="approval-header"><div class="case-chip"><span class="supplier-avatar">{{ application?.supplierName?.slice(0, 1) ?? '?' }}</span><div><span>SELECTED CASE</span><strong>{{ application?.supplierName ?? '未选择申请' }}</strong></div></div><div class="progress-block"><div><span>链路完成度</span><strong>{{ progress }}%</strong></div><div class="progress-track"><span :style="{ width: `${progress}%` }"></span></div></div></div>
            <div class="approval-layout"><article class="surface lane-surface"><div class="surface-heading"><div><span class="panel-kicker">APPROVAL LANE <span>LIVE</span></span><h2>供应商准入轨道</h2></div><span class="workflow-id">{{ workflow?.id ? workflow.id.slice(0, 8) : 'NO INSTANCE' }}</span></div><div v-if="!workflow" class="workflow-empty"><div class="empty-mark">⌁</div><h3>流程实例尚未到达</h3><p>提交事件后，workflow-service 会通过 RocketMQ 创建流程投影。稍等片刻再刷新。</p><button class="secondary-button" type="button" @click="loadState">重新检查</button></div><div v-else class="lane"><div v-for="(task, index) in taskOrder" :key="task" class="lane-step" :class="{ current: workflow.currentTask === task, done: index < currentTaskIndex, last: index === taskOrder.length - 1 }"><div class="lane-rail"><span class="lane-node">{{ index < currentTaskIndex ? '✓' : String(index + 1).padStart(2, '0') }}</span></div><div class="lane-copy"><div class="lane-topline"><span>{{ taskLabels[task] }}</span><small>{{ taskRoles[task] }}</small></div><strong>{{ index < currentTaskIndex ? '已完成' : workflow.currentTask === task ? '等待当前角色处理' : '排队中' }}</strong><p v-if="workflow.currentTask === task">需要 {{ taskRoles[task] }} 角色完成该节点</p></div><span v-if="workflow.currentTask === task" class="current-tag">CURRENT</span></div></div></article><aside class="surface action-surface"><span class="panel-kicker">TASK ACTION <span>↗</span></span><h2>{{ currentTaskLabel }}</h2><p v-if="workflow?.currentTask">当前会话角色为 <strong>{{ session?.roles.join(' / ') }}</strong>，此节点要求 <strong>{{ requiredRole }}</strong>。</p><p v-else>流程完成后，供应商申请会进入最终启用状态。</p><button v-if="workflow?.currentTask" class="primary-button full-width" type="button" :disabled="isBusy || !canComplete" @click="completeCurrentTask">{{ canComplete ? `完成${currentTaskLabel}` : '当前角色不可操作' }} <span>↗</span></button><div v-if="workflow?.status === 'COMPLETED'" class="completed-stamp">✓ ENABLED<span>审批链已完成</span></div><div class="action-meta"><div><span>流程状态</span><strong>{{ workflow?.status ?? 'NOT_STARTED' }}</strong></div><div><span>版本</span><strong>v{{ workflow?.version ?? '—' }}</strong></div><div><span>创建时间</span><strong>{{ formatTime(workflow?.createdAt) }}</strong></div></div></aside></div>
          </template>
        </div>
      </div>
    </section>
  </main>
</template>
