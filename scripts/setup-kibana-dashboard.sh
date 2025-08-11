#!/bin/bash

# Elasticsearch 접속 정보
ES_HOST="localhost:19200"
KIBANA_HOST="localhost:15601"

# Kibana가 준비될 때까지 대기
echo "Kibana가 준비될 때까지 대기 중..."
until $(curl --output /dev/null --silent --head --fail $KIBANA_HOST); do
    printf '.'
    sleep 5
done
echo "Kibana가 준비되었습니다!"

# 인덱스 패턴 생성
echo "인덱스 패턴 생성 중..."
curl -X POST "$KIBANA_HOST/api/saved_objects/index-pattern/logs" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{"attributes":{"title":"logs*","timeFieldName":"timestamp"}}'

echo "기본 대시보드 설정이 완료되었습니다."
echo "Kibana에 접속하여 추가 설정을 진행하세요: $KIBANA_HOST"