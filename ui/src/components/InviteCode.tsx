"use client";

import { useState } from "react";
import { FiCopy, FiCheck, FiClock, FiAlertCircle } from "react-icons/fi";

interface InviteCodeProps {
  port: number | null;
  timeLeft: number;
  sessionExpired: boolean;
}

export default function InviteCode({
  port,
  timeLeft,
  sessionExpired,
}: InviteCodeProps) {
  const [copied, setCopied] = useState(false);

  const copyToClipboard = () => {
    if (port === null) return;
    navigator.clipboard.writeText(port.toString());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Nothing to show until a file has been uploaded
  if (!port && !sessionExpired) return null;

  // Timer colour: green > 30s, amber 10–30s, red < 10s
  const timerColor =
    timeLeft > 30
      ? "text-green-600"
      : timeLeft > 10
        ? "text-amber-500"
        : "text-red-600";

  const timerBg =
    timeLeft > 30
      ? "bg-green-100"
      : timeLeft > 10
        ? "bg-amber-100"
        : "bg-red-100";

  // Session expired — file is gone from the server
  if (sessionExpired) {
    return (
      <div className="mt-6 p-4 bg-red-50 border border-red-200 rounded-lg">
        <div className="flex items-center gap-2 mb-1">
          <FiAlertCircle className="text-red-600 w-5 h-5" />
          <h3 className="text-lg font-medium text-red-800">Session Expired</h3>
        </div>
        <p className="text-sm text-red-600">
          The 60-second sharing window has passed. The file has been removed
          from the server. Upload it again to generate a new invite code.
        </p>
      </div>
    );
  }

  return (
    <div className="mt-6 p-4 bg-green-50 border border-green-200 rounded-lg">
      <div className="flex items-center justify-between mb-1">
        <h3 className="text-lg font-medium text-green-800">
          File Ready to Share!
        </h3>

        {/* Countdown badge */}
        <div
          className={`flex items-center gap-1 px-2 py-1 rounded-full ${timerBg}`}
        >
          <FiClock className={`w-4 h-4 ${timerColor}`} />
          <span className={`text-sm font-mono font-semibold ${timerColor}`}>
            {timeLeft}s
          </span>
        </div>
      </div>

      <p className="text-sm text-green-600 mb-3">
        Share this invite code with anyone you want to share the file with.{" "}
        <span className="font-medium">
          File auto-deletes when the timer hits zero.
        </span>
      </p>

      <div className="flex items-center">
        <div className="flex-1 bg-white p-3 rounded-l-md border border-r-0 border-gray-300 font-mono text-lg">
          {port}
        </div>
        <button
          onClick={copyToClipboard}
          className="p-3 bg-blue-500 hover:bg-blue-600 text-white rounded-r-md transition-colors"
          aria-label="Copy invite code"
        >
          {copied ? (
            <FiCheck className="w-5 h-5" />
          ) : (
            <FiCopy className="w-5 h-5" />
          )}
        </button>
      </div>

      <p className="mt-3 text-xs text-gray-500">
        This code is valid for{" "}
        <span className="font-semibold">{timeLeft} more seconds</span>. After
        that, the file is permanently removed from the server.
      </p>
    </div>
  );
}
