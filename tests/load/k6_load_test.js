import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';

// Custom metrics
const loginErrorRate = new Rate('login_errors');
const bookingErrorRate = new Rate('booking_errors');
const loginDuration = new Trend('login_duration', true);
const bookingDuration = new Trend('booking_duration', true);

const BASE_URL = __ENV.BASE_URL || 'https://aeroassistai.onrender.com';
const API_URL = `${BASE_URL}/api`;

export const options = {
  scenarios: {
    smoke_test: { executor: 'constant-vus', vus: 5, duration: '30s', tags: {scenario: 'smoke'} },
    load_test_10: { executor: 'ramping-vus', startVUs: 0, stages: [{duration: '30s', target: 10},{duration: '1m', target: 10},{duration: '30s', target: 0}], tags: {scenario: 'load_10'} },
    load_test_50: { executor: 'ramping-vus', startVUs: 0, stages: [{duration: '30s', target: 50},{duration: '2m', target: 50},{duration: '30s', target: 0}], startTime: '3m', tags: {scenario: 'load_50'} },
    load_test_100: { executor: 'ramping-vus', startVUs: 0, stages: [{duration: '1m', target: 100},{duration: '3m', target: 100},{duration: '1m', target: 0}], startTime: '7m', tags: {scenario: 'load_100'} },
    load_test_250: { executor: 'ramping-vus', startVUs: 0, stages: [{duration: '1m', target: 250},{duration: '3m', target: 250},{duration: '1m', target: 0}], startTime: '13m', tags: {scenario: 'load_250'} },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed: ['rate<0.05'],
    login_errors: ['rate<0.05'],
    booking_errors: ['rate<0.10'],
  },
};

export function setup() {
  const payload = JSON.stringify({
    username: 'testuser',
    password: 'password'
  });
  const headers = { 'Content-Type': 'application/json' };
  http.post(`${API_URL}/register`, payload, { headers: headers });
  return { username: 'testuser', password: 'password' };
}

export default function (data) {
  const headers = { 'Content-Type': 'application/json' };

  group('Authentication', function () {
    const payload = JSON.stringify({
      username: data.username,
      password: data.password,
    });
    const res = http.post(`${API_URL}/login`, payload, { headers: headers });
    const success = check(res, {
      'status is 200': (r) => r.status === 200,
    });
    loginErrorRate.add(!success);
    loginDuration.add(res.timings.duration);
  });
  
  sleep(Math.random() * 2 + 1);

  group('Flight Search', function () {
    const payload = JSON.stringify({ origin: 'JFK', destination: 'LHR', date: '2024-12-01' });
    const res = http.post(`${API_URL}/search_flights`, payload, { headers: headers });
    check(res, { 'status is 200': (r) => r.status === 200 });
  });

  sleep(Math.random() * 2 + 1);

  group('Booking Flow', function () {
    const payload = JSON.stringify({ flight_id: 'FL123', passenger_name: 'John Doe' });
    const res = http.post(`${API_URL}/book_flight`, payload, { headers: headers });
    const success = check(res, { 'status is 200': (r) => r.status === 200 });
    bookingErrorRate.add(!success);
    bookingDuration.add(res.timings.duration);
  });

  sleep(Math.random() * 2 + 1);

  group('Chat', function () {
    const payload = JSON.stringify({ message: 'Hello bot' });
    const res = http.post(`${API_URL}/send_chat_message`, payload, { headers: headers });
    check(res, { 'status is 200': (r) => r.status === 200 });
  });
  
  sleep(Math.random() * 2 + 1);
}

export function handleSummary(data) {
  return {
    "load_report.html": htmlReport(data),
    "results.json": JSON.stringify(data)
  };
}
