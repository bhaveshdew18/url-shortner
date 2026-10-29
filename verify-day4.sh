#!/usr/bin/env bash
# Day 4 verification: Redis cache-aside, negative caching, async click analytics.
#
# Usage:
#   ./verify-day4.sh                 # core checks
#   ./verify-day4.sh --redis-down    # also stops Redis briefly to prove redirects survive an outage
#   BASE=http://localhost:8080 REDIS_CONTAINER=shortener-redis ./verify-day4.sh
#
# Requires: bash, curl, docker (used to peek inside Redis), and the app + docker compose running.
# Assumes the default cache settings: link TTL 1h, negative TTL 30s.

BASE="${BASE:-http://localhost:8080}"
REDIS_CONTAINER="${REDIS_CONTAINER:-shortener-redis}"
RUN="$(date +%s)-$RANDOM"      # unique per run, so the script can be re-run safely
PASS=0
FAIL=0

ok()  { printf 'PASS  %s\n' "$1"; PASS=$((PASS + 1)); }
bad() { printf 'FAIL  %s\n      %s\n' "$1" "$2"; FAIL=$((FAIL + 1)); }

check() { # label expected actual
  if [ "$2" = "$3" ]; then ok "$1 ($3)"; else bad "$1" "expected '$2', got '$3'"; fi
}

between() { # label value min max
  if [[ "$2" =~ ^-?[0-9]+$ ]] && [ "$2" -ge "$3" ] && [ "$2" -le "$4" ]; then
    ok "$1 (${2}s)"
  else
    bad "$1" "expected a value between $3 and $4, got '$2'"
  fi
}

redis()    { docker exec "$REDIS_CONTAINER" redis-cli "$@" 2>/dev/null | tr -d '\r'; }
code_of()  { curl -s -o /dev/null -w '%{http_code}' "$@"; }
create()   { curl -s -X POST "$BASE/api/v1/urls" -H 'Content-Type: application/json' -d "$1"; }
json_num() { grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }

# --- preconditions -----------------------------------------------------------
if [ "$(redis PING)" != "PONG" ]; then
  echo "Cannot reach Redis through: docker exec $REDIS_CONTAINER redis-cli"
  echo "Is 'docker compose up -d' running? Is the container name right?"
  exit 2
fi
if [ "$(code_of "$BASE/actuator/health")" != "200" ]; then
  echo "App is not healthy at $BASE (is it running?)"
  exit 2
fi

echo "Target: $BASE   run id: $RUN"
echo "------------------------------------------------------------------"

# --- 1. cache-aside on the redirect path -------------------------------------
echo "== 1. Cache-aside on redirect =="
A="cache-$RUN"
create "{\"longUrl\":\"https://example.com/page\",\"customAlias\":\"$A\"}" > /dev/null

check "key absent before first redirect"        0   "$(redis EXISTS "url:$A")"
check "first redirect (cache miss -> database)" 302 "$(code_of "$BASE/$A")"
check "key present after first redirect"        1   "$(redis EXISTS "url:$A")"
between "TTL is set and at most 1 hour" "$(redis TTL "url:$A")" 1 3600

val="$(redis GET "url:$A")"
if [[ "$val" == *"|https://example.com/page" ]]; then
  ok "cached value contains the target URL"
else
  bad "cached value contains the target URL" "value was: $val"
fi
check "second redirect (cache hit)"             302 "$(code_of "$BASE/$A")"

# --- 2. TTL never outlives the link's own expiry ------------------------------
echo "== 2. TTL capped by link expiry =="
B="expiry-$RUN"
EXP=$(date -u -d '+30 seconds' +%Y-%m-%dT%H:%M:%SZ)
create "{\"longUrl\":\"https://example.com\",\"customAlias\":\"$B\",\"expiresAt\":\"$EXP\"}" > /dev/null
check "redirect before expiry"                  302 "$(code_of "$BASE/$B")"
between "TTL capped at link expiry (<= 30s)" "$(redis TTL "url:$B")" 1 30

