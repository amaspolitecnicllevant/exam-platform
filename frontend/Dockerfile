FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
# apk upgrade: porta els últims pegats de seguretat dels paquets del sistema (la imatge base pot anar endarrerida)
RUN apk upgrade --no-cache
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx-limits.conf /etc/nginx/conf.d/00-limits.conf
COPY nginx-comu.conf /etc/nginx/snippets/sedex.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
