// Called by the pings_notify trigger. Looks up who to tell, in which language, and sends
// one FCM notification. The body is translated here, server side, because the recipient's
// language is a property of the recipient and not of the sender's phone.
//
// Deployed with:  supabase functions deploy ping-notify --no-verify-jwt
// Secrets needed: FIREBASE_SERVICE_ACCOUNT (the whole service account JSON, one line)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

const RATE_LIMIT_SECONDS = 60;

/** The only strings this function can send. Keep in step with strings.xml. */
const BODIES: Record<string, (name: string) => string> = {
  en: (name) => `${name} is thinking of you`,
  it: (name) => `${name} sta pensando a te`,
};

function bodyFor(locale: string | null, name: string): string {
  const make = BODIES[locale ?? "en"] ?? BODIES.en;
  return make(name);
}

// ---------------------------------------------------------------- FCM auth

function pemToBinary(pem: string): Uint8Array {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const raw = atob(body);
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

function base64Url(input: Uint8Array | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * Mints a Google OAuth access token from the service account. Signed with Web Crypto so the
 * function has no JWT dependency to keep up to date.
 */
async function accessToken(serviceAccount: Record<string, string>): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${base64Url(JSON.stringify(header))}.${base64Url(JSON.stringify(claims))}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBinary(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)),
  );
  const assertion = `${unsigned}.${base64Url(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) {
    throw new Error(`token exchange failed: ${response.status} ${await response.text()}`);
  }
  return (await response.json()).access_token;
}

// ------------------------------------------------------------------ handler

Deno.serve(async (request) => {
  try {
    const { ping_id } = await request.json().catch(() => ({ ping_id: null }));
    if (!ping_id) return new Response("missing ping_id", { status: 400 });

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: ping, error: pingError } = await supabase
      .from("pings")
      .select("id, couple_id, from_member, created_at")
      .eq("id", ping_id)
      .single();
    if (pingError || !ping) {
      console.error("ping lookup failed", pingError);
      return new Response(JSON.stringify({ error: "no such ping" }), { status: 404 });
    }

    // The real rate limit, enforced here rather than only in the UI: at most one ping per
    // sender per minute, counted against what is already in the table.
    const since = new Date(Date.now() - RATE_LIMIT_SECONDS * 1000).toISOString();
    const { count } = await supabase
      .from("pings")
      .select("id", { count: "exact", head: true })
      .eq("from_member", ping.from_member)
      .gte("created_at", since)
      .neq("id", ping.id);
    if ((count ?? 0) > 0) {
      return new Response(JSON.stringify({ skipped: "rate_limited" }), { status: 200 });
    }

    const { data: members } = await supabase
      .from("members")
      .select("id, display_name, locale, fcm_token")
      .eq("couple_id", ping.couple_id);

    const sender = members?.find((m) => m.id === ping.from_member);
    const recipient = members?.find((m) => m.id !== ping.from_member);
    if (!recipient?.fcm_token) {
      return new Response(JSON.stringify({ skipped: "no_token" }), { status: 200 });
    }

    // Base64 first. The raw JSON form contains ~28 real newlines inside private_key, which
    // do not survive being interpolated through a shell into `supabase secrets set` - the
    // symptom is a 500 here with "SyntaxError ... at position 1" and a heart that never
    // arrives. Base64 has no characters a shell can mangle.
    const encoded = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_B64");
    const raw = encoded
      ? new TextDecoder().decode(Uint8Array.from(atob(encoded), (c) => c.charCodeAt(0)))
      : Deno.env.get("FIREBASE_SERVICE_ACCOUNT");

    if (!raw) {
      // Push is not configured yet. The ping is still recorded; it just does not notify.
      console.log("no Firebase credentials set, skipping send");
      return new Response(JSON.stringify({ skipped: "not_configured" }), { status: 200 });
    }

    let serviceAccount: Record<string, string>;
    try {
      serviceAccount = JSON.parse(raw);
    } catch (error) {
      console.error("Firebase credentials are not valid JSON", error);
      return new Response(JSON.stringify({ error: "bad_credentials" }), { status: 500 });
    }
    const token = await accessToken(serviceAccount);

    const message = {
      message: {
        token: recipient.fcm_token,
        notification: {
          title: "Filo",
          body: bodyFor(recipient.locale, sender?.display_name ?? "Filo"),
        },
        android: { priority: "HIGH", notification: { channel_id: "filo_ping" } },
      },
    };

    const send = await fetch(
      `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(message),
      },
    );

    if (!send.ok) {
      const text = await send.text();
      console.error("fcm send failed", send.status, text);
      return new Response(JSON.stringify({ error: text }), { status: 502 });
    }
    return new Response(JSON.stringify({ sent: true }), { status: 200 });
  } catch (error) {
    console.error("ping-notify failed", error);
    return new Response(JSON.stringify({ error: String(error) }), { status: 500 });
  }
});
