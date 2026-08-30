export type ApiService = 'iam' | 'supplier' | 'workflow';

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

export interface WorkflowInstanceResponse {
  id: string;
  applicationId: string;
  tenantId: string;
  processDefinitionKey: string;
  status: string;
  currentTask: string | null;
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
