// Types mirror the backend's AnalysisResult (backend/src/main/java/.../analysis/AnalysisResult.java).

export type Decision = 'RECOMMEND' | 'RECOMMEND_WITH_CONDITIONS' | 'NEED_MORE_INFO' | 'DECLINE';
export type Severity = 'EXCLUDED' | 'WARNING' | 'INFO';
export type Rating = 'GOOD' | 'FAIR' | 'POOR';
export type CustomerFlag =
  | 'LARGEST_CUSTOMER'
  | 'PAYING_SLOWER'
  | 'CHRONIC_LATE_PAYER'
  | 'STOPPED_BUYING'
  | 'NEW_CUSTOMER'
  | 'HIGH_OVERDUE';

export interface ApplicationInfo {
  id: number;
  companyName: string;
  sourceFileName: string;
  uploadedAt: string;
  periodStart: string;
  periodEnd: string;
  asOfDate: string;
  requestedAmount: number | null;
  status: 'ANALYZED' | 'DECIDED';
  decision: Decision | null;
  decisionNote: string | null;
  decidedAt: string | null;
}

export interface Headline {
  netRevenue: number;
  averageMonthlyRevenue: number;
  revenueTrendPct: number;
  activeCustomers: number;
  invoiceCount: number;
  openReceivables: number;
  overdueReceivables: number;
  daysSalesOutstanding: number;
  averageDaysLate: number;
}

export interface MonthlyPoint {
  month: string;
  grossSales: number;
  creditNotes: number;
  netSales: number;
  invoiceCount: number;
  activeCustomers: number;
}

export interface CustomerShare {
  customerKey: string;
  name: string;
  revenue: number;
  share: number;
}

export interface Concentration {
  topCustomerShare: number;
  top5Share: number;
  hhi: number;
  topCustomers: CustomerShare[];
}

export interface AgingBucket {
  label: string;
  amount: number;
  invoiceCount: number;
  share: number;
}

export interface Collections {
  daysSalesOutstanding: number;
  averageDaysToPay: number;
  averageDaysLate: number;
  onTimeRate: number;
  paidInvoicesMeasured: number;
  aging: AgingBucket[];
}

export interface CustomerSummary {
  customerKey: string;
  name: string;
  segment: string | null;
  city: string | null;
  revenue: number;
  share: number;
  invoiceCount: number;
  firstInvoice: string;
  lastInvoice: string;
  averageDaysLate: number | null;
  baselineDaysLate: number | null;
  recentDaysLate: number | null;
  openBalance: number;
  overdue60Balance: number;
  flags: CustomerFlag[];
}

export interface ScoreFactor {
  name: string;
  points: number;
  maxPoints: number;
  value: string;
  explanation: string;
  rating: Rating;
}

export interface RiskAssessment {
  score: number;
  maxScore: number;
  band: 'Strong' | 'Moderate' | 'Weak';
  recommendation: string;
  factors: ScoreFactor[];
  strengths: string[];
  concerns: string[];
}

export interface FinancingStep {
  label: string;
  amount: number;
  explanation: string;
}

export interface FinancingEstimate {
  method: string;
  steps: FinancingStep[];
  eligibleReceivables: number;
  advanceRate: number;
  suggestedLimit: number;
  requestedAmount: number | null;
  requestCoverage: number | null;
  conditions: string[];
}

export interface IssueCount {
  type: string;
  label: string;
  severity: Severity;
  count: number;
}

export interface DataQuality {
  rowsRead: number;
  rowsAccepted: number;
  rowsExcluded: number;
  warnings: number;
  info: number;
  byType: IssueCount[];
}

export interface Analysis {
  application: ApplicationInfo;
  headline: Headline;
  monthly: MonthlyPoint[];
  concentration: Concentration;
  collections: Collections;
  customers: CustomerSummary[];
  risk: RiskAssessment;
  financing: FinancingEstimate;
  dataQuality: DataQuality;
}

export interface ApplicationSummary {
  id: number;
  companyName: string;
  sourceFileName: string;
  uploadedAt: string;
  periodStart: string;
  periodEnd: string;
  requestedAmount: number | null;
  rowsRead: number;
  rowsAccepted: number;
  rowsExcluded: number;
  status: 'ANALYZED' | 'DECIDED';
  decision: Decision | null;
}

export interface Issue {
  sourceRow: number;
  invoiceNumber: string | null;
  type: string;
  label: string;
  severity: Severity;
  message: string;
  actionTaken: string;
}

export interface InvoiceRow {
  sourceRow: number;
  invoiceNumber: string;
  type: 'INVOICE' | 'CREDIT_NOTE';
  invoiceDate: string;
  dueDate: string;
  customerName: string;
  subtotal: number;
  total: number;
  paymentStatus: 'PAID' | 'UNPAID' | 'PARTIALLY_PAID' | 'NOT_APPLICABLE';
  paidDate: string | null;
  outstanding: number;
}

/** Error with the backend's human-readable message. */
export class ApiError extends Error {}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`/api${path}`, init);
  } catch {
    throw new ApiError('Cannot reach the server. Is the backend running on port 8080?');
  }
  if (!res.ok) {
    let message = `Request failed (${res.status}).`;
    try {
      const body = await res.json();
      if (body?.message) message = body.message;
    } catch {
      /* keep default */
    }
    throw new ApiError(message);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export const api = {
  list: () => request<ApplicationSummary[]>('/applications'),
  get: (id: number) => request<Analysis>(`/applications/${id}`),
  issues: (id: number) => request<Issue[]>(`/applications/${id}/issues`),
  invoices: (id: number, customerKey: string) =>
    request<InvoiceRow[]>(`/applications/${id}/invoices?customerKey=${encodeURIComponent(customerKey)}`),
  upload: (file: File, companyName: string, requestedAmount: string) => {
    const form = new FormData();
    form.append('file', file);
    if (companyName.trim()) form.append('companyName', companyName.trim());
    if (requestedAmount.trim()) form.append('requestedAmount', requestedAmount.replace(/,/g, '').trim());
    return request<Analysis>('/applications', { method: 'POST', body: form });
  },
  loadSample: () => request<Analysis>('/applications/sample', { method: 'POST' }),
  decide: (id: number, decision: Decision, note: string) =>
    request<ApplicationSummary>(`/applications/${id}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision, note }),
    }),
  remove: (id: number) => request<void>(`/applications/${id}`, { method: 'DELETE' }),
};
