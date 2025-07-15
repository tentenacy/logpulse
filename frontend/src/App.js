import { useState, useEffect } from 'react';
import { Toaster, toast } from 'react-hot-toast';
import LogPulseDashboard from './LogPulseDashboard';
import logpulseApi from './services/logpulseApi';

export default function App() {
  const [isConnected, setIsConnected] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // 백엔드 연결 상태 확인
    checkConnection();
  }, []);

  const checkConnection = async () => {
    setIsLoading(true);
    try {
      // 시스템 상태 요청으로 연결 확인
      await logpulseApi.getSystemStatus();
      setIsConnected(true);
      toast.success('LogPulse 서버에 연결되었습니다.');
    } catch (error) {
      console.error('서버 연결 실패:', error);
      setIsConnected(false);
      toast.error('LogPulse 서버에 연결할 수 없습니다. 개발 모드로 실행합니다.');
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-indigo-600 mx-auto"></div>
          <p className="mt-4 text-indigo-600 font-medium">LogPulse 대시보드 로딩 중...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 토스트 알림 컴포넌트 */}
      <Toaster position="top-right" />

      {/* 연결 상태 표시 (개발 모드일 때만) */}
      {!isConnected && (
        <div className="bg-yellow-100 border-l-4 border-yellow-500 p-4">
          <div className="flex">
            <div className="flex-shrink-0">
              <svg className="h-5 w-5 text-yellow-500" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
              </svg>
            </div>
            <div className="ml-3">
              <p className="text-sm text-yellow-700">
                LogPulse 서버에 연결되지 않았습니다. 모의 데이터로 실행 중입니다.
                <button
                  onClick={checkConnection}
                  className="ml-2 font-medium text-yellow-700 underline hover:text-yellow-600"
                >
                  다시 연결
                </button>
              </p>
            </div>
          </div>
        </div>
      )}

      {/* 대시보드 메인 컴포넌트 */}
      <LogPulseDashboard isConnected={isConnected} />
    </div>
  );
}