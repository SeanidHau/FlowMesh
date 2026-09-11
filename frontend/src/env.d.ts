interface FlowMeshApiRequest {
  service: 'iam' | 'supplier' | 'workflow' | 'notification';
  path: string;
  method?: 'GET' | 'POST' | 'PUT';
  token?: string;
  body?: unknown;
  file?: { name: string; type: string; data: ArrayBuffer };
  headers?: Record<string, string>;
}

interface FlowMeshApiResponse {
  status: number;
  body: string;
}

interface Window {
  flowmesh?: {
    request(request: FlowMeshApiRequest): Promise<FlowMeshApiResponse>;
  };
}

/// <reference types="vite/client" />
