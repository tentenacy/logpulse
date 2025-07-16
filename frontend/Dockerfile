# 빌드 스테이지
FROM node:18-alpine as build
WORKDIR /app

# 의존성 설치
COPY package*.json ./
RUN npm install

# 소스 코드 복사 및 빌드
COPY . .
RUN npm run build

# 배포 스테이지
FROM nginx:alpine
# Nginx 설정 파일 복사 (Docker Compose의 볼륨 마운트로 대체 가능)
# COPY nginx/nginx.conf /etc/nginx/conf.d/default.conf
# 빌드 결과물 복사
COPY --from=build /app/build /usr/share/nginx/html
# 80 포트 노출
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]