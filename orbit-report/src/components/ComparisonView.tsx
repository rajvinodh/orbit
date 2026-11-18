import type { ReportData, TestResult } from "../types/report";
import { StatusBadge } from "./StatusBadge";

interface TestKey {
  className: string;
  methodName: string;
}

function key(t: TestKey) {
  return `${t.className}#${t.methodName}`;
}

interface Props {
  data: ReportData;
}

export function ComparisonView({ data }: Props) {
  // Collect tests that appear on more than one device
  const testToDevices = new Map<string, { key: TestKey; results: Map<string, TestResult> }>();

  data.devices.forEach((device) => {
    device.tests.forEach((test) => {
      const k = key(test);
      if (!testToDevices.has(k)) {
        testToDevices.set(k, { key: test, results: new Map() });
      }
      testToDevices.get(k)!.results.set(device.serial, test);
    });
  });

  const multiDevice = [...testToDevices.values()].filter((e) => e.results.size > 1);

  if (multiDevice.length === 0) {
    return (
      <p className="text-gray-600 text-sm">
        No tests ran on multiple devices — nothing to compare.
      </p>
    );
  }

  const serials = data.devices.map((d) => d.serial);

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs">
        <thead>
          <tr className="border-b border-gray-800 text-gray-500">
            <th className="py-2 px-3 text-left">Test</th>
            {serials.map((s) => (
              <th key={s} className="py-2 px-3 text-center">{s}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {multiDevice.map(({ key: k, results }, i) => (
            <tr key={i} className="border-b border-gray-800 hover:bg-gray-900">
              <td className="py-2 px-3">
                <span className="text-gray-500">{k.className}.</span>
                {k.methodName}
              </td>
              {serials.map((serial) => {
                const r = results.get(serial);
                return (
                  <td key={serial} className="py-2 px-3 text-center">
                    {r ? <StatusBadge status={r.status} /> : (
                      <span className="text-gray-700">—</span>
                    )}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
