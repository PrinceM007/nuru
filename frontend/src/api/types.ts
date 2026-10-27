/**
 * TypeScript mirrors of the backend v1 DTOs (docs/API_CONTRACT.md).
 * Field names match the JSON exactly; nothing here is invented.
 */

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export interface ApiErrorBody {
  timestamp?: string
  status: number
  error?: string
  message?: string
  path?: string
  correlationId?: string
  details?: Record<string, unknown>
}

/* ---------- auth & users ---------- */

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export interface Profile {
  id: string
  email: string
  displayName: string
  phone: string | null
  createdAt: string
}

export interface Preferences {
  emailEnabled: boolean
  smsEnabled: boolean
  locale: string
}

export type ConsentPurpose = 'AI_ASSISTANCE' | 'IDENTITY_VERIFICATION'

export interface Consent {
  purpose: string
  granted: boolean
  recordedAt: string
}

/* ---------- groups ---------- */

export interface Group {
  id: string
  name: string
  description: string | null
  currency: string
  createdAt: string
  currentUserRole: string
}

export interface Member {
  userId: string
  displayName: string
  email: string | null
  role: string
  status: string
  joinedAt: string
}

export interface GroupRole {
  id: string
  name: string
  system: boolean
  permissions: string[]
}

export interface JoinRequest {
  id: string
  groupId: string
  userId: string
  displayName: string
  status: string
  message: string | null
  reviewedBy: string | null
  reviewedAt: string | null
  rejectionReason: string | null
  createdAt: string
}

export interface InvitationCreated {
  id: string
  groupId: string
  role: string
  code: string
  expiresAt: string
  maxUses: number
}

export interface Invitation {
  id: string
  groupId: string
  role: string
  expiresAt: string
  revokedAt: string | null
  maxUses: number
  useCount: number
  createdAt: string
}

export const PERMISSIONS = [
  'GROUP_SETTINGS_MANAGE',
  'MEMBERS_MANAGE',
  'ROLES_MANAGE',
  'MEETINGS_MANAGE',
  'VOTES_MANAGE',
  'APPROVAL_POLICIES_MANAGE',
  'INVITATIONS_MANAGE',
  'CONTRIBUTIONS_CONFIRM',
  'LEDGER_VIEW_FULL',
  'LOANS_APPROVE',
  'STATEMENTS_APPROVE',
  'AUDIT_VIEW',
] as const

/** Plain-language explanation of each permission for member-facing UI. */
export const PERMISSION_LABELS: Record<string, string> = {
  GROUP_SETTINGS_MANAGE: 'Change group name and description',
  MEMBERS_MANAGE: 'Approve or remove members',
  ROLES_MANAGE: 'Create roles and change member roles',
  MEETINGS_MANAGE: 'Create and manage meetings',
  VOTES_MANAGE: 'Create and manage votes',
  APPROVAL_POLICIES_MANAGE: 'Choose which statement requests need approval',
  INVITATIONS_MANAGE: 'Create and revoke invitations',
  CONTRIBUTIONS_CONFIRM: 'Confirm or correct contribution records',
  LEDGER_VIEW_FULL: "See every member's financial records",
  LOANS_APPROVE: 'Approve, disburse, and manage loans',
  STATEMENTS_APPROVE: 'Review statement requests',
  AUDIT_VIEW: 'View the audit trail and notification history',
}

/* ---------- finance ---------- */

export interface Schedule {
  id: string
  title: string
  amount: number
  currency: string
  frequency: string
  dueDay: number | null
  startDate: string
  endDate: string | null
  createdAt: string
}

export interface Contribution {
  id: string
  groupId: string
  scheduleId: string | null
  memberUserId: string
  periodStart: string
  periodEnd: string
  amount: number
  currency: string
  status: string
  recordedBy: string
  recordedVia: string
  paymentReference: string | null
  confirmedBy: string | null
  confirmedAt: string | null
  rejectionReason: string | null
  correctionOf: string | null
  notes: string | null
  createdAt: string
  adjustments: Contribution[]
}

export interface LedgerSummary {
  groupId: string
  currency: string
  expectedTotal: number
  confirmedContributionsTotal: number
  loansDisbursedTotal: number
  loansRepaidTotal: number
  loansOutstandingTotal: number
  netBalance: number
  myExpectedTotal: number
  myConfirmedTotal: number
  asOf: string
}

export interface LedgerEntry {
  id: number
  kind: string
  referenceId: string
  memberUserId: string
  amount: number
  currency: string
  occurredAt: string
  createdAt: string
}

export interface Loan {
  id: string
  groupId: string
  borrowerUserId: string
  principal: number
  currency: string
  interestRate: number
  totalDue: number
  outstandingBalance: number
  termMonths: number
  purpose: string | null
  status: string
  requestedAt: string
  decidedBy: string | null
  decidedAt: string | null
  decisionReason: string | null
  disbursedAt: string | null
}

export interface Installment {
  installmentNo: number
  dueDate: string
  amountDue: number
  amountPaid: number
  status: string
}

