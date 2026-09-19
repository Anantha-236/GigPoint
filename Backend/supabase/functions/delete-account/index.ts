// DhwaniMitra: authenticated account deletion
//
// This function must run server-side because deleting an Auth user requires
// privileged credentials. Never place the service-role/secret key in the APK.

import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return Response.json(
      { error: "Method not allowed" },
      { status: 405, headers: corsHeaders },
    );
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey =
    Deno.env.get("SUPABASE_ANON_KEY") ??
    Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    return Response.json(
      { error: "Server configuration is incomplete" },
      { status: 500, headers: corsHeaders },
    );
  }

  const authorization = req.headers.get("Authorization");

  if (!authorization?.startsWith("Bearer ")) {
    return Response.json(
      { error: "Authentication required" },
      { status: 401, headers: corsHeaders },
    );
  }

  // User-scoped client: validates the caller.
  const userClient = createClient(supabaseUrl, anonKey, {
    global: {
      headers: {
        Authorization: authorization,
      },
    },
    auth: {
      persistSession: false,
      autoRefreshToken: false,
    },
  });

  const {
    data: { user },
    error: userError,
  } = await userClient.auth.getUser();

  if (userError || !user) {
    return Response.json(
      { error: "Invalid or expired session" },
      { status: 401, headers: corsHeaders },
    );
  }

  // Admin client stays inside the Edge Function.
  const adminClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
    },
  });

  // profiles -> auth.users and shops -> auth.users are ON DELETE CASCADE.
  // Child shop data also cascades through shops.
  const { error: deleteError } =
    await adminClient.auth.admin.deleteUser(user.id);

  if (deleteError) {
    return Response.json(
      { error: deleteError.message },
      { status: 500, headers: corsHeaders },
    );
  }

  return Response.json(
    {
      ok: true,
      message: "DhwaniMitra account deleted",
    },
    { status: 200, headers: corsHeaders },
  );
});
