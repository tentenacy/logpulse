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
  getLogStatistics: async () => {
    const levelStats = await fetchApi('/statistics/level');
    const sourceStats = await fetchApi('/statistics/source');
    const hourlyStats = await fetchApi('/statistics/hourly');

    return {
      levelStats,
      sourceStats,
      hourlyStats
    };
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
    return fetchApi('/system/status');
  }
};

export default logpulseApi;