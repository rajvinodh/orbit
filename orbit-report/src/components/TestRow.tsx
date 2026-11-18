import { useState } from "react";
import type { TestResult } from "../types/report";
import { StatusBadge } from "./StatusBadge";
import { ScreenshotCarousel } from "./ScreenshotCarousel";
import { LogcatViewer } from "./LogcatViewer";

function fmtMs(ms: number) {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
}

interface Props {
  test: TestResult;
}

export function TestRow({ test }: Props) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <tr
        className="border-b border-gray-800 cursor-pointer hover:bg-gray-900 transition-colors"
        onClick={() => setOpen((o) => !o)}
      >
        <td className="py-2 px-3">
          <StatusBadge status={test.status} />
        </td>
        <td className="py-2 px-3 text-gray-300">
          <span className="text-gray-500">{test.className}.</span>
          {test.methodName}
          {test.ignoredReason && (
            <div className="text-xs text-gray-600 mt-0.5">{test.ignoredReason}</div>
          )}
        </td>
        <td className="py-2 px-3 text-gray-400 text-right">{fmtMs(test.durationMs)}</td>
        <td className="py-2 px-3 text-gray-600 text-xs">
          {test.externalId ?? ""}
        </td>
        <td className="py-2 px-3 text-gray-600 text-center select-none">
          {open ? "▲" : "▼"}
        </td>
      </tr>

      {open && (
        <tr className="border-b border-gray-800 bg-gray-900">
          <td colSpan={5} className="px-4 py-3">
            {test.exception && (
              <div className="mb-2">
                <div className="text-xs text-gray-400 mb-1">Exception</div>
                <pre className="bg-gray-950 rounded p-2 text-xs text-red-300 overflow-x-auto max-h-48 overflow-y-auto whitespace-pre-wrap">
                  {test.exception}
                </pre>
              </div>
            )}

            <ScreenshotCarousel paths={test.screenshots} />

            {test.video && (
              <div className="mt-2">
                <div className="text-xs text-gray-400 mb-1">Video</div>
                <video
                  src={test.video}
                  controls
                  className="max-h-72 rounded border border-gray-700"
                />
              </div>
            )}

            {test.files?.length > 0 && (
              <div className="mt-2">
                <div className="text-xs text-gray-400 mb-1">Files</div>
                <ul className="flex flex-col gap-1">
                  {test.files.map((f) => (
                    <li key={f}>
                      <a
                        href={f}
                        download
                        className="text-xs text-blue-400 hover:text-blue-300 break-all"
                      >
                        {f.split("/").pop()}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {test.logcat.length > 0 && (
              <LogcatViewer lines={test.logcat} title="Logcat" />
            )}

            {test.crashLog.length > 0 && (
              <LogcatViewer lines={test.crashLog} title="Crash log" />
            )}
          </td>
        </tr>
      )}
    </>
  );
}
