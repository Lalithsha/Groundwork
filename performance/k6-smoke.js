import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: { evidence: { executor: 'constant-vus', vus: Number(__ENV.VUS || 5), duration: __ENV.DURATION || '30s' } },
  thresholds: {
    http_req_failed: ['rate<0.005'],
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{name:changes}': ['p(95)<500'],
    'http_req_duration{name:evidence-search}': ['p(95)<750']
  }
};

const apiUrl = __ENV.API_URL || 'http://localhost:8080';

export default function () {
  if (!__ENV.WORKSPACE_ID) throw new Error('WORKSPACE_ID is required');
  const headers = { 'Content-Type': 'application/json' };
  if (__ENV.ACCESS_TOKEN) headers.Authorization = `Bearer ${__ENV.ACCESS_TOKEN}`;
  const changes = http.get(`${apiUrl}/api/workspaces/${__ENV.WORKSPACE_ID}/changes?limit=25`, {
    headers, tags: { name: 'changes' }
  });
  const evidence = http.get(`${apiUrl}/api/workspaces/${__ENV.WORKSPACE_ID}/evidence/search?query=rollback&limit=10`, {
    headers, tags: { name: 'evidence-search' }
  });
  const analytics = http.get(`${apiUrl}/api/workspaces/${__ENV.WORKSPACE_ID}/analytics/summary`, {
    headers, tags: { name: 'analytics' }
  });
  check(changes, { 'changes return 200': value => value.status === 200 });
  check(evidence, { 'evidence search returns 200': value => value.status === 200 });
  check(analytics, { 'analytics return 200': value => value.status === 200 });
  sleep(1);
}
