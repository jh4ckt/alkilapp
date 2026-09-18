# Panel de administracion de AlkilApp
# Build desde backend/:  docker build -f admin/Dockerfile -t alkilapp-admin .
FROM node:20-slim

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY . .

ENV PORT=8080
EXPOSE 8080

# Las credenciales se toman de la service account del servicio (ADC);
# no se hornea ningun JSON de credenciales en la imagen.
CMD ["node", "admin/server.js"]
