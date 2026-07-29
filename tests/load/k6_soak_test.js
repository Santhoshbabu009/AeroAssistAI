import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend } from 'k6/metrics';

const responseTimeTrend = new Trend('response_time_trend');

const BASE_URL = __ENV.BASE_URL || 'https://aeroassistai.onrender.com';
const API_URL = `${BASE_URL}/api`;
const duration = __ENV.CI_MODE ? '10m' : '4h';

export const options = {
  scenarios: {
    soak_test: {
      executor: 'constant-vus',
      vus: 50,
      duration: duration,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    http_req_failed: ['rate<0.01'],
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
    responseTimeTrend.add(res.timings.duration);
  });

  sleep(2);
}
