import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: { retrieval: { executor: 'constant-vus', vus: Number(__ENV.VUS || 5), duration: __ENV.DURATION || '30s' } },
  thresholds: { http_req_failed: ['rate<0.005'], http_req_duration: ['p(95)<1000'] }
};

const apiUrl = __ENV.API_URL || 'http://localhost:8080';

export default function () {
  if (!__ENV.WORKSPACE_ID) throw new Error('WORKSPACE_ID is required');
  const headers = { 'Content-Type': 'application/json' };
  if (__ENV.ACCESS_TOKEN) headers.Authorization = `Bearer ${__ENV.ACCESS_TOKEN}`;
  const response = http.post(`${apiUrl}/api/chat`, JSON.stringify({ question: 'Summarize the retry policy.', retrievalMode: 'hybrid', workspaceId: __ENV.WORKSPACE_ID }), { headers });
  check(response, { 'chat returns 200': value => value.status === 200, 'answer has evidence status': value => value.json('evidenceStatus') !== undefined });
  sleep(1);
}
