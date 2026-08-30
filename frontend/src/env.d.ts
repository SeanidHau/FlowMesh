interface FlowMeshApiRequest {
  service: 'iam' | 'supplier' | 'workflow';
  path: string;
  method?: 'GET' | 'POST';
  token?: string;
  body?: unknown;
  headers?: Record<string, string>;
}

interface FlowMeshApiResponse {
  status: number;
  body: string;
}

interface Window {
  flowmesh: {
    request(request: FlowMeshApiRequest): Promise<FlowMeshApiResponse>;
  };
}

/// <reference types="vite/client" />
