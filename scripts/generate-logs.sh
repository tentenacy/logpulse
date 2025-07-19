#!/bin/bash

# 고급 로그 생성 스크립트
# 다양한 레벨과 현실적인 메시지로 로그 데이터 생성

LOG_DIR="/app/logs"
# TEMPLATE_DIR="/usr/local/share/log-templates"  # 현재 사용하지 않음

# 디렉토리 생성
mkdir -p "$LOG_DIR"

# 로그 파일 경로
APPLICATION_LOG="$LOG_DIR/application.log"
ACCESS_LOG="$LOG_DIR/access.log"
ERROR_LOG="$LOG_DIR/error.log"
SECURITY_LOG="$LOG_DIR/security.log"
PERFORMANCE_LOG="$LOG_DIR/performance.log"

echo "🚀 LogPulse 샘플 데이터 생성 시작..."

# 서비스 이름 배열
SERVICES=("user-service" "payment-service" "notification-service" "auth-service" "api-gateway" "order-service" "inventory-service" "analytics-service" "email-service" "file-service")

# 로그 레벨별 가중치 (현실적인 분포)
# INFO: 60%, DEBUG: 25%, WARN: 10%, ERROR: 5%
declare -a LOG_LEVELS=("INFO" "INFO" "INFO" "INFO" "INFO" "INFO" "DEBUG" "DEBUG" "DEBUG" "WARN" "ERROR")

# 현재 시간에서 지난 7일간의 로그 생성
CURRENT_TIME=$(date +%s)
WEEK_AGO=$((CURRENT_TIME - 604800))  # 7일 = 604800초

# 타임스탬프 생성 함수
generate_timestamp() {
    local random_time=$((WEEK_AGO + RANDOM % 604800))
    date -d "@$random_time" '+%Y-%m-%d %H:%M:%S.%3N'
}