# --- 3. negative caching and eviction on create ------------------------------
echo "== 3. Negative cache =="
N="miss-$RUN"
check "unknown code -> 404"                     404 "$(code_of "$BASE/$N")"
check "negative marker is cached"               "-" "$(redis GET "url:$N")"
between "negative entry has a short TTL (<= 30s)" "$(redis TTL "url:$N")" 1 30
check "create the link that was negative-cached" 201 \
  "$(code_of -X POST "$BASE/api/v1/urls" -H 'Content-Type: application/json' \
      -d "{\"longUrl\":\"https://example.com\",\"customAlias\":\"$N\"}")"
check "negative marker evicted right after create" 0 "$(redis EXISTS "url:$N")"
check "new link works immediately (stale 404 evicted)" 302 "$(code_of "$BASE/$N")"

# --- 4. malformed codes never reach Redis or the database --------------------
echo "== 4. Malformed codes =="
check "malformed code -> 404"                   404 "$(code_of "$BASE/bad.code")"
check "malformed code is never cached"          0   "$(redis EXISTS "url:bad.code")"

# --- 5. async click analytics -------------------------------------------------
echo "== 5. Click analytics =="
C="clicks-$RUN"
create "{\"longUrl\":\"https://example.com\",\"customAlias\":\"$C\"}" > /dev/null

for i in 1 2 3; do
  curl -s -o /dev/null -H 'Referer: https://www.google.com/search?q=a' -H 'User-Agent: verify-day4' "$BASE/$C"
done
for i in 1 2; do
  curl -s -o /dev/null -H 'Referer: https://news.ycombinator.com/item?id=1' -H 'User-Agent: verify-day4' "$BASE/$C"
done
curl -s -o /dev/null "$BASE/nope-$RUN-x"      # a 404 must not count as a click

# clicks are recorded asynchronously, so poll for up to 10 seconds
body=""
for i in $(seq 1 20); do
  body="$(curl -s "$BASE/api/v1/urls/$C/stats")"
  [ "$(echo "$body" | json_num clickCount)" = "5" ] && break
  sleep 0.5
done

check "clickCount reaches 5 (eventually consistent)" 5 "$(echo "$body" | json_num clickCount)"
check "clicksLast24Hours is 5"                       5 "$(echo "$body" | json_num clicksLast24Hours)"
if [[ "$body" == *'"referrer":"www.google.com","clicks":3'* ]]; then
  ok "top referrers: www.google.com has 3 clicks"
else
  bad "top referrers: www.google.com has 3 clicks" "body: $body"
fi
if [[ "$body" == *'"referrer":"news.ycombinator.com","clicks":2'* ]]; then
  ok "top referrers: news.ycombinator.com has 2 clicks"
else
  bad "top referrers: news.ycombinator.com has 2 clicks" "body: $body"
fi

# --- 6. stats edge cases -------------------------------------------------------
echo "== 6. Stats edge cases =="
check "stats for unknown code -> 404"           404 "$(code_of "$BASE/api/v1/urls/nope-$RUN/stats")"
check "stats for malformed code -> 404"         404 "$(code_of "$BASE/api/v1/urls/bad.code/stats")"

# --- 7. optional: Redis outage -------------------------------------------------
if [ "$1" = "--redis-down" ]; then
  echo "== 7. Redis outage =="
  docker stop "$REDIS_CONTAINER" > /dev/null
  start=$(date +%s%N)
  check "redirect still works with Redis down"  302 "$(code_of "$BASE/$A")"
  ms=$(( ($(date +%s%N) - start) / 1000000 ))
  echo "      (took ${ms} ms; a few hundred ms is expected because of Redis timeouts)"

  docker start "$REDIS_CONTAINER" > /dev/null
  sleep 3
  code_of "$BASE/$A" > /dev/null
  check "redirect works after Redis returns"    302 "$(code_of "$BASE/$A")"
  check "cache is repopulated after recovery"   1   "$(redis EXISTS "url:$A")"
fi

echo "------------------------------------------------------------------"
echo "Passed: $PASS   Failed: $FAIL"
[ "$FAIL" -eq 0 ]