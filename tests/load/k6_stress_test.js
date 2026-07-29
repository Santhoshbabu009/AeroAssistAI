import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';

const BASE_URL = __ENV.BASE_URL || 'https://aeroassistai.onrender.com';
const API_URL = `${BASE_URL}/api`;

export const options = {
  scenarios: {
    stress_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10m', target: 1000 },
        { duration: '5m', target: 1000 },
        { duration: '5m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.15'],
  },
};

export function setup() {
  return { username: 'testuser', password: 'password' };
}

export default function (data) {
  const headers = { 'Content-Type': 'application/json' };

  group('Authentication', function () {
    const payload = JSON.stringify({ username: data.username, password: data.password });
    const res = http.post(`${API_URL}/login`, payload, { headers: headers });
    check(res, { 'status is 200': (r) => r.status === 200 });
  });
  
  sleep(Math.random() * 2 + 1);

  group('Flight Search', function () {
    const payload = JSON.stringify({ origin: 'JFK', destination: 'LHR', date: '2024-12-01' });
    const res = http.post(`${API_URL}/search_flights`, payload, { headers: headers });
    check(res, { 'status is 200': (r) => r.status === 200 });
  });

  sleep(Math.random() * 2 + 1);
}

export function handleSummary(data) {
  return {
    "stress_report.html": htmlReport(data),
    "stress_results.json": JSON.stringify(data)
  };
}