# 현실적인 애플리케이션 로그 생성
generate_application_log() {
    local timestamp=$(generate_timestamp)
    local level=${LOG_LEVELS[$((RANDOM % ${#LOG_LEVELS[@]}))]}
    local service=${SERVICES[$((RANDOM % ${#SERVICES[@]}))]}
    local thread="Thread-$((RANDOM % 20 + 1))"
    local class="com.logpulse.${service}.${level,,}.Service$((RANDOM % 5 + 1))"

    local messages=()
    case $level in
        "ERROR")
            messages=(
                "Database connection failed: Connection timeout after 30 seconds"
                "Failed to process payment for user ID: $((RANDOM % 10000)). Reason: Insufficient funds"
                "NullPointerException in user authentication: Invalid session token"
                "External API call failed: HTTP 500 from payment gateway"
                "Failed to send notification email: SMTP server unavailable"
                "Order processing failed: Product ID $((RANDOM % 1000)) not found"
                "Memory allocation error: OutOfMemoryError in image processing"
                "Security violation detected: Multiple failed login attempts from IP 192.168.$((RANDOM % 255)).$((RANDOM % 255))"
                "File upload failed: Maximum file size exceeded for user $((RANDOM % 10000))"
                "Cache invalidation error: Redis connection lost"
            )
            ;;
        "WARN")
            messages=(
                "High memory usage detected: 85% of heap space utilized"
                "Slow database query detected: Query took $((RANDOM % 5000 + 1000))ms to execute"
                "Rate limit approaching for API key: $((RANDOM % 100)) requests remaining"
                "Session cleanup: Removing $((RANDOM % 50)) expired sessions"
                "Deprecated API endpoint accessed: /api/v1/legacy/users"
                "Connection pool reaching capacity: $((RANDOM % 20 + 80))% utilization"
                "Large response size detected: $((RANDOM % 10 + 5))MB response from endpoint"
                "Retry attempt $((RANDOM % 3 + 1))/3 for failed external service call"
                "Disk space warning: $((RANDOM % 15 + 80))% disk usage on /var/log"
                "Queue backlog detected: $((RANDOM % 1000 + 100)) pending messages"
            )
            ;;
        "INFO")
            messages=(
                "User authentication successful for user ID: $((RANDOM % 10000))"
                "Order #ORD-$((RANDOM % 100000)) processed successfully"
                "Payment completed: Transaction ID TXN-$((RANDOM % 1000000))"
                "New user registration: Email verified for user@example$((RANDOM % 1000)).com"
                "Cache refreshed successfully: $((RANDOM % 500)) items updated"
                "Scheduled backup completed: $((RANDOM % 100))GB backed up"
                "Email notification sent to $((RANDOM % 10000)) recipients"
                "Product inventory updated: SKU-$((RANDOM % 10000)) stock level: $((RANDOM % 1000))"
                "API request processed in $((RANDOM % 200 + 50))ms"
                "Session created for user agent: Chrome/$((RANDOM % 20 + 90)).0.$((RANDOM % 10000)).$((RANDOM % 1000))"
            )
            ;;
        "DEBUG")
            messages=(
                "Database connection acquired from pool: Connection-$((RANDOM % 50))"
                "Cache hit for key: user:profile:$((RANDOM % 10000))"
                "Validating request parameters: {userId: $((RANDOM % 10000)), action: 'update'}"
                "Entering method: processUserRegistration() with parameters"
                "SQL query prepared: SELECT * FROM users WHERE id = ? LIMIT 1"
                "Redis operation: SET user:session:$((RANDOM % 100000)) TTL 3600"
                "JWT token validation successful for user $((RANDOM % 10000))"
                "HTTP request headers: Content-Type: application/json, Accept: */*"
                "Algorithm execution time: $((RANDOM % 50))ms for sorting $((RANDOM % 1000)) items"
                "Configuration loaded: database.maxConnections = $((RANDOM % 50 + 10))"
            )
            ;;
    esac

    local message=${messages[$((RANDOM % ${#messages[@]}))]}
    echo "$timestamp [$thread] $level $class - $message"
}

# HTTP 액세스 로그 생성
generate_access_log() {
    local timestamp=$(date -d "@$((WEEK_AGO + RANDOM % 604800))" '+%d/%b/%Y:%H:%M:%S %z')
    local ip="192.168.$((RANDOM % 255)).$((RANDOM % 255))"
    local methods=("GET" "POST" "PUT" "DELETE" "PATCH")
    local method=${methods[$((RANDOM % ${#methods[@]}))]}

    local endpoints=(
        "/api/v1/users"
        "/api/v1/orders"
        "/api/v1/payments"
        "/api/v1/products"
        "/api/v1/auth/login"
        "/api/v1/auth/logout"
        "/api/v1/notifications"
        "/api/v1/analytics"
        "/api/v1/reports"
        "/api/v1/admin/users"
    )
    local endpoint=${endpoints[$((RANDOM % ${#endpoints[@]}))]}/$((RANDOM % 1000))

    local status_codes=(200 200 200 200 201 201 400 401 403 404 500)
    local status=${status_codes[$((RANDOM % ${#status_codes[@]}))]}

    local response_size=$((RANDOM % 50000 + 500))
    local user_agents=(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"
        "curl/7.68.0"
        "PostmanRuntime/7.28.4"
    )
    local user_agent=${user_agents[$((RANDOM % ${#user_agents[@]}))]}

    echo "$ip - - [$timestamp] \"$method $endpoint HTTP/1.1\" $status $response_size \"$user_agent\""
}

# 보안 로그 생성
generate_security_log() {
    local timestamp=$(generate_timestamp)
    local events=(
        "AUTHENTICATION_SUCCESS: User login successful from IP 192.168.$((RANDOM % 255)).$((RANDOM % 255))"
        "AUTHENTICATION_FAILURE: Invalid credentials for user admin from IP 10.0.$((RANDOM % 255)).$((RANDOM % 255))"
        "AUTHORIZATION_DENIED: User $((RANDOM % 10000)) attempted to access restricted resource /admin/users"
        "SESSION_CREATED: New session established for user ID $((RANDOM % 10000))"
        "SESSION_EXPIRED: Session timeout for user ID $((RANDOM % 10000))"
        "SUSPICIOUS_ACTIVITY: Multiple failed login attempts detected from IP 172.16.$((RANDOM % 255)).$((RANDOM % 255))"
        "PASSWORD_CHANGED: User $((RANDOM % 10000)) successfully changed password"
        "ACCOUNT_LOCKED: User account $((RANDOM % 10000)) locked due to multiple failed attempts"
        "PRIVILEGE_ESCALATION: Admin privileges granted to user $((RANDOM % 100))"
        "API_KEY_ROTATION: API key rotated for service: ${SERVICES[$((RANDOM % ${#SERVICES[@]}))]}"
    )
    local event=${events[$((RANDOM % ${#events[@]}))]}
    echo "$timestamp SECURITY - $event"
}

# 성능 로그 생성
generate_performance_log() {
    local timestamp=$(generate_timestamp)
    local metrics=(
        "RESPONSE_TIME: /api/v1/users endpoint responded in $((RANDOM % 2000 + 100))ms"
        "MEMORY_USAGE: Heap memory usage: $((RANDOM % 80 + 20))% ($((RANDOM % 2000 + 500))MB)"
        "CPU_UTILIZATION: System CPU usage: $((RANDOM % 90 + 10))%"
        "DATABASE_QUERY: SELECT query executed in $((RANDOM % 5000 + 50))ms"
        "CACHE_HIT_RATE: Cache hit rate for last hour: $((RANDOM % 40 + 60))%"
        "THREAD_POOL: Active threads: $((RANDOM % 100 + 10))/$((RANDOM % 50 + 150))"
        "GARBAGE_COLLECTION: GC completed in $((RANDOM % 500 + 50))ms, freed $((RANDOM % 500 + 100))MB"
        "NETWORK_LATENCY: External API call latency: $((RANDOM % 1000 + 100))ms"
        "DISK_IO: Disk read/write: $((RANDOM % 1000 + 100))MB/s"
        "CONNECTION_POOL: Database connections: $((RANDOM % 20 + 5))/$((RANDOM % 10 + 50))"
    )
    local metric=${metrics[$((RANDOM % ${#metrics[@]}))]}
    echo "$timestamp PERFORMANCE - $metric"
}

# 로그 생성 함수 실행
echo "📝 애플리케이션 로그 생성 중..."
for i in $(seq 1 2000); do
    generate_application_log >> "$APPLICATION_LOG"
done

echo "🌐 액세스 로그 생성 중..."
for i in $(seq 1 1500); do
    generate_access_log >> "$ACCESS_LOG"
done

echo "🔒 보안 로그 생성 중..."
for i in $(seq 1 300); do
    generate_security_log >> "$SECURITY_LOG"
done

echo "📊 성능 로그 생성 중..."
for i in $(seq 1 500); do
    generate_performance_log >> "$PERFORMANCE_LOG"
done

# 에러 로그는 ERROR 레벨만 별도 파일로
echo "❌ 에러 로그 추출 중..."
grep "ERROR" "$APPLICATION_LOG" > "$ERROR_LOG"

# 파일 크기 및 라인 수 출력
echo ""
echo "✅ 로그 생성 완료!"
echo "📂 생성된 파일:"
for log_file in "$APPLICATION_LOG" "$ACCESS_LOG" "$ERROR_LOG" "$SECURITY_LOG" "$PERFORMANCE_LOG"; do
    if [ -f "$log_file" ]; then
        lines=$(wc -l < "$log_file")
        size=$(du -h "$log_file" | cut -f1)
        echo "   $(basename "$log_file"): $lines lines, $size"
    fi
done

echo ""
echo "🎯 로그 레벨 분포 (application.log):"
echo "   ERROR: $(grep -c "ERROR" "$APPLICATION_LOG") entries"
echo "   WARN:  $(grep -c "WARN" "$APPLICATION_LOG") entries"
echo "   INFO:  $(grep -c "INFO" "$APPLICATION_LOG") entries"
echo "   DEBUG: $(grep -c "DEBUG" "$APPLICATION_LOG") entries"

echo ""
echo "🏁 샘플 데이터 생성이 완료되었습니다!"