import http from 'k6/http';
import { check, sleep, group } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://aeroassistai.onrender.com';
const API_URL = `${BASE_URL}/api`;

export const options = {
  scenarios: {
    spike_test: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '1m', target: 10 },
        { duration: '0s', target: 500 },
        { duration: '30s', target: 500 },
        { duration: '0s', target: 10 },
        { duration: '1m', target: 10 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.20'],
  },
};

export function setup() {
  return { username: 'testuser', password: 'password' };
}

export default function (data) {
  const headers = { 'Content-Type': 'application/json' };

  group('Flight Search', function () {
    const payload = JSON.stringify({ origin: 'JFK', destination: 'LHR', date: '2024-12-01' });
    const res = http.post(`${API_URL}/search_flights`, payload, { headers: headers });
    check(res, { 'status is 200': (r) => r.status === 200 });
  });

  sleep(1);
}
