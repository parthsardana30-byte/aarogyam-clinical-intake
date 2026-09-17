FROM node:22-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY server.js ./
COPY dist ./dist

ENV HOST=0.0.0.0
ENV PORT=80
ENV DATA_DIR=/app/data

EXPOSE 80

CMD ["npm", "start"]
