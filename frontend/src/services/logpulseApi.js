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
 * 쿼리 파라미터 생성 유틸리티 함수
 * @param {Object} params - 파라미터 객체
 * @returns {string} - 쿼리 문자열
 */
function buildQueryParams(params) {
  const queryParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      queryParams.append(key, value);
    }
  });

  const queryString = queryParams.toString();
  return queryString ? `?${queryString}` : '';
}

/**
 * LogPulse API 클라이언트
 */
const logpulseApi = {
  /**
   * 대시보드 통합 통계 정보 조회
   * @param {Object} params - 조회 파라미터 (start, end, source)
   * @returns {Promise<Object>} - 통합 통계 데이터
   */
  getDashboardStats: async (params = {}) => {
    const queryString = buildQueryParams(params);
    return fetchApi(`/dashboard/stats${queryString}`);
  },

  /**
   * 로그 레벨별 카운트 정보 조회
   * @param {Object} params - 조회 파라미터 (start, end, source)
   * @returns {Promise<Object>} - 로그 레벨별 카운트
   */
  getLogCounts: async (params = {}) => {
    const queryString = buildQueryParams(params);
    return fetchApi(`/dashboard/log-counts${queryString}`);
  },

  /**
   * 시간별 로그 통계 조회
   * @param {string} date - 조회할 날짜 (ISO 형식)
   * @returns {Promise<Object>} - 시간별 로그 통계
   */
  getHourlyStats: async (date) => {
    const queryString = date ? `?date=${date}` : '';
    return fetchApi(`/dashboard/hourly-stats${queryString}`);
  },

  /**
   * 소스별 로그 통계 조회
   * @param {Object} params - 조회 파라미터 (start, end)
   * @returns {Promise<Object>} - 소스별 로그 통계
   */
  getSourceStats: async (params = {}) => {
    const queryString = buildQueryParams(params);
    return fetchApi(`/dashboard/source-stats${queryString}`);
  },

  /**
   * 소스별 로그 레벨 통계 조회
   * @param {Object} params - 조회 파라미터 (start, end)
   * @returns {Promise<Object>} - 소스별 로그 레벨 통계
   */
  getSourceLevelStats: async (params = {}) => {
    const queryString = buildQueryParams(params);
    return fetchApi(`/dashboard/source-level-stats${queryString}`);
  },

  /**
   * 시스템 상태 정보 조회
   * @returns {Promise<Object>} - 시스템 상태 정보
   */
  getSystemStatus: async () => {
    return fetchApi('/dashboard/system-status');
  },

  /**
   * 최근 오류 로그 목록 조회
   * @returns {Promise<Object>} - 최근 오류 로그 목록
   */
  getRecentErrors: async () => {
    return fetchApi('/dashboard/recent-errors');
  },

  /**
   * 오류 추세 정보 조회
   * @param {Object} params - 조회 파라미터 (start, end)
   * @returns {Promise<Object>} - 오류 추세 정보
   */
  getErrorTrends: async (params = {}) => {
    const queryString = buildQueryParams(params);
    return fetchApi(`/dashboard/error-trends${queryString}`);
  },

  /**
   * 페이징된 로그 엔트리 목록 조회
   * @param {Object} params - 필터 및 페이징 파라미터
   * @returns {Promise<Object>} - 페이징된 로그 응답
   */
  getLogs: async (params = {}) => {
    const queryString = buildQueryParams({
      page: params.page,
      size: params.size,
      sortBy: params.sortBy || 'createdAt',
      sortDir: params.sortDir || 'desc',
      level: params.level,
      source: params.source,
      start: params.start,
      end: params.end
    });

    return fetchApi(`/logs${queryString}`);
  },

  /**
   * 로그 검색 API (페이징 지원)
   * @param {Object} searchParams - 검색 및 페이징 파라미터
   * @returns {Promise<Object>} - 페이징된 검색 결과
   */
  searchLogs: async (searchParams = {}) => {
    const queryString = buildQueryParams({
      page: searchParams.page,
      size: searchParams.size,
      sortBy: searchParams.sortBy || 'timestamp',
      sortDir: searchParams.sortDir || 'desc',
      keyword: searchParams.keyword,
      level: searchParams.level,
      source: searchParams.source,
      content: searchParams.content,
      start: searchParams.start,
      end: searchParams.end
    });

    return fetchApi(`/logs/search${queryString}`);
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
  }
};

export default logpulseApi;