import { useState } from "react";

interface Props {
  paths: string[];
}

export function ScreenshotCarousel({ paths }: Props) {
  const [idx, setIdx] = useState(0);

  if (paths.length === 0) return null;

  return (
    <div className="mt-2">
      <div className="flex items-center gap-2 mb-1">
        <span className="text-gray-400 text-xs">Screenshots</span>
        <span className="text-gray-600 text-xs">{idx + 1} / {paths.length}</span>
      </div>
      <div className="relative inline-block">
        <img
          src={paths[idx]}
          alt={`screenshot ${idx + 1}`}
          className="max-h-72 rounded border border-gray-700 object-contain"
        />
        {paths.length > 1 && (
          <div className="flex gap-2 mt-1 justify-center">
            <button
              className="px-2 py-0.5 bg-gray-800 rounded text-xs disabled:opacity-30"
              onClick={() => setIdx((i) => i - 1)}
              disabled={idx === 0}
            >
              ‹ prev
            </button>
            <button
              className="px-2 py-0.5 bg-gray-800 rounded text-xs disabled:opacity-30"
              onClick={() => setIdx((i) => i + 1)}
              disabled={idx === paths.length - 1}
            >
              next ›
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
