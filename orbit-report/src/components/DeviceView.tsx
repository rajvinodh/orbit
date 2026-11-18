import { useState } from "react";
import type { DeviceResult, TestStatus } from "../types/report";
import { StatusBadge } from "./StatusBadge";
import { TestRow } from "./TestRow";

function fmtMs(ms: number) {
  const s = Math.round(ms / 1000);
  return s >= 60 ? `${Math.floor(s / 60)}m ${s % 60}s` : `${s}s`;
}

type Filter = "ALL" | TestStatus;

interface Props {
  device: DeviceResult;
}

export function DeviceView({ device }: Props) {
  const [filter, setFilter] = useState<Filter>("ALL");

  const visible = device.tests.filter(
    (t) => filter === "ALL" || t.status === filter
  );

  const counts: Record<TestStatus, number> = { PASS: 0, FAIL: 0, IGNORED: 0, ASSUMPTION_FAILURE: 0 };
  device.tests.forEach((t) => counts[t.status]++);

  return (
    <section className="mb-10">
      {/* Device header */}
      <div className="flex flex-wrap items-center gap-3 mb-4 px-1">
        <StatusBadge status={device.status} />
        <span className="font-semibold text-gray-200">{device.model}</span>
        <span className="text-gray-500 text-xs">
          API {device.apiLevel} · Android {device.osVersion} · {device.serial}
        </span>
        <span className="ml-auto text-gray-400 text-xs">{fmtMs(device.durationMs)}</span>
      </div>

      {/* Filter bar */}
      <div className="flex gap-2 mb-3">
        {(["ALL", "FAIL", "PASS", "IGNORED", "ASSUMPTION_FAILURE"] as const).map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-3 py-1 rounded text-xs border transition-colors ${
              filter === f
                ? "bg-gray-700 border-gray-500 text-white"
                : "border-gray-800 text-gray-500 hover:border-gray-600"
            }`}
          >
            {f === "ALL" ? `All (${device.tests.length})` : `${f} (${counts[f as TestStatus]})`}
          </button>
        ))}
      </div>

      {/* Test table */}
      <div className="overflow-x-auto rounded border border-gray-800">
        <table className="w-full">
          <thead>
            <tr className="border-b border-gray-800 text-gray-500 text-xs">
              <th className="py-2 px-3 text-left w-24">Status</th>
              <th className="py-2 px-3 text-left">Test</th>
              <th className="py-2 px-3 text-right w-20">Duration</th>
              <th className="py-2 px-3 text-left w-28">External ID</th>
              <th className="py-2 px-3 w-8" />
            </tr>
          </thead>
          <tbody>
            {visible.length === 0 ? (
              <tr>
                <td colSpan={5} className="py-6 text-center text-gray-600">
                  no tests
                </td>
              </tr>
            ) : (
              visible.map((t, i) => (
                <TestRow key={`${t.className}${t.methodName}${i}`} test={t} />
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
