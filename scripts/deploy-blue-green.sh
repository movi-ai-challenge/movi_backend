#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
NGINX_CONF="${NGINX_CONF:-nginx/conf.d/movi.conf}"

if grep -q "server movi-blue:8080;" "$NGINX_CONF"; then
  ACTIVE="blue"
  INACTIVE="green"
else
  ACTIVE="green"
  INACTIVE="blue"
fi

echo "Active slot: $ACTIVE"
echo "Deploying slot: $INACTIVE"

docker compose -f "$COMPOSE_FILE" build "movi-$INACTIVE"
docker compose -f "$COMPOSE_FILE" up -d --no-deps "movi-$INACTIVE"

echo "Waiting for movi-$INACTIVE healthcheck..."
ATTEMPTS="${HEALTHCHECK_ATTEMPTS:-30}"
i=1
while [ "$i" -le "$ATTEMPTS" ]; do
  STATUS="$(docker inspect -f '{{.State.Health.Status}}' "movi-$INACTIVE" 2>/dev/null || true)"
  if [ "$STATUS" = "healthy" ]; then
    break
  fi
  sleep 3
  i=$((i + 1))
done

if [ "${STATUS:-}" != "healthy" ]; then
  echo "movi-$INACTIVE did not become healthy. Keeping $ACTIVE active." >&2
  exit 1
fi

tmp_conf="$(mktemp)"
sed "s/server movi-$ACTIVE:8080;/server movi-$INACTIVE:8080;/" "$NGINX_CONF" > "$tmp_conf"
mv "$tmp_conf" "$NGINX_CONF"

docker compose -f "$COMPOSE_FILE" exec nginx nginx -s reload

echo "Switched nginx traffic to movi-$INACTIVE."
echo "Previous slot movi-$ACTIVE is still running for fast rollback."
