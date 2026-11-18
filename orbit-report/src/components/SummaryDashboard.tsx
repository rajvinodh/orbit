import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from "recharts";
import type { ReportData, TestResult, TestStatus } from "../types/report";
import { StatusBadge } from "./StatusBadge";

function fmtMs(ms: number) {
  const s = Math.round(ms / 1000);
  return s >= 60 ? `${Math.floor(s / 60)}m ${s % 60}s` : `${s}s`;
}

function fmtTime(epoch: number) {
  return new Date(epoch).toLocaleString();
}

// Gantt timeline — renders one bar per test per device, coloured by status
function GanttChart({ data }: { data: ReportData }) {
  const startMs = data.meta.startedAt;
  const totalMs = data.meta.durationMs || 1;

  const STATUS_COLOR: Record<string, string> = {
    PASS: "#22c55e",
    FAIL: "#ef4444",
    IGNORED: "#6b7280",
    ASSUMPTION_FAILURE: "#f59e0b",
  };

  return (
    <div className="overflow-x-auto">
      {data.devices.map((device) => (
        <div key={device.serial} className="mb-3">
          <div className="text-xs text-gray-500 mb-1">
            {device.model} <span className="text-gray-700">({device.serial})</span>
          </div>
          <div className="relative h-5 bg-gray-800 rounded">
            {device.tests.map((t, i) => {
              const left = ((t.startedAt - startMs) / totalMs) * 100;
              const width = Math.max((t.durationMs / totalMs) * 100, 0.3);
              return (
                <div
                  key={i}
                  title={`${t.className}.${t.methodName} (${fmtMs(t.durationMs)})`}
                  className="absolute h-full rounded-sm"
                  style={{
                    left: `${left}%`,
                    width: `${width}%`,
                    backgroundColor: STATUS_COLOR[t.status],
                    opacity: 0.85,
                  }}
                />
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

interface Props {
  data: ReportData;
  onFailedClick: (deviceSerial: string) => void;
}

export function SummaryDashboard({ data, onFailedClick }: Props) {
  const { summary, meta } = data;
  const passRate = summary.total > 0 ? Math.round((summary.passed / summary.total) * 100) : 0;

  const allTests = data.devices.flatMap((d) => d.tests);
  // In FANOUT mode use the aggregated tests array to avoid double-counting across devices
  const aggregated = data.meta.executionMode === "FANOUT" && data.tests ? data.tests : null;
  const ignoredCount = aggregated
    ? aggregated.filter((t) => t.aggregatedStatus === "IGNORED").length
    : allTests.filter((t) => t.status === "IGNORED").length;
  const assumptionCount = aggregated
    ? aggregated.filter((t) => t.aggregatedStatus === "ASSUMPTION_FAILURE").length
    : allTests.filter((t) => t.status === "ASSUMPTION_FAILURE").length;

  const pieData = [
    { name: "Passed",             value: summary.passed,  color: "#22c55e" },
    { name: "Failed",             value: summary.failed,  color: "#ef4444" },
    { name: "Ignored",            value: ignoredCount,    color: "#6b7280" },
    { name: "Assumption failure", value: assumptionCount, color: "#f59e0b" },
  ].filter((d) => d.value > 0);

  // Collect all failed tests across all devices for the failed list
  const failedTests: { device: string; test: TestResult }[] = [];
  data.devices.forEach((d) =>
    d.tests.filter((t) => t.status === "FAIL").forEach((t) =>
      failedTests.push({ device: d.serial, test: t })
    )
  );

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-100">{meta.title}</h1>
        <p className="text-gray-500 text-xs mt-1">
          {fmtTime(meta.startedAt)} · {fmtMs(meta.durationMs)} total · orbit {meta.orbitVersion}
        </p>
      </div>

      {/* Stats + donut */}
      <div className="flex flex-wrap gap-6 items-center">
        <div className="flex gap-4">
          {[
            { label: "Total",      value: summary.total,   cls: "text-gray-200" },
            { label: "Passed",     value: summary.passed,  cls: "text-green-400" },
            { label: "Failed",     value: summary.failed,  cls: "text-red-400" },
            { label: "Ignored",    value: ignoredCount,    cls: "text-gray-400" },
            { label: "Assumption", value: assumptionCount, cls: "text-yellow-400" },
          ].map(({ label, value, cls }) => (
            <div key={label} className="text-center">
              <div className={`text-3xl font-bold ${cls}`}>{value}</div>
              <div className="text-gray-500 text-xs mt-1">{label}</div>
            </div>
          ))}
        </div>

        <div className="w-40 h-40">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={pieData}
                cx="50%"
                cy="50%"
                innerRadius={40}
                outerRadius={60}
                dataKey="value"
                startAngle={90}
                endAngle={-270}
              >
                {pieData.map((entry, i) => (
                  <Cell key={i} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{ background: "#1f2937", border: "none", fontSize: 12 }}
                formatter={(v, name) => [`${v}`, String(name)]}
              />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="text-center">
          <div className="text-4xl font-bold text-gray-100">{passRate}%</div>
          <div className="text-gray-500 text-xs mt-1">pass rate</div>
        </div>
      </div>

      {/* Gantt */}
      <div>
        <h2 className="text-sm font-semibold text-gray-400 mb-3">Timeline</h2>
        <GanttChart data={data} />
      </div>

      {/* FANOUT: aggregated test table */}
      {data.meta.executionMode === "FANOUT" && data.tests && data.tests.length > 0 && (
        <div>
          <h2 className="text-sm font-semibold text-gray-400 mb-3">
            Aggregated results ({data.tests.length})
          </h2>
          <div className="space-y-1">
            {data.tests.map((t, i) => (
              <div key={i} className="flex items-start gap-2 text-xs py-1 border-b border-gray-800">
                <div className="mt-0.5 shrink-0">
                  <StatusBadge status={t.aggregatedStatus} />
                </div>
                <div>
                  <span className="text-gray-500">{t.className}.</span>
                  <span className="text-gray-200">{t.methodName}</span>
                  <div className="flex flex-wrap gap-3 mt-0.5">
                    {Object.entries(t.deviceStatuses).map(([serial, status]) => (
                      <span key={serial} className="text-gray-600">
                        {serial}:{" "}
                        <span className={statusTextClass(status)}>{status}</span>
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Failed tests list */}
      {failedTests.length > 0 && (
        <div>
          <h2 className="text-sm font-semibold text-gray-400 mb-3">
            Failed tests ({failedTests.length})
          </h2>
          <ul className="space-y-1">
            {failedTests.map(({ device, test }, i) => (
              <li key={i}>
                <button
                  onClick={() => onFailedClick(device)}
                  className="text-left text-xs hover:text-red-300 transition-colors"
                >
                  <span className="text-red-500 mr-2">✗</span>
                  <span className="text-gray-400">{test.className}.</span>
                  <span className="text-gray-200">{test.methodName}</span>
                  <span className="text-gray-600 ml-2">@{device}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function statusTextClass(status: TestStatus): string {
  switch (status) {
    case "PASS": return "text-green-400";
    case "FAIL": return "text-red-400";
    case "ASSUMPTION_FAILURE": return "text-yellow-400";
    default: return "text-gray-500";
  }
}
