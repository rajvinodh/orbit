import { useState } from "react";
import type { LogLine, LogLevel } from "../types/report";

const LEVEL_COLOURS: Record<LogLevel, string> = {
  V: "text-gray-400",
  D: "text-blue-400",
  I: "text-green-400",
  W: "text-yellow-400",
  E: "text-red-400",
};

interface Props {
  lines: LogLine[];
  title?: string;
}

export function LogcatViewer({ lines, title = "Logcat" }: Props) {
  const [tagFilter, setTagFilter] = useState("");
  const [levelFilter, setLevelFilter] = useState<LogLevel | "">("");

  const filtered = lines.filter(
    (l) =>
      (!tagFilter || l.tag.toLowerCase().includes(tagFilter.toLowerCase())) &&
      (!levelFilter || l.level === levelFilter)
  );

  return (
    <div className="mt-2">
      <div className="flex items-center gap-2 mb-1">
        <span className="text-gray-400 text-xs">{title}</span>
        <input
          className="bg-gray-800 border border-gray-700 rounded px-2 py-0.5 text-xs w-32 focus:outline-none"
          placeholder="filter tag"
          value={tagFilter}
          onChange={(e) => setTagFilter(e.target.value)}
        />
        <select
          className="bg-gray-800 border border-gray-700 rounded px-1 py-0.5 text-xs focus:outline-none"
          value={levelFilter}
          onChange={(e) => setLevelFilter(e.target.value as LogLevel | "")}
        >
          <option value="">all</option>
          {(["V", "D", "I", "W", "E"] as LogLevel[]).map((l) => (
            <option key={l} value={l}>{l}</option>
          ))}
        </select>
      </div>
      <pre className="bg-gray-900 rounded p-2 text-xs overflow-x-auto max-h-60 overflow-y-auto">
        {filtered.length === 0 ? (
          <span className="text-gray-600">no lines</span>
        ) : (
          filtered.map((l, i) => (
            <div key={i} className={LEVEL_COLOURS[l.level]}>
              <span className="text-gray-600">{l.time} </span>
              <span className="font-bold">{l.level}/</span>
              <span className="text-gray-300">{l.tag}</span>
              <span className="text-gray-600">({l.pid}) </span>
              {l.msg}
            </div>
          ))
        )}
      </pre>
    </div>
  );
}
