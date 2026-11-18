declare global {
  interface Window {
    __ORBIT_DATA__?: ReportData;
  }
}

export type TestStatus = "PASS" | "FAIL" | "IGNORED" | "ASSUMPTION_FAILURE";
export type DeviceStatus = "COMPLETED" | "DISCONNECTED";
export type ExecutionMode = "DISTRIBUTE" | "FANOUT";
export type LogLevel = "V" | "D" | "I" | "W" | "E";

export interface LogLine {
  time: string;
  level: LogLevel;
  tag: string;
  pid: number;
  msg: string;
}

export interface TestResult {
  className: string;
  methodName: string;
  status: TestStatus;
  startedAt: number;
  durationMs: number;
  externalId?: string | null;
  exception?: string | null;
  ignoredReason?: string | null;
  screenshots: string[];
  video?: string | null;
  files: string[];
  logcat: LogLine[];
  crashLog: LogLine[];
}

export interface DeviceResult {
  serial: string;
  status: DeviceStatus;
  model: string;
  apiLevel: number;
  osVersion: string;
  durationMs: number;
  disconnectedAt?: number | null;
  tests: TestResult[];
}

export interface AggregatedTest {
  className: string;
  methodName: string;
  aggregatedStatus: TestStatus;
  deviceStatuses: Record<string, TestStatus>;
}

export interface ReportMeta {
  title: string;
  startedAt: number;
  durationMs: number;
  orbitVersion: string;
  executionMode: ExecutionMode;
}

export interface ReportSummary {
  total: number;
  passed: number;
  failed: number;
  ignored: number;
}

export interface ReportData {
  meta: ReportMeta;
  summary: ReportSummary;
  devices: DeviceResult[];
  tests?: AggregatedTest[] | null;
}
