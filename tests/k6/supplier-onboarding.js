// 作用：使用 k6 对 Gateway 入口执行供应商申请创建压测。
// 运行前需通过登录接口取得 Access Token，并通过 FLOWMESH_ACCESS_TOKEN 注入。
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    onboarding: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.FLOWMESH_K6_RATE || 10),
      timeUnit: '1s',
      duration: __ENV.FLOWMESH_K6_DURATION || '1m',
      preAllocatedVUs: 5,
      maxVUs: 30,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const baseUrl = __ENV.FLOWMESH_GATEWAY_URL || 'http://localhost:8080';
  const token = __ENV.FLOWMESH_ACCESS_TOKEN;
  if (!token) {
    throw new Error('FLOWMESH_ACCESS_TOKEN 未设置');
  }

  const response = http.post(
    `${baseUrl}/api/supplier/api/v1/supplier-applications`,
    JSON.stringify({ supplierName: `k6-supplier-${__VU}-${__ITER}` }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': `k6-${__VU}-${__ITER}`,
      },
    },
  );
  check(response, { '申请创建返回成功': (result) => result.status === 201 });
}
