FROM node:22.23.1-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY import.mjs ./
RUN node --check import.mjs
USER node
CMD ["node", "import.mjs"]
