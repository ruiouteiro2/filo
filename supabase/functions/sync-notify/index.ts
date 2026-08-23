// Called by the members_sync_notify trigger whenever something the widgets show changes
// (music, mood, note, photos). Sends one silent data message to the partner's phone so its
// widgets refresh in seconds instead of waiting for the half-hour worker.
//
// Deployed with:  supabase functions deploy sync-notify --no-verify-jwt
// Secrets needed: FIREBASE_SERVICE_ACCOUNT_B64 (same as ping-notify)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

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
    const { member_id } = await request.json().catch(() => ({ member_id: null }));
    if (!member_id) return new Response("missing member_id", { status: 400 });

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: changed, error } = await supabase
      .from("members")
      .select("id, couple_id")
      .eq("id", member_id)
      .single();
    if (error || !changed?.couple_id) {
      return new Response(JSON.stringify({ skipped: "no_such_member" }), { status: 200 });
    }

    const { data: members } = await supabase
      .from("members")
      .select("id, fcm_token")
      .eq("couple_id", changed.couple_id);
    const partner = members?.find((m) => m.id !== member_id);
    if (!partner?.fcm_token) {
      return new Response(JSON.stringify({ skipped: "no_token" }), { status: 200 });
    }

    const encoded = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_B64");
    const raw = encoded
      ? new TextDecoder().decode(Uint8Array.from(atob(encoded), (c) => c.charCodeAt(0)))
      : Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!raw) {
      return new Response(JSON.stringify({ skipped: "not_configured" }), { status: 200 });
    }

    let serviceAccount: Record<string, string>;
    try {
      serviceAccount = JSON.parse(raw);
    } catch (parseError) {
      console.error("Firebase credentials are not valid JSON", parseError);
      return new Response(JSON.stringify({ error: "bad_credentials" }), { status: 500 });
    }
    const token = await accessToken(serviceAccount);

    // Data-only on purpose: nothing appears on screen, the app just wakes and syncs.
    const message = {
      message: {
        token: partner.fcm_token,
        data: { type: "sync" },
        android: { priority: "HIGH" },
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
    console.error("sync-notify failed", error);
    return new Response(JSON.stringify({ error: String(error) }), { status: 500 });
  }
});
