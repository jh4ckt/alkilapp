# AlkilApp Admin Panel - Dockerfile
FROM node:20-slim

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY . .

ENV PORT=8080
# SIN GOOGLE_APPLICATION_CREDENTIALS a proposito: en Cloud Run el panel usa las
# credenciales del runtime service account (ADC). La clave JSON esta en
# .dockerignore para que nunca viaje dentro de la imagen.
EXPOSE 8080

CMD ["node", "server.js"]