FROM node:22.23.1-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY src ./src
COPY test ./test
RUN npm test && npm prune --omit=dev && npm cache clean --force

FROM node:22.23.1-bookworm-slim
ENV NODE_ENV=production
WORKDIR /app
RUN groupadd --system --gid 10001 hotel \
    && useradd --system --uid 10001 --gid hotel --home-dir /app hotel
COPY --from=build --chown=hotel:hotel /app/node_modules ./node_modules
COPY --chown=hotel:hotel package.json ./
COPY --chown=hotel:hotel src ./src
COPY --chown=hotel:hotel migrations ./migrations
RUN mkdir -p /data/avatars && chown -R hotel:hotel /data
USER hotel
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=3s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8080/health').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"
CMD ["node", "src/server.js"]
