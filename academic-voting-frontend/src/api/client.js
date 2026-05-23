/**
 * API client — all calls go through the Vite proxy (/api → localhost:8080).
 * Automatically attaches the JWT Bearer token from localStorage.
 */

const BASE = '/api';

async function request(endpoint, options = {}) {
  const token = localStorage.getItem('av_token');

  const res = await fetch(`${BASE}${endpoint}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
    ...options,
  });

  const json = await res.json().catch(() => ({}));

  if (!res.ok) {
    const msg = json?.message || `HTTP ${res.status}`;
    throw new Error(msg);
  }

  return json;
}

// ── Auth ─────────────────────────────────────────────────────────────────────
export const login = (userId, password) =>
  request('/auth/login', { method: 'POST', body: JSON.stringify({ userId, password }) });

export const getVotingCredential = (pollId) =>
  request('/auth/voting-credential', { method: 'POST', body: JSON.stringify({ pollId }) });

export const getMe = () => request('/auth/me');

// ── Polls ─────────────────────────────────────────────────────────────────────
export const getPollCount  = ()               => request('/polls/count');
export const getPollInfo   = (pollId)         => request(`/polls/${pollId}/info`);
export const getPollResults = (pollId)        => request(`/polls/${pollId}/results`);
export const getHasVoted   = (pollId, addr)   => request(`/polls/${pollId}/voted/${addr}`);

export const castVote = (pollId, voterPrivateKey, optionIndex) =>
  request(`/polls/${pollId}/vote`, {
    method: 'POST',
    body: JSON.stringify({ voterPrivateKey, optionIndex }),
  });

// ── Admin ─────────────────────────────────────────────────────────────────────
export const getAdminInfo = () => request('/admin/info');

export const createPoll = (question, options, durationSeconds, startTime = 0) =>
  request('/polls', {
    method: 'POST',
    body: JSON.stringify({ question, options, durationSeconds, startTime }),
  });
