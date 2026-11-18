import { useState, useEffect } from "react";
import type { ReportData } from "./types/report";
import { SummaryDashboard } from "./components/SummaryDashboard";
import { DeviceView } from "./components/DeviceView";
import { ComparisonView } from "./components/ComparisonView";

type Tab = "summary" | "devices" | "compare";

function LoadingState() {
  return (
    <div className="flex items-center justify-center h-screen text-gray-600">
      Loading report data…
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="flex items-center justify-center h-screen text-red-500">
      {message}
    </div>
  );
}

export default function App() {
  const [data, setData] = useState<ReportData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("summary");
  const [focusedDevice, setFocusedDevice] = useState<string | null>(null);

  useEffect(() => {
    // Prefer data injected inline by OrbitReportRenderer (works on file://).
    // Fall back to fetch for the Vite dev server.
    if (window.__ORBIT_DATA__) {
      setData(window.__ORBIT_DATA__);
      return;
    }
    fetch("data.json")
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then((json: ReportData) => setData(json))
      .catch((e: Error) => setError(`Failed to load data.json: ${e.message}`));
  }, []);

  if (error) return <ErrorState message={error} />;
  if (!data) return <LoadingState />;

  function handleFailedClick(serial: string) {
    setFocusedDevice(serial);
    setTab("devices");
  }

  const TABS: { id: Tab; label: string }[] = [
    { id: "summary", label: "Summary" },
    { id: "devices", label: `Devices (${data.devices.length})` },
    { id: "compare", label: "Compare" },
  ];

  return (
    <div className="min-h-screen flex flex-col">
      {/* Top nav */}
      <nav className="border-b border-gray-800 px-6 py-3 flex items-center gap-6 sticky top-0 bg-gray-950 z-10">
        <span className="text-gray-400 font-semibold text-xs tracking-widest uppercase">Orbit</span>
        <div className="flex gap-1">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => { setTab(t.id); if (t.id !== "devices") setFocusedDevice(null); }}
              className={`px-3 py-1.5 rounded text-xs transition-colors ${
                tab === t.id
                  ? "bg-gray-800 text-white"
                  : "text-gray-500 hover:text-gray-300"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </nav>

      {/* Content */}
      <main className="flex-1 px-6 py-6 max-w-6xl mx-auto w-full">
        {tab === "summary" && (
          <SummaryDashboard data={data} onFailedClick={handleFailedClick} />
        )}

        {tab === "devices" && (
          <div>
            {/* Device selector when there are multiple */}
            {data.devices.length > 1 && (
              <div className="flex gap-2 mb-6 flex-wrap">
                <button
                  onClick={() => setFocusedDevice(null)}
                  className={`px-3 py-1 rounded text-xs border transition-colors ${
                    focusedDevice === null
                      ? "bg-gray-700 border-gray-500 text-white"
                      : "border-gray-800 text-gray-500 hover:border-gray-600"
                  }`}
                >
                  All devices
                </button>
                {data.devices.map((d) => (
                  <button
                    key={d.serial}
                    onClick={() => setFocusedDevice(d.serial)}
                    className={`px-3 py-1 rounded text-xs border transition-colors ${
                      focusedDevice === d.serial
                        ? "bg-gray-700 border-gray-500 text-white"
                        : "border-gray-800 text-gray-500 hover:border-gray-600"
                    }`}
                  >
                    {d.model} <span className="text-gray-600">({d.serial})</span>
                  </button>
                ))}
              </div>
            )}

            {data.devices
              .filter((d) => focusedDevice === null || d.serial === focusedDevice)
              .map((d) => (
                <DeviceView key={d.serial} device={d} />
              ))}
          </div>
        )}

        {tab === "compare" && <ComparisonView data={data} />}
      </main>
    </div>
  );
}
