#!/usr/bin/env bash
# Smoke test for a running URL shortener: a local container, the CI pipeline, or production.
#
# Usage:
#   ./smoke-test.sh                                        # http://localhost:8080
#   ./smoke-test.sh https://my-app.onrender.com --wait 240 # wait for a sleeping free host to wake up
#   ./smoke-test.sh http://localhost:8080 --rate-limit     # also prove the 429 behaviour
#
#   --wait SECONDS   keep polling the readiness probe for up to SECONDS before testing
#   --rate-limit     fire create requests until a 429 appears (burns the creation quota,
#                    so do not run it against production)
#
# Requires: bash and curl. Creates one link named smoke-<timestamp> per run.

BASE="http://localhost:8080"
WAIT=0
CHECK_RATE=0
PASS=0
FAIL=0

while [ $# -gt 0 ]; do
  case "$1" in
    --wait)        WAIT="${2:-0}"; shift 2 ;;
    --rate-limit)  CHECK_RATE=1; shift ;;
    http://*|https://*) BASE="${1%/}"; shift ;;
    *) echo "Unknown argument: $1"; exit 2 ;;
  esac
done

ok()  { printf 'PASS  %s\n' "$1"; PASS=$((PASS + 1)); }
bad() { printf 'FAIL  %s\n      %s\n' "$1" "$2"; FAIL=$((FAIL + 1)); }

check() { # label expected actual
  if [ "$2" = "$3" ]; then ok "$1 ($3)"; else bad "$1" "expected '$2', got '$3'"; fi
}

code_of() { curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$@"; }

echo "Target: $BASE"

# --- wait for the app (free hosts sleep when idle and take a while to wake) ----
if [ "$WAIT" -gt 0 ]; then
  echo "Waiting up to ${WAIT}s for the readiness probe..."
  deadline=$(( $(date +%s) + WAIT ))
  until [ "$(code_of "$BASE/actuator/health/readiness")" = "200" ]; do
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "Not ready after ${WAIT}s. Check the application logs."
      exit 1
    fi
    sleep 3
  done
fi
echo "------------------------------------------------------------------"

# --- probes --------------------------------------------------------------------
check "readiness probe" 200 "$(code_of "$BASE/actuator/health/readiness")"
check "liveness probe"  200 "$(code_of "$BASE/actuator/health/liveness")"

# --- create a link -------------------------------------------------------------
A="smoke-$(date +%s)-$RANDOM"
resp="$(curl -s -w $'\n%{http_code}' --max-time 15 -X POST "$BASE/api/v1/urls" \
        -H 'Content-Type: application/json' \
        -d "{\"longUrl\":\"https://example.com/smoke\",\"customAlias\":\"$A\"}")"
status="${resp##*$'\n'}"
body="${resp%$'\n'*}"

check "create link" 201 "$status"
if [[ "$body" == *"\"shortUrl\":\"$BASE/$A\""* ]]; then
  ok "shortUrl uses the public base URL (APP_BASE_URL is correct)"
else
  bad "shortUrl uses the public base URL (APP_BASE_URL is correct)" "body: $body"
fi

# --- redirect (do not follow it, just inspect it) -------------------------------
r="$(curl -s -o /dev/null --max-time 15 -w '%{http_code}|%{redirect_url}' "$BASE/$A")"
if [ "${r%%|*}" = "302" ] && [[ "${r#*|}" == *example.com* ]]; then
  ok "redirect (302 -> ${r#*|})"
else
  bad "redirect" "got: $r"
fi

# --- error behaviour -------------------------------------------------------------
resp="$(curl -s -w $'\n%{http_code}' --max-time 15 "$BASE/nope-$RANDOM")"
status="${resp##*$'\n'}"; body="${resp%$'\n'*}"
check "unknown code -> 404" 404 "$status"
if [[ "$body" == *'"message"'* ]]; then ok "404 uses the JSON error format"; else bad "404 uses the JSON error format" "body: $body"; fi

resp="$(curl -s -w $'\n%{http_code}' --max-time 15 -X POST "$BASE/api/v1/urls" \
        -H 'Content-Type: application/json' -d '{"longUrl":"ftp://example.com"}')"
status="${resp##*$'\n'}"; body="${resp%$'\n'*}"
check "invalid URL -> 400" 400 "$status"
if [[ "$body" == *'"message"'* ]]; then ok "400 uses the JSON error format"; else bad "400 uses the JSON error format" "body: $body"; fi

# --- stats ------------------------------------------------------------------------
resp="$(curl -s -w $'\n%{http_code}' --max-time 15 "$BASE/api/v1/urls/$A/stats")"
status="${resp##*$'\n'}"; body="${resp%$'\n'*}"
check "stats endpoint" 200 "$status"
if [[ "$body" == *"\"shortCode\":\"$A\""* ]]; then ok "stats describe the new link"; else bad "stats describe the new link" "body: $body"; fi

# --- API docs (informational: they can be switched off in production) ----------------
docs="$(code_of "$BASE/v3/api-docs")"
if [ "$docs" = "200" ]; then ok "OpenAPI document is served"; else echo "INFO  OpenAPI document returned $docs (disabled, or a different path)"; fi

# --- optional: rate limiting ---------------------------------------------------------
if [ "$CHECK_RATE" -eq 1 ]; then
  echo "== rate limit =="
  got=""
  n=0
  for n in $(seq 1 80); do
    hdrs="$(curl -s -D - -o /dev/null --max-time 15 -X POST "$BASE/api/v1/urls" \
            -H 'Content-Type: application/json' -d '{"longUrl":"https://example.com/rl"}')"
    if echo "$hdrs" | head -1 | grep -q ' 429'; then got="$hdrs"; break; fi
  done
  if [ -n "$got" ] && echo "$got" | grep -qi '^retry-after:'; then
    ok "creation is rate limited: 429 with Retry-After after $n requests"
  else
    bad "creation is rate limited" "no 429 with a Retry-After header within 80 requests"
  fi
fi

echo "------------------------------------------------------------------"
echo "Passed: $PASS   Failed: $FAIL"
[ "$FAIL" -eq 0 ]
