import { useState, useEffect, useCallback, useMemo  } from 'react';
import logpulseApi from '../services/logpulseApi';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  LineChart, Line, PieChart, Pie, Cell, AreaChart, Area
} from 'recharts';
import {
  AlertTriangle, Activity, Clock, Database,
  Search, Filter, DownloadCloud, RefreshCw,
  ChevronDown, X, Zap, Info, AlertOctagon,
  ChevronLeft, ChevronRight, Calendar, HelpCircle
} from 'lucide-react';

// 로그 레벨별 색상
const LOG_LEVEL_COLORS = {
  ERROR: '#FF5252',
  WARN: '#FFB74D',
  INFO: '#4FC3F7',
  DEBUG: '#9CCC65'
};

const LOG_LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG'];

/**
 * 타임스탬프 변환 유틸리티 함수
 */
const formatTimestamp = (timestamp) => {
  if (!timestamp) return '';
  
  const date = new Date(timestamp);
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};

/**
 * 메인 대시보드 컴포넌트
 */
export default function LogPulseDashboard() {
  // 전역 상태
  const [activeTab, setActiveTab] = useState('overview');
  const [isLoading, setIsLoading] = useState(false);
  const [timeRangeFilter, setTimeRangeFilter] = useState('24h'); // '24h', '7d', '30d'
  const [error, setError] = useState(null);

  // 대시보드 데이터 상태
  const [dashboardData, setDashboardData] = useState(null);
  const [systemStatus, setSystemStatus] = useState(null);
  const [recentErrors, setRecentErrors] = useState([]);

  // 로그 탐색기 상태
  const [logs, setLogs] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedLog, setSelectedLog] = useState(null);
  const [showLogDetails, setShowLogDetails] = useState(false);
  const [sourceFilter, setSourceFilter] = useState('all');
  const [levelFilter, setLevelFilter] = useState('all');
  const [availableSources, setAvailableSources] = useState(['all']);
  const [allSourcesList, setAllSourcesList] = useState(['all']);

  // 페이징 상태
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const getTimeRangeParams = useCallback(() => {
    const now = new Date();
    let startTime;

    switch (timeRangeFilter) {
      case '24h':
        startTime = new Date(now.getTime() - 24 * 60 * 60 * 1000);
        break;
      case '7d':
        startTime = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        break;
      case '30d':
        startTime = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        break;
      default:
        startTime = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    }

    return {
      start: startTime.toISOString(),
      end: now.toISOString()
    };
  }, [timeRangeFilter]);

  /**
   * 모든 소스 목록 로드
   */
  const loadAllSources = useCallback(async () => {
    try {
      const sourceStats = await logpulseApi.getSourceStats();
      if (sourceStats && sourceStats.sourceStats) {
        const sources = sourceStats.sourceStats.map(item => item.source);
        setAllSourcesList(['all', ...sources]);
      }
    } catch (err) {
      console.error('소스 목록 로드 오류:', err);
    }
  }, []);

  /**
   * 대시보드 데이터 로드
   */
  const loadDashboardData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const timeParams = getTimeRangeParams();

      console.log(timeParams)

      // 통합 대시보드 데이터 로드
      const stats = await logpulseApi.getDashboardStats(timeParams);
      setDashboardData(stats);

      // 시스템 상태 정보 로드
      if (stats.systemStatus) {
        setSystemStatus(stats.systemStatus);
      } else {
        const statusData = await logpulseApi.getSystemStatus();
        setSystemStatus(statusData);
      }

      // 최근 오류 로그 로드
      if (stats.recentErrors) {
        setRecentErrors(stats.recentErrors.recentErrors || []);
      } else {
        const errorData = await logpulseApi.getRecentErrors();
        setRecentErrors(errorData.recentErrors || []);
      }

      // 사용 가능한 소스 목록 업데이트
      if (stats.sourceStats && stats.sourceStats.sourceStats) {
        const sources = stats.sourceStats.sourceStats.map(item => item.source);
        setAvailableSources(['all', ...sources]);
        // 모든 소스 목록도 업데이트
        setAllSourcesList(['all', ...sources]);
      }
    } catch (err) {
      console.error('대시보드 데이터 로드 오류:', err);
      setError('대시보드 데이터를 불러오는 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  }, [getTimeRangeParams]);

  /**
   * 로그 데이터 로드 - 검색 버튼 클릭 시 호출됨
   */
  const loadLogData = async () => {
    setIsLoading(true);
    setError(null);

    try {
      const timeParams = getTimeRangeParams();

      // 검색 또는 기본 로그 조회 파라미터 설정
      const params = {
        page: currentPage,
        size: pageSize,
        sortBy: 'createdAt',
        sortDir: 'desc',
        start: timeParams.start,
        end: timeParams.end
      };

      // 필터 적용
      if (levelFilter !== 'all') {
        params.level = levelFilter;
      }

      if (sourceFilter !== 'all') {
        params.source = sourceFilter;
      }

      // 검색어가 있는 경우 검색 API 사용
      let response;
      if (searchQuery) {
        params.sortBy = 'timestamp'
        response = await logpulseApi.searchLogs({
          ...params,
          keyword: searchQuery
        });
      } else {
        // 일반 로그 조회
        response = await logpulseApi.getLogs(params);
      }

      // 응답 처리
      if (response && response.content) {
        setLogs(response.content);
        setTotalElements(response.totalElements);
        setTotalPages(response.totalPages);

        // 소스 목록 업데이트
        if (response.content.length > 0) {
          const sources = [...new Set(response.content.map(log => log.source))];
          setAvailableSources(['all', ...sources]);
        }
      } else {
        setLogs([]);
        setTotalElements(0);
        setTotalPages(0);
      }
    } catch (err) {
      console.error('로그 데이터 로드 오류:', err);
      setError('로그 데이터를 불러오는 중 오류가 발생했습니다.');
      setLogs([]);
      setTotalElements(0);
      setTotalPages(0);
    } finally {
      setIsLoading(false);
    }
  };

  /**
   * 로그 레벨별 배경색 클래스 반환
   */
  const getLogLevelClass = useCallback((level) => {
    switch (level) {
      case 'ERROR': return 'bg-red-100 text-red-800';
      case 'WARN': return 'bg-yellow-100 text-yellow-800';
      case 'INFO': return 'bg-blue-100 text-blue-800';
      case 'DEBUG': return 'bg-green-100 text-green-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  }, []);

  /**
   * CSV 파일로 로그 다운로드
   */
  const downloadLogs = useCallback(() => {
    if (!logs || logs.length === 0) {
      alert('다운로드할 로그가 없습니다.');
      return;
    }

    try {
      // CSV 헤더 및 콘텐츠 생성
      const headers = ['ID', 'Timestamp', 'Source', 'Level', 'Content'];
      const csvContent = [
        headers.join(','),
        ...logs.map(log => {
          // 콘텐츠에 쉼표가 있는 경우 큰따옴표로 감싸기
          const content = log.content ? `"${log.content.replace(/"/g, '""')}"` : '';
          return [
            log.id,
            formatTimestamp(log.timestamp || log.createdAt),
            log.source,
            log.logLevel,
            content
          ].join(',');
        })
      ].join('\n');

      // Blob 생성 및 다운로드
      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');

      link.setAttribute('href', url);
      link.setAttribute('download', `logpulse_logs_${new Date().toISOString().slice(0, 10)}.csv`);
      link.style.visibility = 'hidden';

      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

    } catch (err) {
      console.error('로그 다운로드 오류:', err);
      alert('로그 다운로드 중 오류가 발생했습니다.');
    }
  }, [logs]);

  /**
   * 페이지 변경 핸들러
   */
  const handlePageChange = useCallback((newPage) => {
    if (newPage >= 0 && newPage < totalPages) {
      setCurrentPage(newPage);
      // 페이지 변경 시 자동으로 로그 데이터를 다시 로드하지 않음
      // 사용자가 검색 버튼을 눌러야만 로드
    }
  }, [totalPages]);

  /**
   * 로그 상세 정보 표시
   */
  const showLogDetail = useCallback((log) => {
    setSelectedLog(log);
    setShowLogDetails(true);
  }, []);

  /**
   * 로그 상세 모달 닫기
   */
  const closeLogDetail = useCallback(() => {
    setShowLogDetails(false);
    setSelectedLog(null);
  }, []);

  /**
   * 대시보드 새로고침
   */
  const refreshDashboard = () => {
    if (activeTab === 'overview') {
      loadDashboardData();
    } else if (activeTab === 'logs') {
      loadLogData();
    }
  };

  // ===== useEffect 훅들 - 정리된 버전 =====

  // 1. 컴포넌트 마운트 시 소스 목록 로드
  useEffect(() => {
    loadAllSources();
  }, []);

  // 2. 탭 변경 시 초기 데이터 로드
  useEffect(() => {
    if (activeTab === 'overview') {
      loadDashboardData();
    } else if (activeTab === 'logs') {
      // 로그 탭으로 변경 시 자동으로 로그를 로드하지만,
      // 이후 필터 변경은 버튼 클릭 시에만 적용
      loadLogData();
    }
  }, [activeTab]);

  // 3. Overview 탭에서 시간 범위 변경 시 대시보드 데이터 리로드
  useEffect(() => {
    if (activeTab === 'overview' && timeRangeFilter) {
      loadDashboardData();
    }
  }, [timeRangeFilter]);

  /**
   * 차트 데이터 메모이제이션
   */
  const chartData = useMemo(() => {
    if (!dashboardData || !dashboardData.logCounts) {
      return {
        pieData: [],
        sourcesData: [],
        hourlyData: [],
        errorTrendsData: []
      };
    }

    // 파이 차트 데이터
    const pieData = [
      { name: 'ERROR', value: dashboardData.logCounts.error, color: LOG_LEVEL_COLORS.ERROR },
      { name: 'WARN', value: dashboardData.logCounts.warn, color: LOG_LEVEL_COLORS.WARN },
      { name: 'INFO', value: dashboardData.logCounts.info, color: LOG_LEVEL_COLORS.INFO },
      { name: 'DEBUG', value: dashboardData.logCounts.debug, color: LOG_LEVEL_COLORS.DEBUG }
    ].filter(item => item.value > 0);

    // 소스별 통계 데이터
    const sourcesData = dashboardData.sourceStats?.sourceStats || [];

    // 시간별 통계 데이터
    const hourlyData = dashboardData.hourlyStats?.hourlyStats || [];

    // 오류 추세 데이터
    const errorTrendsData = dashboardData.errorTrends?.dailyStats || [];

    return { pieData, sourcesData, hourlyData, errorTrendsData };
  }, [dashboardData]);

  /**
   * 로그 상세 정보 모달
   */
  const LogDetailModal = () => {
    if (!showLogDetails || !selectedLog) return null;

    return (
      <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
        <div className="bg-white rounded-lg shadow-xl w-full max-w-3xl mx-4 max-h-[90vh] flex flex-col">
          {/* 모달 헤더 */}
          <div className="flex items-center justify-between p-6 border-b">
            <h3 className="text-lg font-medium text-gray-900">로그 상세 정보</h3>
            <button
              onClick={closeLogDetail}
              className="text-gray-400 hover:text-gray-600"
            >
              <X className="h-6 w-6" />
            </button>
          </div>

          {/* 모달 본문 */}
          <div className="flex-1 overflow-y-auto p-6">
            <div className="space-y-4">
              {/* 시간 및 레벨 */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700">시간</label>
                  <p className="mt-1 text-sm text-gray-900">
                    {formatTimestamp(selectedLog.createdAt || selectedLog.timestamp)}
                  </p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">레벨</label>
                  <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${getLogLevelClass(selectedLog.logLevel)}`}>
                    {selectedLog.logLevel}
                  </span>
                </div>
              </div>

              {/* 소스 */}
              <div>
                <label className="block text-sm font-medium text-gray-700">소스</label>
                <p className="mt-1 text-sm text-gray-900">{selectedLog.source}</p>
              </div>

              {/* 메시지 */}
              <div>
                <label className="block text-sm font-medium text-gray-700">메시지</label>
                <p className="mt-1 text-sm text-gray-900 whitespace-pre-wrap bg-gray-50 p-3 rounded">
                  {selectedLog.content}
                </p>
              </div>

              {/* ID */}
              <div>
                <label className="block text-sm font-medium text-gray-700">로그 ID</label>
                <p className="mt-1 text-sm text-gray-900">{selectedLog.id}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  };

  // 메인 대시보드 컴포넌트의 return 부분
  return (
    <div className="min-h-screen bg-gray-50">
      {/* 헤더 */}
      <header className="bg-white shadow">
        <div className="container mx-auto px-4 py-4 flex justify-between items-center">
          <div className="flex items-center">
            <Zap className="h-8 w-8 text-indigo-600 mr-3" />
            <h1 className="text-2xl font-bold text-gray-900">LogPulse</h1>
          </div>
          <div className="flex items-center space-x-4">
            {/* 시간 범위 필터 */}
            <select
              className="text-sm border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
              value={timeRangeFilter}
              onChange={(e) => setTimeRangeFilter(e.target.value)}
            >
              <option value="24h">최근 24시간</option>
              <option value="7d">최근 7일</option>
              <option value="30d">최근 30일</option>
            </select>

            {/* 새로고침 버튼 */}
            <button
              onClick={refreshDashboard}
              className="flex items-center px-3 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-50"
              disabled={isLoading}
            >
              <RefreshCw className={`h-4 w-4 mr-1 ${isLoading ? 'animate-spin' : ''}`} />
              새로고침
            </button>
          </div>
        </div>
      </header>

      {/* 탭 네비게이션 */}
      <nav className="bg-white border-b">
        <div className="container mx-auto px-4">
          <div className="flex space-x-8">
            <button
              className={`py-4 px-2 border-b-2 font-medium text-sm ${activeTab === 'overview' ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
              onClick={() => setActiveTab('overview')}
            >
              개요
            </button>
            <button
              className={`py-4 px-2 border-b-2 font-medium text-sm ${activeTab === 'logs' ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
              onClick={() => setActiveTab('logs')}
            >
              로그 탐색기
            </button>
          </div>
        </div>
      </nav>

      {/* 메인 콘텐츠 */}
      <main className="container mx-auto px-4 py-6">
        {/* 로딩 인디케이터 */}
        {isLoading && (
          <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
            <div className="bg-white p-6 rounded-lg shadow-xl flex items-center">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-700 mr-3"></div>
              <p className="text-indigo-700 font-medium">데이터 로딩 중...</p>
            </div>
          </div>
        )}

        {/* 개요 탭 컨텐츠 - 이전의 OverviewTab 컴포넌트 내용을 여기로 이동 */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {error && (
              <div className="bg-red-50 border-l-4 border-red-500 p-4 mb-4">
                <div className="flex items-center">
                  <AlertTriangle className="h-5 w-5 text-red-500 mr-2" />
                  <p className="text-red-700">{error}</p>
                </div>
              </div>
            )}

            {/* 시스템 상태 카드 */}
            {systemStatus && (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                {/* 처리율 카드 */}
                <div className="bg-white p-6 rounded-lg shadow-md">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-gray-500 text-sm font-medium">처리율</p>
                      <p className="text-3xl font-bold mt-1">{systemStatus.processedRate?.toLocaleString() || 0}</p>
                      <p className="text-sm text-gray-500 mt-1">로그/분 (5분 평균)</p>
                      <p className="text-xs text-gray-400 mt-1">1분마다 갱신</p>
                    </div>
                    <div className="bg-green-100 p-3 rounded-full">
                      <Activity className="h-6 w-6 text-green-600" />
                    </div>
                  </div>
                </div>

                {/* 오류율 카드 */}
                <div className="bg-white p-6 rounded-lg shadow-md">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-gray-500 text-sm font-medium">오류율</p>
                      <p className="text-3xl font-bold mt-1">{systemStatus.errorRate || 0}%</p>
                      <p className="text-sm text-gray-500 mt-1">전체 로그 대비 (5분 평균)</p>
                      <p className="text-xs text-gray-400 mt-1">1분마다 갱신</p>
                    </div>
                    <div className="bg-red-100 p-3 rounded-full">
                      <AlertTriangle className="h-6 w-6 text-red-600" />
                    </div>
                  </div>
                </div>

                {/* 평균 응답 시간 카드 */}
                <div className="bg-white p-6 rounded-lg shadow-md">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-gray-500 text-sm font-medium">API 응답 시간</p>
                      <p className="text-3xl font-bold mt-1">{systemStatus.avgResponseTime || 0}ms</p>
                      <p className="text-sm text-gray-500 mt-1">최근 5분 평균</p>
                      <p className="text-xs text-gray-400 mt-1">1분마다 갱신</p>
                    </div>
                    <div className="bg-blue-100 p-3 rounded-full">
                      <Clock className="h-6 w-6 text-blue-600" />
                    </div>
                  </div>
                </div>

                {/* 시스템 가동 시간 카드 */}
                <div className="bg-white p-6 rounded-lg shadow-md">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-gray-500 text-sm font-medium">시스템 가동 시간</p>
                      <p className="text-2xl font-bold mt-1">{systemStatus.uptime || '0d 0h 0m'}</p>
                      <p className="text-sm text-gray-500 mt-1">현재 세션</p>
                    </div>
                    <div className="bg-purple-100 p-3 rounded-full">
                      <Database className="h-6 w-6 text-purple-600" />
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* 차트 영역 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* 로그 레벨 분포 파이 차트 */}
              <div className="bg-white p-6 rounded-lg shadow-md">
                <h2 className="text-lg font-semibold mb-4">로그 레벨 분포</h2>
                <div className="h-80">
                  {chartData.pieData.length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={chartData.pieData}
                          cx="50%"
                          cy="50%"
                          labelLine={false}
                          outerRadius={80}
                          fill="#8884d8"
                          dataKey="value"
                          label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                        >
                          {chartData.pieData.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={entry.color} />
                          ))}
                        </Pie>
                        <Tooltip
                          formatter={(value) => [value.toLocaleString(), '로그 수']}
                        />
                        <Legend />
                      </PieChart>
                    </ResponsiveContainer>
                  ) : (
                    <div className="flex items-center justify-center h-full">
                      <div className="text-center text-gray-500">
                        <AlertOctagon className="h-12 w-12 mx-auto mb-2" />
                        <p>로그 데이터가 없습니다</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* 최근 오류 로그 */}
              <div className="bg-white p-6 rounded-lg shadow-md">
                <h2 className="text-lg font-semibold mb-4">최근 오류 로그</h2>
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">시간</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">소스</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">메시지</th>
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {recentErrors && recentErrors.length > 0 ? (
                        recentErrors.map((log) => (
                          <tr key={log.id} className="hover:bg-gray-50 cursor-pointer" onClick={() => showLogDetail(log)}>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                              {formatTimestamp(log.createdAt || log.timestamp)}
                            </td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                              {log.source}
                            </td>
                            <td className="px-6 py-4 text-sm text-gray-500 truncate max-w-md">
                              {log.content}
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan="3" className="px-6 py-4 text-sm text-center text-gray-500">
                            최근 오류 로그가 없습니다
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            {/* 시간별 로그 볼륨 차트 */}
            <div className="bg-white p-6 rounded-lg shadow-md">
              <h2 className="text-lg font-semibold mb-4">시간별 로그 볼륨</h2>
              <div className="h-80">
                {chartData.hourlyData && chartData.hourlyData.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                      data={chartData.hourlyData}
                      margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="hour"/>
                      <YAxis />
                      <Tooltip
                        formatter={(value) => [value.toLocaleString(), '로그 수']}
                      />
                      <Legend />
                      <Area
                        type="monotone"
                        dataKey="error"
                        stackId="1"
                        stroke={LOG_LEVEL_COLORS.ERROR}
                        fill={LOG_LEVEL_COLORS.ERROR}
                        name="ERROR"
                      />
                      <Area
                        type="monotone"
                        dataKey="warn"
                        stackId="1"
                        stroke={LOG_LEVEL_COLORS.WARN}
                        fill={LOG_LEVEL_COLORS.WARN}
                        name="WARN"
                      />
                      <Area
                        type="monotone"
                        dataKey="info"
                        stackId="1"
                        stroke={LOG_LEVEL_COLORS.INFO}
                        fill={LOG_LEVEL_COLORS.INFO}
                        name="INFO"
                      />
                      <Area
                        type="monotone"
                        dataKey="debug"
                        stackId="1"
                        stroke={LOG_LEVEL_COLORS.DEBUG}
                        fill={LOG_LEVEL_COLORS.DEBUG}
                        name="DEBUG"
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="flex items-center justify-center h-full">
                    <div className="text-center text-gray-500">
                      <AlertOctagon className="h-12 w-12 mx-auto mb-2" />
                      <p>시간별 로그 데이터가 없습니다</p>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* 소스별 로그 통계 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* 소스별 로그 수 차트 */}
              <div className="bg-white p-6 rounded-lg shadow-md">
                <h2 className="text-lg font-semibold mb-4">소스별 로그 분포</h2>
                <div className="h-80">
                  {chartData.sourcesData && chartData.sourcesData.length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={chartData.sourcesData.slice(0, 10)} // 상위 10개만 표시
                        margin={{ top: 20, right: 30, left: 20, bottom: 60 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis
                          dataKey="source"
                          angle={-45}
                          textAnchor="end"
                          height={60}
                          interval={0}
                        />
                        <YAxis />
                        <Tooltip
                          formatter={(value) => [value.toLocaleString(), '로그 수']}
                        />
                        <Legend />
                        <Bar
                          dataKey="count"
                          name="로그 수"
                          fill="#4FC3F7"
                        />
                      </BarChart>
                    </ResponsiveContainer>
                  ) : (
                    <div className="flex items-center justify-center h-full">
                      <div className="text-center text-gray-500">
                        <AlertOctagon className="h-12 w-12 mx-auto mb-2" />
                        <p>소스별 로그 데이터가 없습니다</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* 오류 추세 차트 */}
              <div className="bg-white p-6 rounded-lg shadow-md">
                <h2 className="text-lg font-semibold mb-4">오류 추세</h2>
                <div className="h-80">
                  {chartData.errorTrendsData && chartData.errorTrendsData.length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart
                        data={chartData.errorTrendsData}
                        margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis
                          dataKey="date"
                          tickFormatter={(date) => {
                            if (!date) return '';
                            const d = new Date(date);
                            return `${d.getMonth() + 1}/${d.getDate()}`;
                          }}
                        />
                        <YAxis yAxisId="left" orientation="left" stroke="#FF5252" />
                        <YAxis yAxisId="right" orientation="right" stroke="#4FC3F7" />
                        <Tooltip
                          formatter={(value, name) => {
                            if (name === '오류 수') return [value.toLocaleString(), name];
                            if (name === '오류율') return [`${value}%`, name];
                            return [value, name];
                          }}
                        />
                        <Legend />
                        <Line
                          yAxisId="left"
                          type="monotone"
                          dataKey="errorLogs"
                          name="오류 수"
                          stroke="#FF5252"
                          activeDot={{ r: 8 }}
                        />
                        <Line
                          yAxisId="right"
                          type="monotone"
                          dataKey="errorRate"
                          name="오류율"
                          stroke="#4FC3F7"
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <div className="flex items-center justify-center h-full">
                      <div className="text-center text-gray-500">
                        <AlertOctagon className="h-12 w-12 mx-auto mb-2" />
                        <p>오류 추세 데이터가 없습니다</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 로그 탐색기 탭 - 이전의 LogsExplorerTab 컴포넌트 내용을 여기로 이동 */}
        {activeTab === 'logs' && (
          <div className="space-y-6">
            {error && (
              <div className="bg-red-50 border-l-4 border-red-500 p-4 mb-4">
                <div className="flex items-center">
                  <AlertTriangle className="h-5 w-5 text-red-500 mr-2" />
                  <p className="text-red-700">{error}</p>
                </div>
              </div>
            )}

            {/* 필터 및 검색 영역 */}
            <div className="bg-white p-6 rounded-lg shadow-md">
              <div className="flex flex-col md:flex-row items-end space-y-4 md:space-y-0 md:space-x-4">
                {/* 검색 입력 */}
                <div className="w-full md:w-auto flex-1">
                  <label htmlFor="search-query" className="block text-xs font-medium text-gray-700 mb-1">검색어</label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                      <Search className="h-5 w-5 text-gray-400" />
                    </div>
                    <input
                      id="search-query"
                      type="text"
                      className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md text-sm placeholder-gray-500 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                      placeholder="로그 검색..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          loadLogData();
                        }
                      }}
                      autoComplete="off"
                    />
                  </div>
                </div>

                <div className="flex flex-col md:flex-row space-y-4 md:space-y-0 md:space-x-4">
                  {/* 소스 필터 */}
                  <div className="w-full md:w-48">
                    <label htmlFor="source-filter" className="block text-xs font-medium text-gray-700 mb-1">소스</label>
                    <select
                      id="source-filter"
                      className="block w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                      value={sourceFilter}
                      onChange={(e) => setSourceFilter(e.target.value)}
                    >
                      {allSourcesList.map(source => (
                        <option key={source} value={source}>
                          {source === 'all' ? '모든 소스' : source}
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* 레벨 필터 */}
                  <div className="w-full md:w-40">
                    <label htmlFor="level-filter" className="block text-xs font-medium text-gray-700 mb-1">레벨</label>
                    <select
                      id="level-filter"
                      className="block w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                      value={levelFilter}
                      onChange={(e) => setLevelFilter(e.target.value)}
                    >
                      <option value="all">모든 레벨</option>
                      {LOG_LEVELS.map(level => (
                        <option key={level} value={level}>{level}</option>
                      ))}
                    </select>
                  </div>

                  {/* 페이지 크기 */}
                  <div className="w-full md:w-32">
                    <label htmlFor="page-size" className="block text-xs font-medium text-gray-700 mb-1">페이지 크기</label>
                    <select
                      id="page-size"
                      className="block w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                      value={pageSize}
                      onChange={(e) => setPageSize(Number(e.target.value))}
                    >
                      <option value={10}>10</option>
                      <option value={20}>20</option>
                      <option value={50}>50</option>
                      <option value={100}>100</option>
                    </select>
                  </div>

                  {/* 검색 버튼 */}
                  <button
                    className="flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none md:self-end"
                    onClick={loadLogData}
                  >
                    <Search className="h-4 w-4 mr-1" />
                    검색
                  </button>

                  {/* 다운로드 버튼 */}
                  <button
                    className="flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none md:self-end"
                    onClick={downloadLogs}
                    disabled={logs.length === 0}
                  >
                    <DownloadCloud className="h-4 w-4 mr-1" />
                    CSV 내보내기
                  </button>
                </div>
              </div>
            </div>

            {/* 로그 테이블 */}
            <div className="bg-white rounded-lg shadow-md">
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">타임스탬프</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">소스</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">레벨</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">메시지</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {logs.length > 0 ? (
                      logs.map((log) => (
                        <tr key={log.id} className="hover:bg-gray-50 cursor-pointer" onClick={() => showLogDetail(log)}>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                            {formatTimestamp(log.createdAt || log.timestamp)}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                            {log.source}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap">
                            <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${getLogLevelClass(log.logLevel)}`}>
                              {log.logLevel}
                            </span>
                          </td>
                          <td className="px-6 py-4 text-sm text-gray-500 truncate max-w-md">
                            {log.content}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan="4" className="px-6 py-4 text-sm text-center text-gray-500">
                          {isLoading ? (
                            <div className="flex justify-center items-center">
                              <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-indigo-700 mr-2"></div>
                              데이터 로딩 중...
                            </div>
                          ) : (
                            searchQuery || levelFilter !== 'all' || sourceFilter !== 'all'
                              ? '검색 조건에 맞는 로그가 없습니다'
                              : '로그 데이터가 없습니다'
                          )}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {/* 페이지네이션 */}
              {logs.length > 0 && (
                <div className="px-6 py-3 flex items-center justify-between border-t border-gray-200 bg-gray-50">
                  <div className="flex items-center text-sm text-gray-700">
                    <span>
                      {totalElements === 0 ? '결과 없음' :
                        `${currentPage * pageSize + 1}부터 ${Math.min((currentPage + 1) * pageSize, totalElements)}까지, 총 ${totalElements}개 로그`}
                    </span>
                  </div>

                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => {
                        handlePageChange(currentPage - 1);
                        loadLogData();
                      }}
                      disabled={currentPage === 0}
                      className="relative inline-flex items-center px-2 py-2 border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed rounded-l-md"
                    >
                      <ChevronLeft className="h-4 w-4" />
                      이전
                    </button>

                    <div className="flex items-center space-x-1">
                      {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
                        let pageNum;
                        if (totalPages <= 5) {
                          pageNum = i;
                        } else if (currentPage < 2) {
                          pageNum = i;
                        } else if (currentPage >= totalPages - 2) {
                          pageNum = totalPages - 5 + i;
                        } else {
                          pageNum = currentPage - 2 + i;
                        }

                        if (pageNum < 0 || pageNum >= totalPages) return null;

                        return (
                          <button
                            key={pageNum}
                            onClick={() => {
                              handlePageChange(pageNum);
                              loadLogData();
                            }}
                            className={`relative inline-flex items-center px-3 py-2 border text-sm font-medium ${
                              currentPage === pageNum
                                ? 'bg-indigo-50 border-indigo-500 text-indigo-600'
                                : 'bg-white border-gray-300 text-gray-500 hover:bg-gray-50'
                            }`}
                          >
                            {pageNum + 1}
                          </button>
                        );
                      })}
                    </div>

                    <button
                      onClick={() => {
                        handlePageChange(currentPage + 1);
                        loadLogData();
                      }}
                      disabled={currentPage >= totalPages - 1}
                      className="relative inline-flex items-center px-2 py-2 border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed rounded-r-md"
                    >
                      다음
                      <ChevronRight className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </main>

      {/* 로그 상세 모달 */}
      <LogDetailModal />
    </div>
  );
}