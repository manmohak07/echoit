'use client';

import { useState } from 'react';
import { FiDownload } from 'react-icons/fi';

interface FileDownloadProps {
  onDownload: (port: number, pin: number) => Promise<void>;
  isDownloading: boolean;
  serverError: string | null;
}

export default function FileDownload({ onDownload, isDownloading, serverError }: FileDownloadProps) {
  const [inviteCode, setInviteCode] = useState('');
  const [error, setError] = useState('');
  const [pin, setPin] = useState('');
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    
    const port = parseInt(inviteCode.trim(), 10);
    if (isNaN(port) || port <= 0 || port > 65535) {
      setError('Please enter a valid port number (1-65535)');
      return;
    }
    
    const pinNum = parseInt(pin.trim(), 10);
    if (isNaN(pinNum) || pinNum < 1000 || pinNum > 9999) {
      setError('Please enter a valid 4-digit PIN (1000–9999)');
      return;
    }
    
    try {
      await onDownload(port, pinNum);
    } catch (err) {
      setError('Failed to download the file. Please check the invite code and try again.');
    }
  };
  
  return (
    <div className="space-y-4">
      <div className="bg-blue-50 p-4 rounded-lg border border-blue-100">
        <h3 className="text-lg font-medium text-blue-800 mb-2">Receive a File</h3>
        <p className="text-sm text-blue-600 mb-0">
          Enter the invite code shared with you to download the file.
        </p>
      </div>

      {serverError && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-md">
          <p className="text-sm text-red-700">{serverError}</p>
        </div>
      )}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="inviteCode" className="block text-sm font-medium text-gray-700 mb-1">
            Invite Code
          </label>
          <input
            type="text"
            id="inviteCode"
            value={inviteCode}
            onChange={(e) => setInviteCode(e.target.value)}
            placeholder="Enter the invite code (port number)"
            className="input-field"
            disabled={isDownloading}
            required
          />
          {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
        </div>

        <div>
          <label htmlFor="pin" className="block text-sm font-medium text-gray-700 mb-1">
            Pin
          </label>
          <input
            type="text"
            id="pin"
            value={pin}
            onChange={(e) => setPin(e.target.value)}
            placeholder="Enter the pin"
            className="input-field"
            disabled={isDownloading}
            required
            maxLength={4}
          />
        </div>
        
        <button
          type="submit"
          className="btn-primary flex items-center justify-center w-full"
          disabled={isDownloading}
        >
          {isDownloading ? (
            <span>Downloading...</span>
          ) : (
            <>
              <FiDownload className="mr-2" />
              <span>Download File</span>
            </>
          )}
        </button>
      </form>
    </div>
  );
}
