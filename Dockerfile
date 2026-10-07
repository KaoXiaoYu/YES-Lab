FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html vite.config.js ./
COPY config ./config
COPY public ./public
COPY src ./src
ARG VITE_UI_PRESET=general
RUN VITE_UI_PRESET="$VITE_UI_PRESET" npm run build

FROM caddy:2.11-alpine
COPY LICENSE /usr/share/licenses/yeslab/LICENSE
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /workspace/dist /srv
EXPOSE 80 443
