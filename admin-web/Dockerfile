FROM node:22.23.1-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=http://localhost:8080/api/v1
ENV VITE_API_URL=$VITE_API_URL
RUN npm test
RUN npm run build

FROM node:22.23.1-bookworm-slim
ENV NODE_ENV=production
WORKDIR /app
RUN groupadd --system --gid 10001 hotel \
    && useradd --system --uid 10001 --gid hotel --home-dir /app hotel
COPY --from=build --chown=hotel:hotel /app/dist ./dist
COPY --chown=hotel:hotel server.mjs ./
USER hotel
EXPOSE 3000
CMD ["node", "server.mjs"]
