export class ApiError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}
function publicResponse(value) {
  if (Array.isArray(value)) return value.map(publicResponse);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(Object.entries(value)
    .filter(([key, item]) => !/password|token|secret|cookie|owner_hash|code_hash/i.test(key) && !(key === "code" && /^[0-9]{6}$/.test(String(item))))
    .map(([key, item]) => [key, publicResponse(item)]));
}
export function createApiClient({fetchImpl = globalThis.fetch, onResponse = () => {}, root = new URL("../api/v1/", import.meta.url).href} = {}) {
  const base = new URL(root);
  function endpoint(path) {
    const result = new URL(path, base);
    if (result.origin !== base.origin || !result.pathname.startsWith(base.pathname)) throw new Error("허용되지 않은 API 주소입니다.");
    return result;
  }
  async function read(response) {
    if (response.status === 204) return null;
    if (response.headers.get("content-type")?.includes("json")) {
      try { return await response.json(); } catch { throw new ApiError(response.status, "INVALID_RESPONSE", "서버 응답을 읽을 수 없습니다."); }
    }
    return null;
  }
  async function fetchResponse(url, options) {
    try { return await fetchImpl(url, {...options, credentials: "same-origin", cache: "no-store", redirect: "error"}); }
    catch { throw new ApiError(0, "NETWORK_ERROR", "서버에 연결하지 못했습니다. 서버 실행 상태를 확인하세요."); }
  }
  return {
    async request(path, {method = "GET", body} = {}) {
      const url = endpoint(path);
      method = method.toUpperCase();
      const headers = new Headers();
      if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        const csrfResponse = await fetchResponse(endpoint("auth/csrf"), {method: "GET"});
        const csrf = await read(csrfResponse);
        if (!csrfResponse.ok || typeof csrf?.token !== "string" || typeof csrf?.headerName !== "string")
          throw new ApiError(csrfResponse.status, "CSRF_UNAVAILABLE", "보안 토큰을 가져오지 못했습니다. 다시 시도하세요.");
        headers.set(csrf.headerName, csrf.token);
      }
      const multipart = body instanceof FormData;
      if (body !== undefined && !multipart) headers.set("Content-Type", "application/json");
      const response = await fetchResponse(url, {
        method, headers, body: multipart || body === undefined ? body : JSON.stringify(body)
      });
      const result = await read(response);
      onResponse({path: url.pathname, method, status: response.status, body: publicResponse(result)});
      if (!response.ok) throw new ApiError(response.status, result?.code || "HTTP_ERROR", result?.message || "요청을 처리하지 못했습니다.");
      return result;
    }
  };
}
