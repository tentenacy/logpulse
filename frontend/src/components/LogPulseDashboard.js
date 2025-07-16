import { useState, useEffect } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  LineChart, Line, PieChart, Pie, Cell
} from 'recharts';
import {
  AlertTriangle, Activity, Clock, Database,
  Search, Filter, DownloadCloud, RefreshCw,
  ChevronDown, X, Zap, Info, AlertOctagon
} from 'lucide-react';

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042'];
const LOG_LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG'];

export default function LogPulseDashboard() {
  // 상태 관리
  const [activeTab, setActiveTab] = useState('overview');
  const [logs, setLogs] = useState([]);
  const [logStats, setLogStats] = useState({ error: 0, warn: 0, info: 0, debug: 0, total: 0 });
  const [timeRangeFilter, setTimeRangeFilter] = useState('last24h');
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [showLogDetails, setShowLogDetails] = useState(false);
  const [selectedLog, setSelectedLog] = useState(null);
  const [sourceFilter, setSourceFilter] = useState('all');
  const [levelFilter, setLevelFilter] = useState('all');
  const [systemStats, setSystemStats] = useState({
    processedRate: 0,
    errorRate: 0,
    avgResponseTime: 0,
    uptime: '0d 0h 0m'
  });
  const [processingHistory, setProcessingHistory] = useState([]);

  // 모의 데이터 생성 및 로딩
  useEffect(() => {
    setIsLoading(true);

    // 모의 로그 데이터 생성
    const mockLogs = generateMockLogs(100);
    const mockStats = calculateLogStats(mockLogs);
    const mockProcessingHistory = generateProcessingHistory();

    // 로딩 시뮬레이션
    setTimeout(() => {
      setLogs(mockLogs);
      setLogStats(mockStats);
      setSystemStats({
        processedRate: 16542,
        errorRate: 1.2,
        avgResponseTime: 85,
        uptime: '12d 4h 32m'
      });
      setProcessingHistory(mockProcessingHistory);
      setIsLoading(false);
    }, 1000);
  }, [timeRangeFilter]);

  // 로그 통계 계산
  const calculateLogStats = (logData) => {
    const stats = { error: 0, warn: 0, info: 0, debug: 0, total: logData.length };

    logData.forEach(log => {
      switch(log.logLevel) {
        case 'ERROR': stats.error++; break;
        case 'WARN': stats.warn++; break;
        case 'INFO': stats.info++; break;
        case 'DEBUG': stats.debug++; break;
        default: break;
      }
    });

    return stats;
  };

  // 필터링된 로그 가져오기
  const getFilteredLogs = () => {
    return logs.filter(log => {
      const sourceMatch = sourceFilter === 'all' || log.source === sourceFilter;
      const levelMatch = levelFilter === 'all' || log.logLevel === levelFilter;
      const searchMatch = searchQuery === '' ||
        log.content.toLowerCase().includes(searchQuery.toLowerCase()) ||
        log.source.toLowerCase().includes(searchQuery.toLowerCase());

      return sourceMatch && levelMatch && searchMatch;
    });
  };

  // 로그 상세 정보 표시
  const showLogDetail = (log) => {
    setSelectedLog(log);
    setShowLogDetails(true);
  };

  // 모의 로그 데이터 생성 함수
  const generateMockLogs = (count) => {
    const sources = ['api-service', 'user-service', 'payment-service', 'notification-service', 'auth-service'];
    const logTypes = [
      {level: 'ERROR', prefix: 'Failed to'},
      {level: 'WARN', prefix: 'Potential issue with'},
      {level: 'INFO', prefix: 'Successfully processed'},
      {level: 'DEBUG', prefix: 'Checking status of'}
    ];
    const actions = [
      'connect to database',
      'authenticate user',
      'process payment',
      'send notification',
      'validate token',
      'update user profile',
      'retrieve data from cache',
      'communicate with external API'
    ];

    return Array.from({ length: count }, (_, i) => {
      const logType = logTypes[Math.floor(Math.random() * logTypes.length)];
      const source = sources[Math.floor(Math.random() * sources.length)];
      const action = actions[Math.floor(Math.random() * actions.length)];
      const now = new Date();
      const timestamp = new Date(now - Math.floor(Math.random() * 86400000 * 3)); // Up to 3 days ago

      return {
        id: `log-${i + 1}`,
        timestamp,
        source,
        logLevel: logType.level,
        content: `${logType.prefix} ${action}. Transaction ID: TXN-${Math.floor(Math.random() * 1000000)}`,
        ip: `192.168.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`,
        user: Math.random() > 0.7 ? `user-${Math.floor(Math.random() * 1000)}` : null
      };
    });
  };

  // 모의 처리 이력 데이터 생성
  const generateProcessingHistory = () => {
    const hours = Array.from({ length: 24 }, (_, i) => i);

    return hours.map(hour => {
      const baseValue = 12000 + Math.random() * 8000;
      const errorValue = baseValue * 0.02 * Math.random();
      const warnValue = baseValue * 0.05 * Math.random();
      const debugValue = baseValue * 0.3 * Math.random();
      const infoValue = baseValue - errorValue - warnValue - debugValue;

      return {
        hour: `${hour}:00`,
        total: Math.floor(baseValue),
        error: Math.floor(errorValue),
        warn: Math.floor(warnValue),
        info: Math.floor(infoValue),
        debug: Math.floor(debugValue)
      };
    });
  };

  // 파이 차트 데이터
  const pieChartData = [
    { name: 'ERROR', value: logStats.error, color: '#FF5252' },
    { name: 'WARN', value: logStats.warn, color: '#FFB74D' },
    { name: 'INFO', value: logStats.info, color: '#4FC3F7' },
    { name: 'DEBUG', value: logStats.debug, color: '#9CCC65' }
  ];

  // 로그 다운로드
  const downloadLogs = () => {
    const filteredLogs = getFilteredLogs();
    const csvContent = "data:text/csv;charset=utf-8,"
      + "ID,Timestamp,Source,Level,Content,IP,User\n"
      + filteredLogs.map(log =>
          `${log.id},${log.timestamp.toISOString()},${log.source},${log.logLevel},"${log.content}",${log.ip},${log.user || ""}`)
            .join("\n");

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `logpulse_logs_${new Date().toISOString()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // 로그 새로고침
  const refreshLogs = () => {
    setIsLoading(true);

    setTimeout(() => {
      const freshLogs = generateMockLogs(100);
      setLogs(freshLogs);
      setLogStats(calculateLogStats(freshLogs));
      setIsLoading(false);
    }, 1000);
  };

  // 로그 레벨에 따른 배경색 설정
  const getLogLevelBg = (level) => {
    switch(level) {
      case 'ERROR': return 'bg-red-100 text-red-800';
      case 'WARN': return 'bg-yellow-100 text-yellow-800';
      case 'INFO': return 'bg-blue-100 text-blue-800';
      case 'DEBUG': return 'bg-green-100 text-green-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  // 소스 목록 가져오기
  const getSources = () => {
    const sources = new Set(logs.map(log => log.source));
    return ['all', ...Array.from(sources)];
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 헤더 */}
      <header className="bg-indigo-700 text-white shadow-lg">
        <div className="container mx-auto px-4 py-4 flex justify-between items-center">
          <div className="flex items-center space-x-2">
            <Zap className="h-8 w-8" />
            <h1 className="text-2xl font-bold">LogPulse Dashboard</h1>
          </div>
          <div className="flex items-center space-x-4">
            <div className="flex items-center bg-indigo-800 px-3 py-1 rounded-md">
              <Clock className="h-4 w-4 mr-2" />
              <select
                className="bg-transparent text-white text-sm outline-none"
                value={timeRangeFilter}
                onChange={e => setTimeRangeFilter(e.target.value)}
              >
                <option value="last1h">Last 1 Hour</option>
                <option value="last6h">Last 6 Hours</option>
                <option value="last24h">Last 24 Hours</option>
                <option value="last7d">Last 7 Days</option>
              </select>
            </div>
            <button
              className="bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1 rounded-md flex items-center text-sm"
              onClick={refreshLogs}
            >
              <RefreshCw className="h-4 w-4 mr-1" /> Refresh
            </button>
          </div>
        </div>
      </header>

      {/* 탭 네비게이션 */}
      <nav className="bg-white shadow">
        <div className="container mx-auto px-4">
          <div className="flex space-x-8">
            <button
              className={`py-4 px-2 border-b-2 font-medium text-sm ${activeTab === 'overview' ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
              onClick={() => setActiveTab('overview')}
            >
              Overview
            </button>
            <button
              className={`py-4 px-2 border-b-2 font-medium text-sm ${activeTab === 'logs' ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
              onClick={() => setActiveTab('logs')}
            >
              Logs Explorer
            </button>
            <button
              className={`py-4 px-2 border-b-2 font-medium text-sm ${activeTab === 'stats' ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
              onClick={() => setActiveTab('stats')}
            >
              Statistics
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
              <p className="text-indigo-700 font-medium">Loading data...</p>
            </div>
          </div>
        )}

        {/* 개요 탭 */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {/* 시스템 상태 카드 */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
              <div className="bg-white p-6 rounded-lg shadow">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-gray-500 text-sm font-medium">Processing Rate</p>
                    <p className="text-3xl font-bold mt-1">{systemStats.processedRate.toLocaleString()}</p>
                    <p className="text-sm text-gray-500 mt-1">logs/minute</p>
                  </div>
                  <div className="bg-green-100 p-3 rounded-full">
                    <Activity className="h-6 w-6 text-green-600" />
                  </div>
                </div>
              </div>

              <div className="bg-white p-6 rounded-lg shadow">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-gray-500 text-sm font-medium">Error Rate</p>
                    <p className="text-3xl font-bold mt-1">{systemStats.errorRate}%</p>
                    <p className="text-sm text-gray-500 mt-1">of total logs</p>
                  </div>
                  <div className="bg-red-100 p-3 rounded-full">
                    <AlertTriangle className="h-6 w-6 text-red-600" />
                  </div>
                </div>
              </div>

              <div className="bg-white p-6 rounded-lg shadow">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-gray-500 text-sm font-medium">Avg Response Time</p>
                    <p className="text-3xl font-bold mt-1">{systemStats.avgResponseTime}</p>
                    <p className="text-sm text-gray-500 mt-1">milliseconds</p>
                  </div>
                  <div className="bg-blue-100 p-3 rounded-full">
                    <Clock className="h-6 w-6 text-blue-600" />
                  </div>
                </div>
              </div>

              <div className="bg-white p-6 rounded-lg shadow">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-gray-500 text-sm font-medium">Uptime</p>
                    <p className="text-3xl font-bold mt-1">{systemStats.uptime}</p>
                    <p className="text-sm text-gray-500 mt-1">days:hours:minutes</p>
                  </div>
                  <div className="bg-purple-100 p-3 rounded-full">
                    <Database className="h-6 w-6 text-purple-600" />
                  </div>
                </div>
              </div>
            </div>

            {/* 처리 이력 차트 */}
            <div className="bg-white p-6 rounded-lg shadow">
              <h2 className="text-lg font-semibold mb-4">Log Processing History (24 Hours)</h2>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={processingHistory}
                    margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="hour" />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="error" stackId="a" fill="#FF5252" name="ERROR" />
                    <Bar dataKey="warn" stackId="a" fill="#FFB74D" name="WARN" />
                    <Bar dataKey="info" stackId="a" fill="#4FC3F7" name="INFO" />
                    <Bar dataKey="debug" stackId="a" fill="#9CCC65" name="DEBUG" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* 로그 통계 및 최근 로그 */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* 로그 레벨 분포 */}
              <div className="bg-white p-6 rounded-lg shadow lg:col-span-1">
                <h2 className="text-lg font-semibold mb-4">Log Level Distribution</h2>
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={pieChartData}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={80}
                        paddingAngle={5}
                        dataKey="value"
                        label={({name, percent}) => `${name} ${(percent * 100).toFixed(0)}%`}
                      >
                        {pieChartData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => [value, 'Count']} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <div className="mt-4 grid grid-cols-2 gap-2">
                  {pieChartData.map((item) => (
                    <div key={item.name} className="flex items-center">
                      <div className="w-3 h-3 rounded-full mr-2" style={{ backgroundColor: item.color }}></div>
                      <span className="text-sm">{item.name}: {item.value}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* 최근 오류 로그 */}
              <div className="bg-white p-6 rounded-lg shadow lg:col-span-2">
                <div className="flex justify-between items-center mb-4">
                  <h2 className="text-lg font-semibold">Recent Error Logs</h2>
                  <button
                    className="text-indigo-600 hover:text-indigo-800 text-sm font-medium"
                    onClick={() => { setActiveTab('logs'); setLevelFilter('ERROR'); }}
                  >
                    View All
                  </button>
                </div>

                <div className="overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-200">
                      <thead className="bg-gray-50">
                        <tr>
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Timestamp</th>
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Source</th>
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Message</th>
                        </tr>
                      </thead>
                      <tbody className="bg-white divide-y divide-gray-200">
                        {logs.filter(log => log.logLevel === 'ERROR').slice(0, 5).map((log) => (
                          <tr key={log.id} className="hover:bg-gray-50 cursor-pointer" onClick={() => showLogDetail(log)}>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                              {log.timestamp.toLocaleString()}
                            </td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                              {log.source}
                            </td>
                            <td className="px-6 py-4 text-sm text-gray-500 truncate max-w-md">
                              {log.content}
                            </td>
                          </tr>
                        ))}

                        {logs.filter(log => log.logLevel === 'ERROR').length === 0 && (
                          <tr>
                            <td colSpan="3" className="px-6 py-4 text-sm text-center text-gray-500">
                              No error logs found
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 로그 탐색기 탭 */}
        {activeTab === 'logs' && (
          <div className="bg-white rounded-lg shadow">
            {/* 필터 및 검색 */}
            <div className="p-6 border-b">
              <div className="flex flex-col md:flex-row md:items-center space-y-4 md:space-y-0 md:space-x-4">
                <div className="relative flex-1">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Search className="h-5 w-5 text-gray-400" />
                  </div>
                  <input
                    type="text"
                    className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md text-sm placeholder-gray-500 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                    placeholder="Search logs..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>

                <div className="flex space-x-4">
                  <div className="w-48">
                    <label htmlFor="source-filter" className="block text-xs font-medium text-gray-700 mb-1">Source</label>
                    <select
                      id="source-filter"
                      className="block w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                      value={sourceFilter}
                      onChange={(e) => setSourceFilter(e.target.value)}
                    >
                      {getSources().map(source => (
                        <option key={source} value={source}>{source}</option>
                      ))}
                    </select>
                  </div>

                  <div className="w-40">
                    <label htmlFor="level-filter" className="block text-xs font-medium text-gray-700 mb-1">Level</label>
                    <select
                      id="level-filter"
                      className="block w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                      value={levelFilter}
                      onChange={(e) => setLevelFilter(e.target.value)}
                    >
                      <option value="all">All Levels</option>
                      {LOG_LEVELS.map(level => (
                        <option key={level} value={level}>{level}</option>
                      ))}
                    </select>
                  </div>

                  <button
                    className="flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none"
                    onClick={downloadLogs}
                  >
                    <DownloadCloud className="h-4 w-4 mr-1" />
                    Export
                  </button>
                </div>
              </div>
            </div>

            {/* 로그 테이블 */}
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Timestamp</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Source</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Level</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Message</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {getFilteredLogs().map((log) => (
                    <tr key={log.id} className="hover:bg-gray-50 cursor-pointer" onClick={() => showLogDetail(log)}>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {log.timestamp.toLocaleString()}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {log.source}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${getLogLevelBg(log.logLevel)}`}>
                          {log.logLevel}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-500 truncate max-w-md">
                        {log.content}
                      </td>
                    </tr>
                  ))}

                  {getFilteredLogs().length === 0 && (
                    <tr>
                      <td colSpan="4" className="px-6 py-4 text-sm text-center text-gray-500">
                        No logs found matching your filters
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* 페이지네이션 (간단 버전) */}
            <div className="px-6 py-3 flex items-center justify-between border-t border-gray-200">
              <div className="text-sm text-gray-700">
                Showing <span className="font-medium">{getFilteredLogs().length}</span> logs
              </div>

              <div className="flex-1 flex justify-center md:justify-end">
                <nav className="relative z-0 inline-flex rounded-md shadow-sm -space-x-px" aria-label="Pagination">
                  <button className="relative inline-flex items-center px-2 py-2 rounded-l-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50">
                    Previous
                  </button>
                  <button className="relative inline-flex items-center px-2 py-2 rounded-r-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50">
                    Next
                  </button>
                </nav>
              </div>
            </div>
          </div>
        )}

        {/* 통계 탭 */}
        {activeTab === 'stats' && (
          <div className="space-y-6">
            {/* 로그 레벨 통계 */}
            <div className="bg-white p-6 rounded-lg shadow">
              <h2 className="text-lg font-semibold mb-4">Log Volume by Level</h2>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart
                    data={processingHistory}
                    margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="hour" />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Line type="monotone" dataKey="error" stroke="#FF5252" name="ERROR" />
                    <Line type="monotone" dataKey="warn" stroke="#FFB74D" name="WARN" />
                    <Line type="monotone" dataKey="info" stroke="#4FC3F7" name="INFO" />
                    <Line type="monotone" dataKey="debug" stroke="#9CCC65" name="DEBUG" />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* 소스별 통계 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="bg-white p-6 rounded-lg shadow">
                <h2 className="text-lg font-semibold mb-4">Logs by Source</h2>
                <div className="h-80">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={(() => {
                        const sourceStats = {};
                        logs.forEach(log => {
                          sourceStats[log.source] = (sourceStats[log.source] || 0) + 1;
                        });
                        return Object.keys(sourceStats).map(source => ({
                          source,
                          count: sourceStats[source]
                        }));
                      })()}
                      margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="source" />
                      <YAxis />
                      <Tooltip />
                      <Bar dataKey="count" fill="#8884d8" name="Log Count" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>

              {/* 시간대별 오류율 */}
              <div className="bg-white p-6 rounded-lg shadow">
                <h2 className="text-lg font-semibold mb-4">Error Rate Over Time</h2>
                <div className="h-80">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart
                      data={processingHistory.map(entry => ({
                        hour: entry.hour,
                        errorRate: (entry.error / entry.total) * 100
                      }))}
                      margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="hour" />
                      <YAxis unit="%" />
                      <Tooltip formatter={(value) => [value.toFixed(2) + '%', 'Error Rate']} />
                      <Line type="monotone" dataKey="errorRate" stroke="#FF5252" name="Error Rate" />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </div>

            {/* 상세 통계 표 */}
            <div className="bg-white p-6 rounded-lg shadow">
              <h2 className="text-lg font-semibold mb-4">Detailed Statistics</h2>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Source</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Total</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">ERROR</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">WARN</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">INFO</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">DEBUG</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Error Rate</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {(() => {
                      // 소스별 통계 계산
                      const sourceStats = {};
                      logs.forEach(log => {
                        if (!sourceStats[log.source]) {
                          sourceStats[log.source] = { total: 0, ERROR: 0, WARN: 0, INFO: 0, DEBUG: 0 };
                        }
                        sourceStats[log.source].total++;
                        sourceStats[log.source][log.logLevel]++;
                      });

                      return Object.keys(sourceStats).map(source => {
                        const stats = sourceStats[source];
                        const errorRate = (stats.ERROR / stats.total) * 100;

                        return (
                          <tr key={source}>
                            <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{source}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{stats.total}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{stats.ERROR}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{stats.WARN}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{stats.INFO}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{stats.DEBUG}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{errorRate.toFixed(2)}%</td>
                          </tr>
                        );
                      });
                    })()}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* 로그 상세 모달 */}
      {showLogDetails && selectedLog && (
        <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-3xl mx-4 max-h-[90vh] flex flex-col">
            <div className="flex justify-between items-center px-6 py-4 border-b">
              <h3 className="text-lg font-medium text-gray-900">Log Details</h3>
              <button
                className="text-gray-400 hover:text-gray-500"
                onClick={() => setShowLogDetails(false)}
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="p-6 overflow-y-auto flex-grow">
              <div className="grid grid-cols-2 gap-4 mb-6">
                <div>
                  <p className="text-sm font-medium text-gray-500">ID</p>
                  <p className="mt-1 text-sm text-gray-900">{selectedLog.id}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">Timestamp</p>
                  <p className="mt-1 text-sm text-gray-900">{selectedLog.timestamp.toLocaleString()}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">Source</p>
                  <p className="mt-1 text-sm text-gray-900">{selectedLog.source}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">Level</p>
                  <p className="mt-1">
                    <span className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${getLogLevelBg(selectedLog.logLevel)}`}>
                      {selectedLog.logLevel}
                    </span>
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">IP Address</p>
                  <p className="mt-1 text-sm text-gray-900">{selectedLog.ip}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">User</p>
                  <p className="mt-1 text-sm text-gray-900">{selectedLog.user || 'N/A'}</p>
                </div>
              </div>

              <div className="mb-6">
                <p className="text-sm font-medium text-gray-500">Message</p>
                <div className="mt-2 p-4 bg-gray-50 rounded-md">
                  <p className="text-sm text-gray-900 whitespace-pre-wrap">{selectedLog.content}</p>
                </div>
              </div>

              {/* 관련 로그 표시 (같은 소스의 이전/이후 로그) */}
              <div>
                <p className="text-sm font-medium text-gray-500 mb-2">Related Logs</p>
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Timestamp</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Level</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Message</th>
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {logs
                        .filter(log => log.source === selectedLog.source && log.id !== selectedLog.id)
                        .sort((a, b) => Math.abs(a.timestamp - selectedLog.timestamp) - Math.abs(b.timestamp - selectedLog.timestamp))
                        .slice(0, 3)
                        .map(log => (
                          <tr key={log.id} className="hover:bg-gray-50 cursor-pointer" onClick={() => setSelectedLog(log)}>
                            <td className="px-4 py-2 whitespace-nowrap text-xs text-gray-500">
                              {log.timestamp.toLocaleString()}
                            </td>
                            <td className="px-4 py-2 whitespace-nowrap">
                              <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${getLogLevelBg(log.logLevel)}`}>
                                {log.logLevel}
                              </span>
                            </td>
                            <td className="px-4 py-2 text-xs text-gray-500 truncate max-w-xs">
                              {log.content}
                            </td>
                          </tr>
                        ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <div className="px-6 py-4 border-t flex justify-end">
              <button
                className="inline-flex justify-center px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 border border-gray-300 rounded-md hover:bg-gray-200 focus:outline-none"
                onClick={() => setShowLogDetails(false)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}