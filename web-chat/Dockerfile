# 预览站镜像：构建双入口静态产物并用 nginx 托管。
# 构建上下文为 web-chat/ 目录（compose 已配置），部署后访问 /preview.html
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
