/**
 * Resource functions for every v1 endpoint the UI uses.
 * Paths, methods, and query parameters mirror docs/API_CONTRACT.md.
 */
import { del, get, newIdempotencyKey, pageParams, patch, post, put } from './client'
import type {
  AiConversation,
  AiProposal,
  AiStatus,
  AuditEvent,
  ChatResponse,
  Contribution,
  ConversationDetail,
  CreditEstimate,
  Group,
  GroupRole,
  GroupTotals,
  Installment,
  Invitation,
  InvitationCreated,
  JoinRequest,
  LedgerEntry,
  LedgerSummary,
  Loan,
  Meeting,
  MeetingNote,
  Member,
  NotificationRecord,
  Page,
  Participant,
  Preferences,
  Profile,
  Repayment,
  Schedule,
  StatementDocument,
  StatementPolicy,
  StatementRequest,
  TokenResponse,
  VerificationStatus,
  Vote,
  VoteResults,
  Consent,
} from './types'

export { newIdempotencyKey }

/* ---------- auth ---------- */

export function register(input: {
  email: string
  password: string
  displayName: string
  phone?: string
}): Promise<TokenResponse> {
  return post('/api/v1/auth/register', input, { auth: false })
}

export function login(input: { email: string; password: string }): Promise<TokenResponse> {
  return post('/api/v1/auth/login', input, { auth: false })
}

export function logout(refreshToken: string): Promise<void> {
  return post('/api/v1/auth/logout', { refreshToken }, { auth: false })
}

/* ---------- users ---------- */

export const users = {
  me: () => get<Profile>('/api/v1/users/me'),
  updateMe: (input: { displayName?: string; phone?: string }) =>
    patch<Profile>('/api/v1/users/me', input),
  preferences: () => get<Preferences>('/api/v1/users/me/preferences'),
  updatePreferences: (input: { emailEnabled?: boolean; smsEnabled?: boolean; locale?: string }) =>
    patch<Preferences>('/api/v1/users/me/preferences', input),
  /** Latest decision per purpose, keyed by purpose name. */
  consents: () => get<Record<string, Consent>>('/api/v1/users/me/consents'),
  setConsent: (purpose: string, granted: boolean) =>
    post<Consent>('/api/v1/users/me/consents', { purpose, granted }),
}

/* ---------- groups ---------- */

export const groups = {
  list: (page = 0) => get<Page<Group>>(`/api/v1/groups?${pageParams(page)}`),
  create: (input: { name: string; description?: string; currency: string }) =>
    post<Group>('/api/v1/groups', input),
  detail: (groupId: string) => get<Group>(`/api/v1/groups/${groupId}`),
  update: (groupId: string, input: { name?: string; description?: string }) =>
    patch<Group>(`/api/v1/groups/${groupId}`, input),
  members: (groupId: string, page = 0) =>
    get<Page<Member>>(`/api/v1/groups/${groupId}/members?${pageParams(page)}`),
  roles: (groupId: string) => get<GroupRole[]>(`/api/v1/groups/${groupId}/roles`),
  createRole: (groupId: string, input: { name: string; permissions: string[] }) =>
    post<GroupRole>(`/api/v1/groups/${groupId}/roles`, input),
  updateRole: (groupId: string, roleId: string, permissions: string[]) =>
    patch<GroupRole>(`/api/v1/groups/${groupId}/roles/${roleId}`, { permissions }),
  deleteRole: (groupId: string, roleId: string) =>
    del<void>(`/api/v1/groups/${groupId}/roles/${roleId}`),
  changeRole: (groupId: string, userId: string, roleId: string) =>
    patch<Member>(`/api/v1/groups/${groupId}/members/${userId}/role`, { roleId }),
  removeMember: (groupId: string, userId: string) =>
    del<void>(`/api/v1/groups/${groupId}/members/${userId}`),
  leave: (groupId: string) => del<void>(`/api/v1/groups/${groupId}/members/me`),
  transferAdmin: (groupId: string, newAdminUserId: string) =>
    post<void>(`/api/v1/groups/${groupId}/admin-transfer`, { newAdminUserId }),
}

/* ---------- membership ---------- */

