interface Env {
  API_ORIGIN?: string;
}

export const onRequest: PagesFunction<Env> = async ({ request, env }) => {
  const origin = (env.API_ORIGIN || "").replace(/\/$/, "");
  if (!origin) {
    return new Response(JSON.stringify({ error: "Cloudflare Pages chưa cấu hình API_ORIGIN." }), {
      status: 503,
      headers: { "Content-Type": "application/json; charset=utf-8" }
    });
  }
  const incoming = new URL(request.url);
  const target = `${origin}${incoming.pathname}${incoming.search}`;
  const headers = new Headers(request.headers);
  headers.delete("host");
  return fetch(new Request(target, { method: request.method, headers, body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body }));
};
