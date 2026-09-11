export type ApiService = 'iam' | 'supplier' | 'workflow' | 'notification';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface ApplicationResponse {
  id: string;
  supplierName: string;
  status: string;
  stateVersion: number;
}

export interface SupplierDocumentResponse {
  id: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  scanStatus: string;
  createdAt: string;
}

export interface DocumentDownloadResponse {
  url: string;
  expiresAt: string;
}

export interface NotificationResponse {
  id: string;
  type: string;
  title: string;
  content: string;
  status: string;
  createdAt: string;
}

export interface WorkflowInstanceResponse {
  id: string;
  applicationId: string;
  tenantId: string;
  processDefinitionKey: string;
  status: string;
  currentTask: string | null;
  availableTasks: string[];
  completedTasks: string[];
  version: number;
  createdAt: string;
}

export interface UserSession {
  tenantId: string;
  username: string;
  accessToken: string;
  refreshToken: string;
  roles: string[];
}

export const taskLabels: Record<string, string> = {
  PURCHASER_REVIEW: '采购初审',
  LEGAL_REVIEW: '法务会签',
  FINANCE_REVIEW: '财务会签',
  OPERATIONS_ACTIVATION: '运营启用',
};

export const taskRoles: Record<string, string> = {
  PURCHASER_REVIEW: 'PURCHASER',
  LEGAL_REVIEW: 'LEGAL',
  FINANCE_REVIEW: 'FINANCE',
  OPERATIONS_ACTIVATION: 'OPERATIONS',
};

export const taskOrder = [
  'PURCHASER_REVIEW',
  'LEGAL_REVIEW',
  'FINANCE_REVIEW',
  'OPERATIONS_ACTIVATION',
];
