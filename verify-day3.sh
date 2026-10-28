#!/usr/bin/env bash
# Day 3 verification: runs the 13-row test matrix against a running app.
# Usage:
#   ./verify-day3.sh            # rows 1-13
#   ./verify-day3.sh --expiry   # also runs the expiry test (takes ~7 seconds)
#   BASE=http://localhost:8080 ./verify-day3.sh
#
# Requires: bash, curl, and the app + docker compose services running.

BASE="${BASE:-http://localhost:8080}"
ALIAS="my-link-$(date +%s)"   # unique per run, so the script can be re-run safely
PASS=0
FAIL=0

# run <label> <METHOD> <path> <expected_status> <body_must_contain|""> [json_body]
run() {
  local label="$1" method="$2" path="$3" expected="$4" contains="$5" data="${6:-}"
  local out status body

  if [ -n "$data" ]; then
    out=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE$path" \
          -H 'Content-Type: application/json' -d "$data")
  else
    out=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE$path")
  fi
  status="${out##*$'\n'}"
  body="${out%$'\n'*}"

  if [ "$status" = "$expected" ] && { [ -z "$contains" ] || [[ "$body" == *"$contains"* ]]; }; then
    printf 'PASS  %-46s %s\n' "$label" "$status"
    PASS=$((PASS + 1))
  else
    printf 'FAIL  %-46s got %s, expected %s' "$label" "$status" "$expected"
    [ -n "$contains" ] && printf ' containing: %s' "$contains"
    printf '\n      body: %s\n' "$body"
    FAIL=$((FAIL + 1))
  fi
}

echo "Target: $BASE   (alias for this run: $ALIAS)"
echo "------------------------------------------------------------------"

run "1  create valid URL"              POST /api/v1/urls 201 '"shortCode"' \
    '{"longUrl":"https://example.com"}'

run "2  ftp scheme rejected"           POST /api/v1/urls 400 'https' \
    '{"longUrl":"ftp://example.com"}'

run "3  IP address rejected"           POST /api/v1/urls 400 'IP addresses' \
    '{"longUrl":"http://127.0.0.1:8080/admin"}'

run "4  javascript: scheme rejected"   POST /api/v1/urls 400 '' \
    '{"longUrl":"javascript:alert(1)"}'

run "5  create with custom alias"      POST /api/v1/urls 201 "\"shortCode\":\"$ALIAS\"" \
    "{\"longUrl\":\"https://example.com\",\"customAlias\":\"$ALIAS\"}"

run "6  duplicate alias -> conflict"   POST /api/v1/urls 409 'already in use' \
    "{\"longUrl\":\"https://example.com\",\"customAlias\":\"$ALIAS\"}"

run "7  alias 'abc' rejected"          POST /api/v1/urls 400 "'-' or '_'" \
    '{"longUrl":"https://example.com","customAlias":"abc"}'

run "8  alias 'swagger-ui' reserved"   POST /api/v1/urls 400 'reserved' \
    '{"longUrl":"https://example.com","customAlias":"swagger-ui"}'

# Row 9: redirect. Don't follow it; check status and Location.
r9=$(curl -s -o /dev/null -w '%{http_code}|%{redirect_url}' "$BASE/$ALIAS")
if [ "${r9%%|*}" = "302" ] && [[ "${r9#*|}" == *example.com* ]]; then
  printf 'PASS  %-46s %s -> %s\n' "9  GET /<alias> redirects" "${r9%%|*}" "${r9#*|}"
  PASS=$((PASS + 1))
else
  printf 'FAIL  %-46s got %s\n' "9  GET /<alias> redirects" "$r9"
  FAIL=$((FAIL + 1))
fi

run "10 unknown code -> 404 JSON"      GET /zzzz 404 'No URL found'

run "11 malformed JSON -> 400"         POST /api/v1/urls 400 '"message"' \
    '{bad'

run "12 wrong method -> 405 JSON"      DELETE /api/v1/urls 405 '"message"'

run "13 expiresAt in the past -> 400"  POST /api/v1/urls 400 'expiresAt' \
    '{"longUrl":"https://example.com","expiresAt":"2020-01-01T00:00:00Z"}'

# Optional: expiry flips from 302 to 410
if [ "$1" = "--expiry" ]; then
  echo "------------------------------------------------------------------"
  echo "Expiry test (waits ~7 seconds)..."
  EXP_ALIAS="short-lived-$(date +%s)"
  EXP=$(date -u -d '+5 seconds' +%Y-%m-%dT%H:%M:%SZ)
  run "E1 create link expiring in 5s"  POST /api/v1/urls 201 '"shortCode"' \
      "{\"longUrl\":\"https://example.com\",\"customAlias\":\"$EXP_ALIAS\",\"expiresAt\":\"$EXP\"}"
  c1=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/$EXP_ALIAS")
  if [ "$c1" = "302" ]; then
    printf 'PASS  %-46s %s\n' "E2 redirect works before expiry" "$c1"; PASS=$((PASS + 1))
  else
    printf 'FAIL  %-46s got %s, expected 302\n' "E2 redirect works before expiry" "$c1"; FAIL=$((FAIL + 1))
  fi
  sleep 7
  run "E3 expired link -> 410 Gone"    GET "/$EXP_ALIAS" 410 'expired'
fi

echo "------------------------------------------------------------------"
echo "Passed: $PASS   Failed: $FAIL"
[ "$FAIL" -eq 0 ]
