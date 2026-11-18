import type { TestStatus, DeviceStatus } from "../types/report";

type Status = TestStatus | DeviceStatus;

const COLOURS: Record<Status, string> = {
  PASS:               "bg-green-900 text-green-300",
  FAIL:               "bg-red-900 text-red-300",
  IGNORED:            "bg-gray-700 text-gray-400",
  ASSUMPTION_FAILURE: "bg-yellow-900 text-yellow-300",
  COMPLETED:          "bg-green-900 text-green-300",
  DISCONNECTED:       "bg-red-900 text-red-300",
};

export function StatusBadge({ status }: { status: Status }) {
  return (
    <span className={`px-2 py-0.5 rounded text-xs font-semibold ${COLOURS[status]}`}>
      {status}
    </span>
  );
}
