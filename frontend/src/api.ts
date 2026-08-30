import type {
  ApiService,
  ApplicationResponse,
  TokenResponse,
  UserSession,
  WorkflowInstanceResponse,
} from './types';

interface RequestOptions {
  method?: 'GET' | 'POST';
  body?: unknown;
  authenticated?: boolean;
  headers?: Record<string, string>;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

function decodeRoles(accessToken: string): string[] {
  try {
    const payload = accessToken.split('.')[1];
    const json = JSON.parse(atob(payload.replaceAll('-', '+').replaceAll('_', '/')));
    return Array.isArray(json.roles) ? json.roles : [];
  } catch {
    return [];
  }
}

export class FlowMeshApi {
  private session: UserSession | null = null;

  get currentSession(): UserSession | null {
    return this.session;
  }

  async login(tenantId: string, username: string, password: string): Promise<UserSession> {
    const tokens = await this.request<TokenResponse>('iam', '/api/v1/auth/login', {
      method: 'POST',
      body: { tenantId, username, password },
    });
    this.session = {
      tenantId,
      username,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      roles: decodeRoles(tokens.accessToken),
    };
    return this.session;
  }

  async logout(): Promise<void> {
    if (this.session) {
      await this.request('iam', '/api/v1/auth/logout', {
        method: 'POST',
        body: { refreshToken: this.session.refreshToken },
      }).catch(() => undefined);
    }
    this.session = null;
  }

  async createApplication(supplierName: string): Promise<ApplicationResponse> {
    return this.request<ApplicationResponse>('supplier', '/api/v1/supplier-applications', {
      method: 'POST',
      body: { supplierName },
      authenticated: true,
      headers: {
        'Idempotency-Key': crypto.randomUUID(),
        'X-Trace-Id': crypto.randomUUID(),
      },
    });
  }

  async getApplication(applicationId: string): Promise<ApplicationResponse> {
    return this.request<ApplicationResponse>(
      'supplier', `/api/v1/supplier-applications/${applicationId}`, { authenticated: true },
    );
  }

  async getWorkflow(applicationId: string): Promise<WorkflowInstanceResponse> {
    return this.request<WorkflowInstanceResponse>(
      'workflow', `/api/v1/workflow-instances/${applicationId}`, { authenticated: true },
    );
  }

  async completeTask(applicationId: string, taskKey: string): Promise<WorkflowInstanceResponse> {
    return this.request<WorkflowInstanceResponse>(
      'workflow', `/api/v1/workflow-instances/${applicationId}/tasks`, {
        method: 'POST',
        body: { taskKey },
        authenticated: true,
        headers: { 'X-Trace-Id': crypto.randomUUID() },
      },
    );
  }

  private async request<T>(
    service: ApiService,
    path: string,
    options: RequestOptions = {},
  ): Promise<T> {
    const request: FlowMeshApiRequest = {
      service,
      path,
      method: options.method,
      token: options.authenticated ? this.session?.accessToken : undefined,
      body: options.body,
      headers: options.headers,
    };
    const response = window.flowmesh
      ? await window.flowmesh.request(request)
      : await fetch(`/api/${service}${path}`, {
        method: options.method ?? 'GET',
        headers: {
          Accept: 'application/json',
          ...(options.authenticated && this.session?.accessToken
            ? { Authorization: `Bearer ${this.session.accessToken}` }
            : {}),
          ...(options.headers ?? {}),
          ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
        },
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      }).then(async (browserResponse) => ({
        status: browserResponse.status,
        body: await browserResponse.text(),
      }));
    let body: Record<string, unknown> = {};
    if (response.body) {
      try {
        body = JSON.parse(response.body) as Record<string, unknown>;
      } catch {
        body = {};
      }
    }
    if (response.status >= 400) {
      throw new ApiError(
        response.status,
        typeof body.code === 'string' ? body.code : 'REQUEST_FAILED',
        typeof body.message === 'string' ? body.message : `请求失败（${response.status}）`,
      );
    }
    return body as T;
  }
}