export const membership = {
  requestJoin: (groupId: string, message?: string) =>
    post<JoinRequest>(`/api/v1/groups/${groupId}/join-requests`, { message }),
  listRequests: (groupId: string, status?: string, page = 0) =>
    get<Page<JoinRequest>>(
      `/api/v1/groups/${groupId}/join-requests?${pageParams(page)}${status ? `&status=${status}` : ''}`,
    ),
  approve: (groupId: string, requestId: string, roleId?: string) =>
    post<JoinRequest>(`/api/v1/groups/${groupId}/join-requests/${requestId}/approve`, { roleId }),
  reject: (groupId: string, requestId: string, reason?: string) =>
    post<JoinRequest>(`/api/v1/groups/${groupId}/join-requests/${requestId}/reject`, { reason }),
  cancel: (groupId: string, requestId: string) =>
    del<void>(`/api/v1/groups/${groupId}/join-requests/${requestId}`),
  createInvitation: (
    groupId: string,
    input: { roleId?: string; expiresInHours?: number; maxUses?: number },
  ) => post<InvitationCreated>(`/api/v1/groups/${groupId}/invitations`, input),
  listInvitations: (groupId: string, page = 0) =>
    get<Page<Invitation>>(`/api/v1/groups/${groupId}/invitations?${pageParams(page)}`),
  revokeInvitation: (groupId: string, invitationId: string) =>
    del<void>(`/api/v1/groups/${groupId}/invitations/${invitationId}`),
  acceptInvitation: (code: string) =>
    post<{ groupId: string }>('/api/v1/invitations/accept', { code }),
}

/* ---------- finance ---------- */

export const finance = {
  createSchedule: (
    groupId: string,
    input: {
      title: string
      amount: number
      frequency: string
      dueDay?: number
      startDate: string
      endDate?: string
    },
  ) => post<Schedule>(`/api/v1/groups/${groupId}/contribution-schedules`, input),
  schedules: (groupId: string, page = 0) =>
    get<Page<Schedule>>(`/api/v1/groups/${groupId}/contribution-schedules?${pageParams(page)}`),
  recordContribution: (
    groupId: string,
    input: {
      memberUserId?: string
      scheduleId?: string
      periodStart: string
      periodEnd: string
      amount: number
      assessment?: boolean
      notes?: string
    },
    idempotencyKey: string,
  ) => post<Contribution>(`/api/v1/groups/${groupId}/contributions`, input, { idempotencyKey }),
  contributions: (groupId: string, opts: { memberUserId?: string; status?: string; page?: number } = {}) =>
    get<Page<Contribution>>(
      `/api/v1/groups/${groupId}/contributions?${pageParams(opts.page ?? 0)}` +
        `${opts.memberUserId ? `&memberUserId=${opts.memberUserId}` : ''}` +
        `${opts.status ? `&status=${opts.status}` : ''}`,
    ),
  contribution: (groupId: string, contributionId: string) =>
    get<Contribution>(`/api/v1/groups/${groupId}/contributions/${contributionId}`),
  updateContribution: (
    groupId: string,
    contributionId: string,
    input: { amount?: number; periodStart?: string; periodEnd?: string; notes?: string },
  ) => patch<Contribution>(`/api/v1/groups/${groupId}/contributions/${contributionId}`, input),
  confirmContribution: (groupId: string, contributionId: string, occurredAt?: string) =>
    post<Contribution>(`/api/v1/groups/${groupId}/contributions/${contributionId}/confirm`, {
      occurredAt,
    }),
  rejectContribution: (groupId: string, contributionId: string, reason?: string) =>
    post<Contribution>(`/api/v1/groups/${groupId}/contributions/${contributionId}/reject`, { reason }),
  cancelContribution: (groupId: string, contributionId: string) =>
    post<Contribution>(`/api/v1/groups/${groupId}/contributions/${contributionId}/cancel`, {}),
  adjustContribution: (groupId: string, contributionId: string, amount: number, reason: string) =>
    post<Contribution>(`/api/v1/groups/${groupId}/contributions/${contributionId}/adjust`, {
      amount,
      reason,
    }),
  ledgerSummary: (groupId: string) =>
    get<LedgerSummary>(`/api/v1/groups/${groupId}/ledger/summary`),
  ledgerEntries: (groupId: string, page = 0) =>
    get<Page<LedgerEntry>>(`/api/v1/groups/${groupId}/ledger/entries?${pageParams(page)}`),
}

/* ---------- loans ---------- */

