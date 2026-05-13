"use client";

import { useState, useEffect } from "react";
import FileUpload from "@/components/FileUpload";
import FileDownload from "@/components/FileDownload";
import InviteCode from "@/components/InviteCode";
import axios from "axios";

export default function Home() {
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [port, setPort] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<"upload" | "download">("upload");
  const [timeLeft, setTimeLeft] = useState(60);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [pin, setPin] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  
  // Start the countdown as soon as the backend confirms the upload (port is set)
  useEffect(() => {
    if (port === null) return;

    // Reset for a fresh session each time a new file is uploaded
    setTimeLeft(60);
    setSessionExpired(false);

    const interval = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          setSessionExpired(true);
          setPort(null);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    // Cleanup: if the component unmounts or port changes (new upload), kill old interval
    return () => clearInterval(interval);
  }, [port]);

  const handleFileUpload = async (file: File) => {
    setUploadedFile(file);
    setIsUploading(true);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await axios.post("/api/upload", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });

      setPort(response.data.port);
      setPin(response.data.pin);
    } catch (error) {
      console.error("Error uploading file:", error);
      alert("Failed to upload file. Please try again.");
    } finally {
      setIsUploading(false);
    }
  };

  const handleDownload = async (port: number, pin: number) => {
    setIsDownloading(true);
    setDownloadError(null);

    try {
      // Request download from Java backend
      const response = await axios.get(`/api/download/${port}?pin=${pin}`, {
        responseType: "blob",
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;

      // Try to get filename from response headers
      // Axios normalizes headers to lowercase, but we need to handle different cases
      const headers = response.headers;
      let contentDisposition = "";

      // Look for content-disposition header regardless of case
      for (const key in headers) {
        if (key.toLowerCase() === "content-disposition") {
          contentDisposition = headers[key];
          break;
        }
      }

      let filename = "downloaded-file";

      if (contentDisposition) {
        const filenameMatch = contentDisposition.match(/filename="(.+)"/);
        if (filenameMatch && filenameMatch.length === 2) {
          filename = filenameMatch[1];
        }
      }

      link.setAttribute("download", filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error: any) {
      console.error("Error downloading file:", error);
      if (error.response) {
        const text = await (error.response.data as Blob).text();
        try {
          const errData = JSON.parse(text);
          const retriesMsg = errData.retriesLeft !== undefined
            ? ` ${errData.retriesLeft} attempt(s) remaining.` : '';
          setDownloadError(errData.error + retriesMsg);
        } catch {
          setDownloadError(text || 'Download failed.');
        }
      } else {
        setDownloadError('Failed to download. Check the invite code and PIN.');
      }
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      <header className="text-center mb-12">
        <h1 className="text-4xl font-bold text-blue-600 mb-2">Echoit</h1>
        <p className="text-xl text-gray-600">
          Secure peer-to-peer File Sharing
        </p>
      </header>

      <div className="bg-white rounded-lg shadow-lg p-6">
        <div className="flex border-b mb-6">
          <button
            className={`px-4 py-2 font-medium ${
              activeTab === "upload"
                ? "text-blue-600 border-b-2 border-blue-600"
                : "text-gray-500 hover:text-gray-700"
            }`}
            onClick={() => setActiveTab("upload")}
          >
            Share a File
          </button>
          <button
            className={`px-4 py-2 font-medium ${
              activeTab === "download"
                ? "text-blue-600 border-b-2 border-blue-600"
                : "text-gray-500 hover:text-gray-700"
            }`}
            onClick={() => setActiveTab("download")}
          >
            Receive a File
          </button>
        </div>

        {activeTab === "upload" ? (
          <div>
            <FileUpload
              onFileUpload={handleFileUpload}
              isUploading={isUploading}
            />

            {uploadedFile && !isUploading && (
              <div className="mt-4 p-3 bg-gray-50 rounded-md">
                <p className="text-sm text-gray-600">
                  Selected file:{" "}
                  <span className="font-medium">{uploadedFile.name}</span> (
                  {Math.round(uploadedFile.size / 1024)} KB)
                </p>
              </div>
            )}

            {isUploading && (
              <div className="mt-6 text-center">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent"></div>
                <p className="mt-2 text-gray-600">Uploading file...</p>
              </div>
            )}

            <InviteCode
              port={port}
              timeLeft={timeLeft}
              sessionExpired={sessionExpired}
              pin={pin}
            />
          </div>
        ) : (
          <div>
            <FileDownload
              onDownload={handleDownload}
              isDownloading={isDownloading}
              serverError={downloadError}
            />

            {isDownloading && (
              <div className="mt-6 text-center">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent"></div>
                <p className="mt-2 text-gray-600">Downloading file...</p>
              </div>
            )}
          </div>
        )}
      </div>

      <footer className="mt-12 text-center text-gray-500 text-sm">
        <p>
          Echoit &copy; {new Date().getFullYear()} - Secure peer-to-peer File
          Sharing
        </p>
      </footer>
    </div>
  );
}
