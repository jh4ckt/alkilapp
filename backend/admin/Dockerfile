# AlkilApp Admin Panel - Dockerfile
FROM node:20-slim

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY . .

ENV PORT=8080
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/alkilapp-seed-sa.json
EXPOSE 8080

CMD ["node", "server.js"]