export const loans = {
  request: (
    groupId: string,
    input: { principal: number; interestRate?: number; termMonths: number; purpose?: string },
    idempotencyKey: string,
  ) => post<Loan>(`/api/v1/groups/${groupId}/loans`, input, { idempotencyKey }),
  list: (groupId: string, page = 0) =>
    get<Page<Loan>>(`/api/v1/groups/${groupId}/loans?${pageParams(page)}`),
  detail: (groupId: string, loanId: string) =>
    get<Loan>(`/api/v1/groups/${groupId}/loans/${loanId}`),
  approve: (groupId: string, loanId: string) =>
    post<Loan>(`/api/v1/groups/${groupId}/loans/${loanId}/approve`, {}),
  reject: (groupId: string, loanId: string, reason: string) =>
    post<Loan>(`/api/v1/groups/${groupId}/loans/${loanId}/reject`, { reason }),
  cancel: (groupId: string, loanId: string) =>
    post<Loan>(`/api/v1/groups/${groupId}/loans/${loanId}/cancel`, {}),
  disburse: (groupId: string, loanId: string) =>
    post<Loan>(`/api/v1/groups/${groupId}/loans/${loanId}/disburse`, {}),
  markDefaulted: (groupId: string, loanId: string, reason: string) =>
    post<Loan>(`/api/v1/groups/${groupId}/loans/${loanId}/mark-defaulted`, { reason }),
  reactivate: (groupId: string, loanId: string) =>
    post<Loan>(`/api/v1/groups/${groupId}/loans/${loanId}/reactivate`, {}),
  schedule: (groupId: string, loanId: string) =>
    get<Installment[]>(`/api/v1/groups/${groupId}/loans/${loanId}/schedule`),
  submitRepayment: (groupId: string, loanId: string, amount: number, idempotencyKey: string) =>
    post<Repayment>(`/api/v1/groups/${groupId}/loans/${loanId}/repayments`, { amount }, { idempotencyKey }),
  repayments: (groupId: string, loanId: string, page = 0) =>
    get<Page<Repayment>>(`/api/v1/groups/${groupId}/loans/${loanId}/repayments?${pageParams(page)}`),
  confirmRepayment: (groupId: string, loanId: string, repaymentId: string) =>
    post<Repayment>(
      `/api/v1/groups/${groupId}/loans/${loanId}/repayments/${repaymentId}/confirm`,
      {},
    ),
  rejectRepayment: (groupId: string, loanId: string, repaymentId: string, reason?: string) =>
    post<Repayment>(
      `/api/v1/groups/${groupId}/loans/${loanId}/repayments/${repaymentId}/reject`,
      { reason },
    ),
}

/* ---------- meetings ---------- */

export interface MeetingInput {
  title: string
  agenda?: string
  startsAt: string
  endsAt?: string
  timezone: string
  location?: string
  meetingLink?: string
  invitedUserIds?: string[]
}

export const meetings = {
  create: (groupId: string, input: MeetingInput) =>
    post<Meeting>(`/api/v1/groups/${groupId}/meetings`, input),
  list: (groupId: string, page = 0) =>
    get<Page<Meeting>>(`/api/v1/groups/${groupId}/meetings?${pageParams(page)}`),
  detail: (groupId: string, meetingId: string) =>
    get<Meeting>(`/api/v1/groups/${groupId}/meetings/${meetingId}`),
  update: (groupId: string, meetingId: string, input: Partial<MeetingInput>) =>
    patch<Meeting>(`/api/v1/groups/${groupId}/meetings/${meetingId}`, input),
  complete: (groupId: string, meetingId: string) =>
    post<Meeting>(`/api/v1/groups/${groupId}/meetings/${meetingId}/complete`, {}),
  cancel: (groupId: string, meetingId: string) =>
    post<Meeting>(`/api/v1/groups/${groupId}/meetings/${meetingId}/cancel`, {}),
  attendance: (groupId: string, meetingId: string, userId: string, attendance: string) =>
    post<Participant>(`/api/v1/groups/${groupId}/meetings/${meetingId}/attendance/${userId}`, {
      attendance,
    }),
  addNote: (groupId: string, meetingId: string, body: string) =>
    post<MeetingNote>(`/api/v1/groups/${groupId}/meetings/${meetingId}/notes`, { body }),
  updateNote: (groupId: string, meetingId: string, noteId: string, body: string) =>
    patch<MeetingNote>(`/api/v1/groups/${groupId}/meetings/${meetingId}/notes/${noteId}`, { body }),
  deleteNote: (groupId: string, meetingId: string, noteId: string) =>
    del<void>(`/api/v1/groups/${groupId}/meetings/${meetingId}/notes/${noteId}`),
}

/* ---------- votes ---------- */

export interface VoteInput {
  question: string
  description?: string
  ballotPolicy: string
  opensAt: string
  closesAt: string
  quorum?: number
  options: string[]
}

