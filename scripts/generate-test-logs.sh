#!/bin/bash

# 로그 디렉토리 설정
LOG_DIR="$HOME/logpulse/logs"

# 디렉토리가 없으면 생성
mkdir -p "$LOG_DIR"

# 로그 파일 경로
APPLICATION_LOG="$LOG_DIR/application.log"
ACCESS_LOG="$LOG_DIR/access.log"
ERROR_LOG="$LOG_DIR/error.log"

# 로그 레벨 배열
LOG_LEVELS=("INFO" "DEBUG" "WARN" "ERROR")

# 임의의 로그 메시지 생성 함수
generate_app_log() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S.%3N')
    local level=${LOG_LEVELS[$((RANDOM % 4))]}
    local thread="Thread-$((RANDOM % 10))"
    local class="com.tenacy.app.Service$((RANDOM % 5))"
    local message="Processing request #$((RANDOM % 1000)) with parameters [id=$((RANDOM % 100)), type=TYPE_$((RANDOM % 5))]"

    echo "$timestamp [$thread] $level $class - $message"
}

generate_access_log() {
    local timestamp=$(date '+%d/%b/%Y:%H:%M:%S %z')
    local ip="192.168.0.$((RANDOM % 255))"
    local method=("GET" "POST" "PUT" "DELETE")
    local paths=("/api/v1/users" "/api/v1/products" "/api/v1/orders" "/api/v1/payments")
    local status_codes=(200 201 400 404 500)

    echo "$ip - - [$timestamp] \"${method[$((RANDOM % 4))]} ${paths[$((RANDOM % 4))]}/$((RANDOM % 100)) HTTP/1.1\" ${status_codes[$((RANDOM % 5))]} $((RANDOM % 1000))"
}

generate_error_log() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S.%3N')
    local errors=(
        "NullPointerException: Cannot invoke method on null object"
        "IllegalArgumentException: Invalid parameter value"
        "DatabaseException: Connection timeout"
        "OutOfMemoryError: Java heap space"
        "SecurityException: Access denied"
    )

    echo "$timestamp ERROR - ${errors[$((RANDOM % 5))]}"
}

# 지정된 횟수만큼 로그 파일에 로그 추가
for i in $(seq 1 100); do
    generate_app_log >> "$APPLICATION_LOG"
    generate_access_log >> "$ACCESS_LOG"

    # 10% 확률로 에러 로그 생성
    if [ $((RANDOM % 10)) -eq 0 ]; then
        generate_error_log >> "$ERROR_LOG"
    fi

    # 약간의 지연
    sleep 0.1
done

echo "생성 완료: 각 로그 파일에 로그 항목이 추가되었습니다."
echo "로그 파일 위치: $LOG_DIR"