export interface Repayment {
  id: string
  loanId: string
  amount: number
  currency: string
  payerUserId: string
  status: string
  recordedVia: string
  paymentReference: string | null
  rejectionReason: string | null
  createdAt: string
  confirmedAt: string | null
}

/* ---------- meetings ---------- */

export interface Participant {
  userId: string
  displayName: string
  attendance: string
}

export interface MeetingNote {
  id: string
  authorUserId: string
  authorDisplayName: string
  body: string
  createdAt: string
  updatedAt: string
}

export interface Meeting {
  id: string
  groupId: string
  title: string
  agenda: string | null
  startsAt: string
  endsAt: string | null
  timezone: string
  location: string | null
  meetingLink: string | null
  status: string
  participants: Participant[]
  notes: MeetingNote[]
  createdAt: string
}

/* ---------- votes ---------- */

export interface VoteOption {
  id: string
  label: string
  position: number
}

export interface Vote {
  id: string
  groupId: string
  question: string
  description: string | null
  ballotPolicy: string
  status: string
  opensAt: string
  closesAt: string
  quorum: number | null
  options: VoteOption[]
  eligibleCount: number
  eligibilityPolicy: string
  voted: boolean
  publishedAt: string | null
}

export interface MyBallot {
  voted: boolean
  optionId: string | null
}

export interface OptionTally {
  optionId: string
  label: string
  votes: number
}

export interface VoterChoice {
  userId: string
  displayName: string
}

export interface VoteResults {
  voteId: string
  ballotPolicy: string
  tallies: OptionTally[]
  totalBallots: number
  eligibleCount: number
  quorum: number | null
  quorumMet: boolean
  publishedAt: string | null
  attributableChoices: Record<string, VoterChoice[]> | null
}

/* ---------- statements ---------- */

export interface StatementPolicy {
  requestType: string
  approvalRequired: boolean
}

export interface StatementRequest {
  id: string
  groupId: string
  requesterUserId: string
  requestType: string
  periodStart: string
  periodEnd: string
  status: string
  reviewerUserId: string | null
  decidedAt: string | null
  rejectionReason: string | null
  createdAt: string
}

export interface ContributionLine {
  periodStart: string
  periodEnd: string
  amount: number
  status: string
  recordedVia: string
}

export interface LoanLine {
  loanId: string
  principal: number
  totalDue: number
  outstanding: number
  status: string
}

export interface GroupTotals {
  expectedTotal: number
  confirmedContributionsTotal: number
  loansDisbursedTotal: number
  loansRepaidTotal: number
  loansOutstandingTotal: number
  netBalance: number
  activeMembers: number
}

export interface StatementDocument {
  requestId: string
  requestType: string
  currency: string
  periodStart: string
  periodEnd: string
  generatedAt: string
  title: string
  contributions: ContributionLine[]
  loans: LoanLine[]
  expectedTotal: number
  confirmedTotal: number
  borrowedTotal: number
  loansOutstandingTotal: number
  groupTotals: GroupTotals | null
}

/* ---------- AI ---------- */

export interface AiStatus {
  provider: string
  available: boolean
  detail: string
}

export interface AiConversation {
  id: string
  groupId: string | null
  title: string | null
  createdAt: string
  updatedAt: string
}

export interface AiMessage {
  id: number
  role: string
  content: string
  createdAt: string
}

export interface ExecutedTool {
  tool: string
  summary: string
  facts: Record<string, unknown>
}

export interface AiProposal {
  id: string
  conversationId: string
  groupId: string | null
  tool: string
  description: string
  args: Record<string, unknown>
  status: string
  resultSummary: string | null
  createdAt: string
  expiresAt: string
  decidedAt: string | null
}

export interface ChatResponse {
  assistantMessage: string
  executedTools: ExecutedTool[]
  proposals: AiProposal[]
}

export interface ConversationDetail {
  id: string
  groupId: string | null
  title: string | null
  createdAt: string
  updatedAt: string
  messages: AiMessage[]
  proposals: AiProposal[]
}

/* ---------- identity / credit / notifications / audit ---------- */

export interface VerificationStatus {
  status: string
  provider: string
  providerReference: string | null
  verifiedAt: string | null
  expiresAt: string | null
  failureReason: string | null
  updatedAt: string | null
}

export interface CreditFactor {
  name: string
  value: string
  weight: number
}

export interface CreditEstimate {
  provider: string
  memberUserId: string
  insufficientHistory: boolean
  score: number | null
  factors: CreditFactor[]
  dataPeriodStart: string
  dataPeriodEnd: string
  estimatedAt: string
  label: string
}

export interface NotificationRecord {
  id: string
  groupId: string
  channel: string
  kind: string
  subject: string
  status: string
  provider: string
  providerReference: string | null
  errorDetail: string | null
  createdAt: string
}

export interface AuditEvent {
  id: number
  occurredAt: string
  actorUserId: string
  groupId: string
  action: string
  targetType: string
  targetId: string
  outcome: string
  details: string
  correlationId: string
}
