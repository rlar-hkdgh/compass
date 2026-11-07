import test from "node:test";
import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";

const source = await readFile(new URL("../src/main/resources/demo/api-client.js", import.meta.url));
const {createApiClient, ApiError} = await import("data:text/javascript;base64," + source.toString("base64"));
const root = "http://demo.example.test/api/v1/";
const json = (value, status = 200) => new Response(JSON.stringify(value), {status, headers: {"Content-Type": "application/json"}});

test("rotates CSRF per mutation and never exposes passwords, codes or tokens in the response log", async () => {
  const calls = [], events = [];
  let csrf = 0;
  const api = createApiClient({root, onResponse: event => events.push(event), fetchImpl: async (url, init) => {
    calls.push({path: url.pathname, init});
    if (url.pathname.endsWith("/csrf")) return json({headerName: "X-Test-CSRF", token: "fake-csrf-" + (++csrf)});
    if (url.pathname.endsWith("/login")) return json({id: 1, schoolEmail: "demo@sju.ac.kr", passwordHash: "fixture-hash"});
    if (url.pathname.endsWith("/confirm")) return json({emailVerified: true, code: "001234"});
    return new Response(null, {status: 204});
  }});
  await api.request("auth/signup/email/confirm", {method: "POST", body: {schoolEmail: "demo@sju.ac.kr", code: "001234"}});
  await api.request("auth/login", {method: "POST", body: {schoolEmail: "demo@sju.ac.kr", password: "Synthetic-test-123!"}});
  assert.equal(await api.request("auth/logout", {method: "POST"}), null);
  assert.deepEqual(calls.map(call => call.init.method), ["GET","POST","GET","POST","GET","POST"]);
  assert.deepEqual(calls.filter(call => call.init.method === "POST").map(call => call.init.headers.get("X-Test-CSRF")),
    ["fake-csrf-1","fake-csrf-2","fake-csrf-3"]);
  assert.equal(JSON.parse(calls[1].init.body).code, "001234");
  for (const call of calls) {
    assert.equal(call.init.credentials, "same-origin");
    assert.equal(call.init.cache, "no-store");
    assert.equal(call.init.redirect, "error");
  }
  assert.doesNotMatch(JSON.stringify(events), /Synthetic-test|fixture-hash|001234|fake-csrf|passwordHash/);
});
test("read requests require no CSRF lookup and preserve the API error status and code", async () => {
  let calls = 0;
  const api = createApiClient({root, fetchImpl: async (url, init) => {
    calls++;
    assert.equal(url.pathname, "/api/v1/me/career-goal");
    assert.equal(init.method, "GET");
    assert.equal(init.headers.has("X-CSRF-TOKEN"), false);
    return json({code: "ITEM_NOT_FOUND", message: "목표 없음"}, 404);
  }});
  await assert.rejects(api.request("me/career-goal"), error => error instanceof ApiError && error.status === 404 && error.code === "ITEM_NOT_FOUND");
  assert.equal(calls, 1);
});
test("a failed CSRF lookup prevents the write and rejected mutations are not silently retried", async () => {
  const failed = [];
  const first = createApiClient({root, fetchImpl: async (_, init) => { failed.push(init.method); return json({}, 403); }});
  await assert.rejects(first.request("auth/signup/email/send", {method: "POST", body: {}}), {code: "CSRF_UNAVAILABLE"});
  assert.deepEqual(failed, ["GET"]);
  const requests = [];
  const second = createApiClient({root, fetchImpl: async (_, init) => {
    requests.push(init.method);
    return init.method === "GET" ? json({headerName: "X-CSRF-TOKEN", token: "test-token"}) : json({code: "CSRF_INVALID"}, 403);
  }});
  await assert.rejects(second.request("auth/signup/email/send", {method: "POST", body: {}}), {code: "CSRF_INVALID"});
  assert.deepEqual(requests, ["GET", "POST"]);
});
test("API calls cannot leave the configured origin or API prefix", async () => {
  const api = createApiClient({root, fetchImpl: () => assert.fail("unexpected request")});
  await assert.rejects(api.request("https://untrusted.example.test/auth/login", {method: "POST"}), /허용되지 않은 API/);
  await assert.rejects(api.request("../demo"), /허용되지 않은 API/);
});
