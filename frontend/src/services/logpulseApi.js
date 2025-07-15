/**
 * LogPulse REST API 서비스
 * 백엔드 서버와의 통신을 담당하는 클라이언트 모듈
 */

// API 기본 설정
const API_BASE_URL = 'http://localhost:8080/api/v1';

/**
 * 기본 HTTP 요청 메서드
 * @param {string} endpoint - API 엔드포인트 경로
 * @param {Object} options - fetch 옵션
 * @returns {Promise<any>} - JSON 응답 또는 에러
 */
async function fetchApi(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;

  // 기본 헤더 설정
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers
  };

  try {
    const response = await fetch(url, { ...options, headers });

    // HTTP 에러 처리
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.message || `API 요청 실패: ${response.status} ${response.statusText}`);
    }

    // 응답 데이터가 없는 경우 처리
    if (response.status === 204) {
      return null;
    }

    // JSON 응답 파싱
    return await response.json();
  } catch (error) {
    console.error(`API 호출 오류 (${endpoint}):`, error);
    throw error;
  }
}

/**
 * LogPulse API 클라이언트
 */
const logpulseApi = {
  /**
   * 로그 엔트리 목록 조회
   * @param {Object} params - 필터 파라미터
   * @returns {Promise<Array>} - 로그 엔트리 배열
   */
  getLogs: async (params = {}) => {
    let endpoint = '/logs';
    const queryParams = new URLSearchParams();

    // 필터 파라미터 적용
    if (params.level) {
      endpoint = `/logs/level/${params.level}`;
    } else if (params.start && params.end) {
      queryParams.append('start', params.start);
      queryParams.append('end', params.end);
      endpoint = `/logs/period?${queryParams.toString()}`;
    }

    return fetchApi(endpoint);
  },

  /**
   * 로그 검색 API
   * @param {Object} searchParams - 검색 파라미터
   * @returns {Promise<Array>} - 검색 결과 배열
   */
  searchLogs: async (searchParams = {}) => {
    let endpoint = '/logs/search';

    if (searchParams.level) {
      endpoint = `/logs/search/level/${searchParams.level}`;
    } else if (searchParams.source) {
      endpoint = `/logs/search/source?source=${encodeURIComponent(searchParams.source)}`;
    } else if (searchParams.content) {
      endpoint = `/logs/search/content?content=${encodeURIComponent(searchParams.content)}`;
    } else if (searchParams.keyword) {
      endpoint = `/logs/search/keyword?keyword=${encodeURIComponent(searchParams.keyword)}`;
    } else if (searchParams.start && searchParams.end) {
      const queryParams = new URLSearchParams();
      queryParams.append('start', searchParams.start);
      queryParams.append('end', searchParams.end);
      endpoint = `/logs/search/period?${queryParams.toString()}`;
    }

    return fetchApi(endpoint);
  },

  /**
   * 새 로그 생성
   * @param {Object} logData - 로그 데이터
   * @returns {Promise<Object>} - 생성된 로그 정보
   */
  createLog: async (logData) => {
    return fetchApi('/logs', {
      method: 'POST',
      body: JSON.stringify(logData)
    });
  },

  /**
   * 통합 로그 이벤트 제출
   * @param {Object} logEvent - 로그 이벤트 데이터
   * @returns {Promise<Object>} - 응답 결과
   */
  submitLogEvent: async (logEvent) => {
    return fetchApi('/logs/integration', {
      method: 'POST',
      body: JSON.stringify(logEvent)
    });
  },

  /**
   * 로그 통계 데이터 조회
   * @returns {Promise<Object>} - 통계 데이터
   */
  getLogStatistics: async (timeRange = 'day') => {
    // 실제 API가 있다고 가정하고 예시 구현
    // 백엔드에 해당 API가 없으므로 모의 데이터를 제공

    // 모의 응답 데이터
    const mockStats = {
      summary: {
        total: 25647,
        error: 324,
        warn: 843,
        info: 18432,
        debug: 6048,
        errorRate: 1.26
      },
      bySource: {
        'api-service': 8765,
        'user-service': 5432,
        'payment-service': 4321,
        'notification-service': 3456,
        'auth-service': 3673
      },
      byHour: [
        { hour: 0, count: 876, error: 12 },
        { hour: 1, count: 543, error: 8 },
        // ... 더 많은 시간별 데이터
        { hour: 23, count: 986, error: 15 }
      ]
    };

    // 실제 구현에서는 아래 주석을 해제하고 실제 API를 호출
    // return fetchApi(`/logs/statistics?timeRange=${timeRange}`);

    // 모의 데이터 반환 (지연 시뮬레이션)
    return new Promise((resolve) => {
      setTimeout(() => resolve(mockStats), 500);
    });
  },

  /**
   * 성능 테스트 실행
   * @param {Object} testParams - 테스트 매개변수
   * @returns {Promise<Object>} - 테스트 결과
   */
  runPerformanceTest: async (testParams) => {
    return fetchApi('/performance/test', {
      method: 'POST',
      body: JSON.stringify(testParams)
    });
  },

  /**
   * 시스템 상태 조회
   * @returns {Promise<Object>} - 시스템 상태 정보
   */
  getSystemStatus: async () => {
    // 모의 시스템 상태 정보
    return new Promise((resolve) => {
      setTimeout(() => resolve({
        processedRate: 16542,
        errorRate: 1.2,
        avgResponseTime: 85,
        uptime: '12d 4h 32m',
        activeConnections: 23,
        cpuUsage: 38.5,
        memoryUsage: 42.7,
        diskUsage: 68.3
      }), 300);
    });
  }
};

export default logpulseApi;