export const votes = {
  create: (groupId: string, input: VoteInput) =>
    post<Vote>(`/api/v1/groups/${groupId}/votes`, input),
  list: (groupId: string, page = 0) =>
    get<Page<Vote>>(`/api/v1/groups/${groupId}/votes?${pageParams(page)}`),
  detail: (groupId: string, voteId: string) =>
    get<Vote>(`/api/v1/groups/${groupId}/votes/${voteId}`),
  update: (groupId: string, voteId: string, input: Partial<VoteInput>) =>
    patch<Vote>(`/api/v1/groups/${groupId}/votes/${voteId}`, input),
  remove: (groupId: string, voteId: string) =>
    del<void>(`/api/v1/groups/${groupId}/votes/${voteId}`),
  open: (groupId: string, voteId: string) =>
    post<Vote>(`/api/v1/groups/${groupId}/votes/${voteId}/open`, {}),
  close: (groupId: string, voteId: string) =>
    post<Vote>(`/api/v1/groups/${groupId}/votes/${voteId}/close`, {}),
  publish: (groupId: string, voteId: string) =>
    post<Vote>(`/api/v1/groups/${groupId}/votes/${voteId}/publish`, {}),
  castBallot: (groupId: string, voteId: string, optionId: string) =>
    post<{ voted: boolean; optionId?: string }>(
      `/api/v1/groups/${groupId}/votes/${voteId}/ballots`,
      { optionId },
    ),
  results: (groupId: string, voteId: string) =>
    get<VoteResults>(`/api/v1/groups/${groupId}/votes/${voteId}/results`),
}

/* ---------- statements ---------- */

export const statements = {
  policies: (groupId: string) =>
    get<StatementPolicy[]>(`/api/v1/groups/${groupId}/statements/policies`),
  setPolicy: (groupId: string, requestType: string, approvalRequired: boolean) =>
    put<StatementPolicy>(`/api/v1/groups/${groupId}/statements/policies`, {
      requestType,
      approvalRequired,
    }),
  request: (groupId: string, input: { requestType: string; periodStart: string; periodEnd: string }) =>
    post<StatementRequest>(`/api/v1/groups/${groupId}/statements/requests`, input),
  list: (groupId: string, page = 0) =>
    get<Page<StatementRequest>>(`/api/v1/groups/${groupId}/statements/requests?${pageParams(page)}`),
  approve: (groupId: string, requestId: string) =>
    post<StatementRequest>(`/api/v1/groups/${groupId}/statements/requests/${requestId}/approve`, {}),
  reject: (groupId: string, requestId: string, reason?: string) =>
    post<StatementRequest>(`/api/v1/groups/${groupId}/statements/requests/${requestId}/reject`, {
      reason,
    }),
  document: (groupId: string, requestId: string) =>
    get<StatementDocument>(`/api/v1/groups/${groupId}/statements/requests/${requestId}/document`),
}

/* ---------- AI ---------- */

export const ai = {
  status: () => get<AiStatus>('/api/v1/ai/status'),
  createConversation: (groupId?: string, title?: string) =>
    post<AiConversation>('/api/v1/ai/conversations', { groupId, title }),
  conversations: (page = 0) =>
    get<Page<AiConversation>>(`/api/v1/ai/conversations?${pageParams(page)}`),
  conversation: (conversationId: string) =>
    get<ConversationDetail>(`/api/v1/ai/conversations/${conversationId}`),
  deleteConversation: (conversationId: string) =>
    del<void>(`/api/v1/ai/conversations/${conversationId}`),
  sendMessage: (conversationId: string, text: string) =>
    post<ChatResponse>(`/api/v1/ai/conversations/${conversationId}/messages`, { text }),
  proposals: (status?: string) =>
    get<AiProposal[]>(`/api/v1/ai/proposals${status ? `?status=${status}` : ''}`),
  confirmProposal: (proposalId: string) =>
    post<AiProposal>(`/api/v1/ai/proposals/${proposalId}/confirm`, {}),
  rejectProposal: (proposalId: string) =>
    post<AiProposal>(`/api/v1/ai/proposals/${proposalId}/reject`, {}),
}

/* ---------- identity / credit ---------- */

export const identity = {
  status: () => get<VerificationStatus>('/api/v1/verification/me'),
  start: () => post<VerificationStatus>('/api/v1/verification/me/start', {}),
  refresh: () => post<VerificationStatus>('/api/v1/verification/me/refresh', {}),
  creditEstimate: (groupId: string, memberUserId?: string) =>
    get<CreditEstimate>(
      `/api/v1/groups/${groupId}/credit/estimate${memberUserId ? `?memberUserId=${memberUserId}` : ''}`,
    ),
}

/* ---------- notifications & audit ---------- */

export const notifications = {
  mine: (page = 0) => get<Page<NotificationRecord>>(`/api/v1/notifications/me?${pageParams(page)}`),
  group: (groupId: string, page = 0) =>
    get<Page<NotificationRecord>>(`/api/v1/groups/${groupId}/notifications?${pageParams(page)}`),
}

export const audit = {
  events: (groupId: string, page = 0) =>
    get<Page<AuditEvent>>(`/api/v1/groups/${groupId}/audit?${pageParams(page)}`),
}

export type { GroupTotals }
