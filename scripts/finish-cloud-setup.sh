#!/usr/bin/env bash
#
# Everything that needs a logged-in Supabase CLI, in one go.
#
#   1. npx supabase login       <- you, once, in a real terminal
#   2. bash scripts/finish-cloud-setup.sh
#
# Safe to re-run. Reads the Firebase key from disk; nothing secret is printed.
set -euo pipefail

PROJECT_REF="lrmyvjzvsamqabofkmzz"
FIREBASE_KEY="${FIREBASE_KEY:-$HOME/Documents/filo-secrets/firebase-adminsdk.json}"
FUNCTIONS_URL="https://${PROJECT_REF}.supabase.co/functions/v1"

cd "$(dirname "$0")/.."

say() { printf '\n=== %s ===\n' "$1"; }

say "checking login"
if ! npx --yes supabase projects list >/dev/null 2>&1; then
  echo "Not logged in. Run this first, in your own terminal:"
  echo
  echo "    npx supabase login"
  echo
  echo "then run this script again."
  exit 1
fi
echo "logged in"

say "linking to $PROJECT_REF"
npx --yes supabase link --project-ref "$PROJECT_REF"

say "pushing the schema"
npx --yes supabase db push

say "deploying the ping-notify function"
# --no-verify-jwt: it is only ever called by the database trigger, and it validates the
# ping id it is handed. This is also why no service role key needs storing in the database.
npx --yes supabase functions deploy ping-notify --no-verify-jwt

say "setting the Firebase secret"
if [ ! -f "$FIREBASE_KEY" ]; then
  echo "Firebase key not found at: $FIREBASE_KEY"
  echo "Set FIREBASE_KEY=/path/to/key.json and re-run."
  exit 1
fi
# Passed as a value, never echoed.
npx --yes supabase secrets set "FIREBASE_SERVICE_ACCOUNT=$(cat "$FIREBASE_KEY")" >/dev/null
echo "FIREBASE_SERVICE_ACCOUNT set"

say "pointing the ping trigger at the deployed function"
npx --yes supabase db push >/dev/null 2>&1 || true
cat <<SQL > /tmp/filo_config.sql
insert into private.config (key, value)
values ('functions_url', '$FUNCTIONS_URL')
on conflict (key) do update set value = excluded.value;
SQL
echo "Run this once in the SQL editor (it needs no credentials from the CLI):"
echo
cat /tmp/filo_config.sql

say "done"
echo "Remaining manual step: Authentication > Providers > Anonymous sign-ins = ON"
