import type { AccountDetail } from './accountApi';
export const accountFixture: AccountDetail = {
  user: { userId: 10, fullName: 'Mai Anh', email: 'mai@example.invalid', phoneNumber: null, roles: ['BUYER', 'SELLER'], status: 'ACTIVE', authenticationStatus: 'ACTIVE', version: 2, reputation: 100, activeRestrictions: 0, createdAt: '2026-10-01T10:00:00Z' },
  reason: null, expiresAt: null, restrictions: [], orders: [], cases: [], reputation: { score: 100, events: [] }, history: [], historyTotal: 0,
  allowedScopes: ['NEW_LISTINGS', 'CHAT', 'BUY_EKYC', 'NEW_TRANSACTIONS'], allowedActions: ['RESTRICT', 'LOCK', 'DEACTIVATE', 'ACTIVATE', 'LIFT', 'COMPENSATE'], allowedWeeks: [1, 2, 3], outages: [], serverTime: '2026-10-12T10:00:00Z',
};
