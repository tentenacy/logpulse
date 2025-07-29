-- 기존 데이터베이스 생성 쿼리
CREATE DATABASE IF NOT EXISTS logpulse;
CREATE DATABASE IF NOT EXISTS logpulse_test;

-- logpulse 데이터베이스 선택
USE logpulse;

-- 시간별 통계 테이블
CREATE TABLE IF NOT EXISTS log_hourly_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_date DATE NOT NULL,
    hour_of_day INT NOT NULL,
    log_level VARCHAR(10) NOT NULL,
    source VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_hourly_stats_unique (log_date, hour_of_day, log_level, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일별 통계 테이블
CREATE TABLE IF NOT EXISTS log_daily_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_date DATE NOT NULL,
    log_level VARCHAR(10) NOT NULL,
    source VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_daily_stats_unique (log_date, log_level, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 소스별 통계 테이블
CREATE TABLE IF NOT EXISTS log_source_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_date DATE NOT NULL,
    source VARCHAR(255) NOT NULL,
    log_level VARCHAR(10) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_source_stats_unique (log_date, source, log_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 통계 데이터 조회를 위한 뷰 생성
CREATE OR REPLACE VIEW v_log_daily_summary AS
SELECT
    log_date,
    SUM(CASE WHEN log_level = 'ERROR' THEN count ELSE 0 END) as error_count,
    SUM(CASE WHEN log_level = 'WARN' THEN count ELSE 0 END) as warn_count,
    SUM(CASE WHEN log_level = 'INFO' THEN count ELSE 0 END) as info_count,
    SUM(CASE WHEN log_level = 'DEBUG' THEN count ELSE 0 END) as debug_count,
    SUM(count) as total_count,
    CASE
        WHEN SUM(count) > 0
        THEN ROUND((SUM(CASE WHEN log_level = 'ERROR' THEN count ELSE 0 END) / SUM(count)) * 100, 2)
        ELSE 0
    END as error_rate
FROM
    log_daily_stats
GROUP BY
    log_date
ORDER BY
    log_date DESC;

-- 소스별 통계 요약 뷰
CREATE OR REPLACE VIEW v_source_summary AS
SELECT
    source,
    SUM(count) as total_count,
    SUM(CASE WHEN log_level = 'ERROR' THEN count ELSE 0 END) as error_count,
    SUM(CASE WHEN log_level = 'WARN' THEN count ELSE 0 END) as warn_count,
    SUM(CASE WHEN log_level = 'INFO' THEN count ELSE 0 END) as info_count,
    SUM(CASE WHEN log_level = 'DEBUG' THEN count ELSE 0 END) as debug_count,
    CASE
        WHEN SUM(count) > 0
        THEN ROUND((SUM(CASE WHEN log_level = 'ERROR' THEN count ELSE 0 END) / SUM(count)) * 100, 2)
        ELSE 0
    END as error_rate
FROM
    log_source_stats
GROUP BY
    source
ORDER BY
    total_count DESC;

-- 테스트 데이터베이스에도 동일한 테이블 구조 생성
USE logpulse_test;

-- 시간별 통계 테이블 (테스트 DB)
CREATE TABLE IF NOT EXISTS log_hourly_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_date DATE NOT NULL,
    hour_of_day INT NOT NULL,
    log_level VARCHAR(10) NOT NULL,
    source VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_hourly_stats_unique (log_date, hour_of_day, log_level, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일별 통계 테이블 (테스트 DB)
CREATE TABLE IF NOT EXISTS log_daily_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_date DATE NOT NULL,
    log_level VARCHAR(10) NOT NULL,
    source VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_daily_stats_unique (log_date, log_level, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 소스별 통계 테이블 (테스트 DB)
CREATE TABLE IF NOT EXISTS log_source_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_date DATE NOT NULL,
    source VARCHAR(255) NOT NULL,
    log_level VARCHAR(10) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_source_stats_unique (log_date, source, log_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 통계 데이터 조회를 위한 뷰 생성 (테스트 DB)
CREATE OR REPLACE VIEW v_log_daily_summary AS
SELECT
    log_date,
    SUM(CASE WHEN log_level = 'ERROR' THEN count ELSE 0 END) as error_count,
    SUM(CASE WHEN log_level = 'WARN' THEN count ELSE 0 END) as warn_count,
    SUM(CASE WHEN log_level = 'INFO' THEN count ELSE 0 END) as info_count,
    SUM(CASE WHEN log_level = 'DEBUG' THEN count ELSE 0 END) as debug_count,
    SUM(count) as total_count,
    CASE
        WHEN SUM(count) > 0
        THEN ROUND((SUM(CASE WHEN log_level = 'ERROR' THEN count ELSE 0 END) / SUM(count)) * 100, 2)
        ELSE 0
    END as error_rate
FROM
    log_daily_stats
GROUP BY
    log_date
ORDER BY
    log_date DESC;

-- 소스별 통계 요약 뷰 (테스트 DB)
CREATE OR REPLACE VIEW v_source_summary AS
SELECT
    source,
    SUM(count) as total_count,
    SUM(CASE WHEN log_level = 'ERROR' THEN count ELSE 0 END) as error_count,
    SUM(CASE WHEN log_level = 'WARN' THEN count ELSE 0 END) as warn_count,
    SUM(CASE WHEN log_level = 'INFO' THEN count ELSE 0 END) as info_count,
    SUM(CASE WHEN log_level = 'DEBUG' THEN count ELSE 0 END) as debug_count,
    CASE
        WHEN SUM(count) > 0
        THEN ROUND((SUM(CASE WHEN log_level = 'ERROR' THEN count ELSE 0 END) / SUM(count)) * 100, 2)
        ELSE 0
    END as error_rate
FROM
    log_source_stats
GROUP BY
    source
ORDER BY
    total_count DESC;