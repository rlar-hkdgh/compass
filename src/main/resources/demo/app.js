import {createApiClient, ApiError} from "./api-client.js";

const $ = id => document.getElementById(id);
const state = {profile: null, catalogs: null, majorsLoaded: false, busy: false, availableEmail: "", sentEmail: "", verifiedEmail: "", verifiedUntil: 0, resendAt: 0};
const errors = {
  INVALID_CREDENTIALS: "학교 이메일 또는 비밀번호를 확인하세요.",
  UNAUTHENTICATED: "먼저 로그인하세요.",
  SESSION_EXPIRED: "세션이 만료됐습니다. 다시 로그인하세요.",
  CSRF_INVALID: "보안 토큰이 만료됐습니다. 버튼을 다시 눌러 요청하세요.",
  EMAIL_VERIFICATION_REQUIRED: "같은 브라우저에서 학교 이메일 인증을 완료한 뒤 가입하세요.",
  EMAIL_DELIVERY_FAILED: "메일을 발송하지 못했습니다. 서버의 SMTP 설정을 확인하세요."
};
function notice(message, error = false) {
  $("notice").textContent = message;
  $("notice").dataset.error = String(error);
}
const api = createApiClient({onResponse: result => {
  const body = result.path.endsWith("/courses/import/preview") && result.body?.wholeImport
    ? {gpa: result.body.gpa, completedSemesters: result.body.completedSemesters, ...result.body.wholeImport, warnings: result.body.warnings}
    : result.body;
  $("response").textContent = JSON.stringify(body, null, 2) ?? "(응답 본문 없음)";
  const item = document.createElement("li");
  item.textContent = result.method + " " + result.path + " → " + result.status;
  $("request-log").prepend(item);
  while ($("request-log").children.length > 12) $("request-log").lastElementChild.remove();
}});
async function run(action) {
  if (state.busy) return;
  state.busy = true;
  notice("처리 중...");
  const buttons = [...document.querySelectorAll("button")].map(button => [button, button.disabled]);
  for (const [button] of buttons) button.disabled = true;
  try { await action(); }
  catch (error) {
    const cause = error.cause instanceof ApiError ? error.cause : error;
    if (state.profile && cause instanceof ApiError && ["UNAUTHENTICATED", "SESSION_EXPIRED"].includes(cause.code)) setProfile(null);
    notice((error.code ? error.code + ": " : "") + (errors[error.code] || error.message || "요청 처리에 실패했습니다."), true);
  } finally {
    state.busy = false;
    for (const [button, disabled] of buttons) if (button.isConnected) button.disabled = disabled;
    updateAuth();
  }
}
function click(id, action) { $(id).addEventListener("click", () => run(action)); }
function submit(id, action) {
  $(id).addEventListener("submit", event => { event.preventDefault(); run(action); });
}
function email() { return $("signup-email").value.trim().toLowerCase(); }
function validEmail(input) {
  if (!input.reportValidity()) throw new Error("학교 이메일을 입력하세요.");
  const value = input.value.trim().toLowerCase();
  if (!/^[^@\s]+@(sju\.ac\.kr|sejong\.co\.kr)$/.test(value)) throw new Error("sju.ac.kr 또는 sejong.co.kr 학교 이메일을 입력하세요.");
  input.value = value;
  return value;
}
function resetVerification() {
  Object.assign(state, {availableEmail: "", sentEmail: "", verifiedEmail: "", verifiedUntil: 0, resendAt: 0});
  $("email-code").value = "";
  $("email-status").textContent = "이메일 중복 확인부터 진행하세요.";
  updateAuth();
}
function updateAuth() {
  if (state.busy) return;
  const same = email();
  const cooldown = Math.max(0, Math.ceil((state.resendAt - Date.now()) / 1000));
  const verified = state.verifiedEmail === same && state.verifiedUntil > Date.now();
  $("email-send").disabled = !same || state.availableEmail !== same || cooldown > 0;
  $("email-send").textContent = cooldown ? "재발송 대기 (" + cooldown + "초)" : state.sentEmail === same ? "인증번호 재발송" : "인증번호 발송";
  $("email-confirm").disabled = !same || state.sentEmail !== same || verified;
  $("signup-submit").disabled = !verified;
  $("logout").disabled = !state.profile;
  if (state.verifiedEmail && !verified && state.verifiedUntil <= Date.now())
    $("email-status").textContent = "가입 인증 유효시간이 지났습니다. 번호를 다시 발송하고 인증하세요.";
}
$("signup-email").addEventListener("input", resetVerification);
setInterval(updateAuth, 1000);

click("email-check", async () => {
  const value = validEmail($("signup-email"));
  const result = await api.request("auth/signup/email/availability", {method: "POST", body: {schoolEmail: value}});
  state.availableEmail = result.available ? value : "";
  $("email-status").textContent = result.available ? "가입 가능한 이메일입니다. 인증번호를 발송하세요." : "이미 가입된 이메일입니다. 아래에서 로그인하세요.";
  $("login-email").value = value;
  notice(result.available ? "이메일 중복 확인 완료" : "이미 가입된 계정입니다.");
});
click("email-send", async () => {
  const value = validEmail($("signup-email"));
  const result = await api.request("auth/signup/email/send", {method: "POST", body: {schoolEmail: value}});
  state.sentEmail = value; state.verifiedEmail = ""; state.verifiedUntil = 0;
  state.resendAt = Date.parse(result.resendAvailableAt) || Date.now() + 60000;
  $("email-code").value = "";
  $("email-status").textContent = "학교 이메일의 받은편지함·스팸함을 확인하세요. 번호는 10분간 유효합니다.";
  notice("인증번호를 발송했습니다.");
});
click("email-confirm", async () => {
  const value = validEmail($("signup-email"));
  const code = $("email-code").value.trim();
  if (!/^[0-9]{6}$/.test(code)) throw new Error("메일로 받은 6자리 숫자를 입력하세요.");
  const result = await api.request("auth/signup/email/confirm", {method: "POST", body: {schoolEmail: value, code}});
  if (!result.emailVerified) throw new Error("이메일 인증이 완료되지 않았습니다.");
  state.verifiedEmail = value; state.verifiedUntil = Date.parse(result.verifiedUntil) || 0;
  $("email-code").value = "";
  $("email-status").textContent = "인증 완료. 10분 안에 아래 정보를 입력하고 가입하세요.";
  notice("학교 이메일 인증 완료");
});
submit("signup-form", async () => {
  const schoolEmail = validEmail($("signup-email"));
  if (state.verifiedEmail !== schoolEmail || state.verifiedUntil <= Date.now()) throw new Error("이메일 인증을 먼저 완료하세요.");
  const password = $("signup-password").value;
  if (new TextEncoder().encode(password).length > 72) throw new Error("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
  await api.request("auth/signup", {method: "POST", body: {schoolEmail, password, name: $("signup-name").value.trim(), isTransfer: $("signup-transfer").checked}});
  $("signup-password").value = ""; $("login-email").value = schoolEmail;
  resetVerification();
  notice("회원가입 완료. 아래 로그인 폼에서 같은 학교 이메일로 로그인하세요.");
});

function fillProfile(profile) {
  $("profile-name").value = profile.name;
  $("profile-transfer").checked = profile.isTransfer;
  showProfileTranscript(profile);
}
function showProfileTranscript(profile) {
  $("profile-semester").textContent = profile.semester == null ? "저장된 이수학기 없음" : "정규 이수학기 " + profile.semester + "개";
  $("profile-gpa").textContent = profile.gpa == null ? "저장된 평점 없음" : "저장된 평점 " + profile.gpa + " / " + profile.gpaScale;
}
function setProfile(profile) {
  if (!profile || state.profile?.id !== profile.id) {
    $("profile-form").reset(); $("goal-form").reset(); $("major-rows").replaceChildren();
    state.majorsLoaded = false;
    pendingCourseStatus = "";
    $("majors-status").textContent = "";
    showRegionSelection();
    for (const form of document.querySelectorAll("[data-onboarding] form")) form.reset();
    renderOnboarding(null);
    clearTranscript();
  }
  state.profile = profile;
  $("student-fields").disabled = !profile;
  for (const fieldset of document.querySelectorAll("[data-onboarding]")) fieldset.disabled = !profile;
  $("session-status").textContent = profile ? "로그인됨: " + profile.schoolEmail + " / " + profile.name : "로그인하지 않은 상태입니다.";
  if (profile) fillProfile(profile);
  else {
    for (const input of document.querySelectorAll('input[type="password"]')) input.value = "";
    $("response").textContent = "(로그인한 계정의 정보만 조회할 수 있습니다.)";
    resetVerification();
  }
}
async function readProfile() { const profile = await api.request("me"); setProfile(profile); return profile; }
submit("login-form", async () => {
  const schoolEmail = validEmail($("login-email"));
  const profile = await api.request("auth/login", {method: "POST", body: {schoolEmail, password: $("login-password").value}});
  $("login-password").value = "";
  setProfile(profile);
  await loadSavedMajors();
  await readOnboarding();
  notice("로그인 완료. 학생 기본 정보와 전공을 확인하세요.");
});
click("session-read", async () => {
  await readProfile();
  if (!state.majorsLoaded) await loadSavedMajors();
  await readOnboarding();
  notice("현재 로그인 상태를 조회했습니다.");
});
click("logout", async () => {
  await api.request("auth/logout", {method: "POST"});
  setProfile(null); $("login-form").reset(); $("signup-form").reset();
  notice("로그아웃했습니다.");
});
click("profile-read", async () => {
  await readProfile();
  if (!state.majorsLoaded) await loadSavedMajors();
  notice("저장된 학생 정보를 불러왔습니다.");
});
submit("profile-form", async () => {
  if (!state.majorsLoaded) throw new Error("전공 조회를 완료한 뒤 다시 시도하세요.");
  const majors = majorValues();
  if (!majors.length) throw new Error("전공을 하나 이상 선택하세요.");
  const file = $("transcript-file").files[0];
  const preview = transcript;
  if (file && !preview) throw new Error("성적표 미리보기에서 저장 가능한 과목을 확인하세요.");

  const body = {name: $("profile-name").value.trim(), isTransfer: $("profile-transfer").checked};
  const profile = await saveBasicPart("기본 정보", [], () => api.request("me", {method: "PATCH", body}));
  setProfile(profile);
  await saveBasicPart("전공", ["기본 정보"], () => api.request("me/majors", {method: "PUT", body: {majors}}));

  const steps = ["BASIC_INFO", "MAJORS"];
  let message = "학생 기본 정보와 전공을 저장했습니다.";
  if (preview) {
    const result = await saveBasicPart("성적표", ["기본 정보", "전공"], async () => {
      if (file !== $("transcript-file").files[0]) throw new Error("선택한 파일이 바뀌었습니다. 미리보기를 다시 실행하세요.");
      try {
        return await saveTranscript(preview, file);
      } catch (error) {
        if (["PREVIEW_EXPIRED", "PREVIEW_CONSUMED", "PREVIEW_NOT_FOUND"].includes(error.code)) clearTranscript();
        throw error;
      }
    });
    if (!result.hasCourses) throw new Error(message + " 성적표에 저장 가능한 과목이 없습니다. 충돌을 확인하세요.");
    pendingCourseStatus = result.message;
  }
  if (pendingCourseStatus) {
    steps.push("COURSES");
    message += " " + pendingCourseStatus;
  }
  await finishSavedSteps(steps, message, "다음을 다시 누르세요.");
  pendingCourseStatus = "";
  // run() temporarily disables buttons, so focus an input on the next step.
  $("goal-job").focus();
});
async function saveBasicPart(label, saved, action) {
  try {
    return await action();
  } catch (error) {
    const previous = saved.length ? " 저장 완료 · " + saved.join(", ") + "." : "";
    throw new Error(label + " 저장에 실패했습니다." + previous + " " + (errors[error.code] || error.message), {cause: error});
  }
}

function options(select, values, placeholder = "") {
  select.replaceChildren();
  if (placeholder) select.add(new Option(placeholder, ""));
  for (const item of values) select.add(new Option(item.name, item.code));
}
async function readCatalogs() {
  const [majors, completions, regions, stacks, jobs, certifications] = await Promise.all([
    api.request("student-codes/majors"), api.request("student-codes/major-completion-types"), api.request("student-codes/regions/grouped"),
    api.request("student-codes/tech-stacks"), api.request("student-codes/job-categories"), api.request("student-codes/certifications")
  ]);
  const oldRows = majorValues();
  state.catalogs = {majors, completions};
  renderRegions(regions);
  refreshOptionList("project-stacks", stacks);
  refreshOptionList("internship-job", jobs, "선택하세요");
  refreshOptionList("goal-job", jobs, "선택하세요");
  refreshCheckboxes("certification-codes", "certificationCodes", certifications);
  $("major-rows").replaceChildren();
  for (const value of oldRows) addMajor(value);
}
function majorValues() {
  return [...$("major-rows").children].map(row => ({
    majorCode: row.querySelector('[data-field="major"]').value,
    completionTypeCode: row.querySelector('[data-field="completion"]').value
  }));
}
function addMajor(value = {}) {
  if (!state.catalogs) throw new Error("전공·이수 구분 코드를 먼저 조회하세요.");
  const row = document.createElement("div"); row.className = "major-row";
  for (const [field, labelText, values, selected] of [
    ["major", "전공", state.catalogs.majors, value.majorCode],
    ["completion", "이수 구분", state.catalogs.completions, value.completionTypeCode || "PRIMARY"]
  ]) {
    const label = document.createElement("label"); label.append(document.createTextNode(labelText));
    const select = document.createElement("select"); select.dataset.field = field; select.required = true;
    options(select, values, "선택하세요"); select.value = selected || "";
    label.append(select); row.append(label);
  }
  const remove = document.createElement("button"); remove.type = "button"; remove.textContent = "이 전공 선택 제거";
  remove.addEventListener("click", () => { if (!state.busy) row.remove(); }); row.append(remove);
  $("major-rows").append(row);
}
click("catalog-read", async () => { await readCatalogs(); notice("입력 항목 코드를 불러왔습니다."); });
click("major-add", async () => {
  if (!state.majorsLoaded) throw new Error("저장된 전공을 먼저 불러오세요.");
  addMajor();
  notice("전공과 이수 구분을 선택한 뒤 다음을 누르세요.");
});
async function loadSavedMajors() {
  $("majors-status").textContent = "저장된 전공을 불러오는 중입니다.";
  try {
    if (!state.catalogs) await readCatalogs();
    const result = await api.request("me/majors");
    $("major-rows").replaceChildren();
    for (const value of result.majors) addMajor(value);
    state.majorsLoaded = true;
    $("majors-status").textContent = result.majors.length ? "저장된 전공을 불러왔습니다." : "저장된 전공이 없습니다. 전공을 추가하세요.";
  } catch (error) {
    $("majors-status").textContent = "전공 조회에 실패했습니다. 저장된 전공 다시 조회를 눌러 주세요.";
    throw new Error($("majors-status").textContent, {cause: error});
  }
}
click("majors-read", async () => { await loadSavedMajors(); notice($("majors-status").textContent); });
function fillGoal(goal) {
  for (const [id, key] of [["goal-job", "jobCategoryCode"], ["target-salary", "targetSalary"],
    ["target-company", "targetCompany"], ["target-date", "targetDate"]])
    $(id).value = goal[key] ?? "";
  for (const checkbox of $("goal-form").elements.namedItem("companySizes"))
    checkbox.checked = goal.companySizes.includes(checkbox.value);
  setRegionSelection(goal.regionCodes || []);
}
click("goal-read", async () => {
  if (!state.catalogs) await readCatalogs();
  try {
    const goal = await api.request("me/career-goal"); fillGoal(goal);
    notice("저장된 취업 목표를 불러왔습니다.");
  } catch (error) {
    if (error.status !== 404 || error.code !== "ITEM_NOT_FOUND") throw error;
    $("goal-form").reset();
    const preferences = await api.request("me/preferences");
    setRegionSelection(preferences.regionCodes);
    notice("아직 저장된 취업 목표가 없습니다. 목표를 입력하고 저장하세요.");
  }
});
submit("goal-form", async () => {
  const salary = Number($("target-salary").value);
  if (!Number.isSafeInteger(salary) || salary < 0) throw new Error("목표 연봉은 0 이상의 정수로 입력하세요.");
  const form = new FormData($("goal-form"));
  const companySizes = form.getAll("companySizes");
  if (!companySizes.length) throw new Error("기업 규모를 하나 이상 선택하세요.");
  const regionCodes = form.getAll("regionCodes");
  if (!regionCodes.length) throw new Error("희망 지역을 하나 이상 선택하세요.");
  const body = {
    jobCategoryCode: $("goal-job").value, companySizes, targetSalary: salary,
    regionCodes,
    targetCompany: $("target-company").value.trim(), targetDate: $("target-date").value
  };
  const goal = await api.request("me/career-goal", {method: "PUT", body}); fillGoal(goal);
  await finishSavedStep("PREFERENCES", "취업 목표를 저장했습니다. 아래에서 수강 과목과 선택 이력을 입력할 수 있습니다.", "취업 목표 전체 저장을 다시 누르세요.");
});

const optionalSteps = [
  {path: "courses", step: "COURSES", label: "수강 과목"},
  {path: "projects", step: "PROJECTS", label: "프로젝트"},
  {path: "internships", step: "INTERNSHIPS", label: "인턴"},
  {path: "awards", step: "AWARDS", label: "수상"},
  {path: "certifications", step: "CERTIFICATIONS", label: "자격증"}
];
const stepLabels = Object.fromEntries(optionalSteps.map(({step, label}) => [step, label]));
Object.assign(stepLabels, {BASIC_INFO: "학생 기본 정보", MAJORS: "전공", PREFERENCES: "취업 목표"});
const statusLabels = {NOT_STARTED: "미완료", COMPLETED: "완료", SKIPPED: "건너뜀"};

function renderOnboarding(progress) {
  const completed = progress?.steps.filter(({status}) => status === "COMPLETED").map(({step}) => step) || [];
  $("goal-fields").disabled = !completed.includes("BASIC_INFO") || !completed.includes("MAJORS");
  $("onboarding-steps").replaceChildren();
  if (!progress) {
    $("onboarding-summary").textContent = "로그인 후 진행 상태를 확인할 수 있습니다.";
    return;
  }
  $("onboarding-summary").textContent = progress.finished
    ? "온보딩 완료"
    : "완료 " + progress.completedSteps + "개 · 건너뜀 " + progress.skippedSteps + "개 / 전체 " + progress.totalSteps + "개";
  for (const {step, status} of progress.steps) {
    const item = document.createElement("li");
    item.textContent = stepLabels[step] + " · " + statusLabels[status];
    $("onboarding-steps").append(item);
  }
}
async function readOnboarding() {
  const progress = await api.request("me/onboarding");
  renderOnboarding(progress);
  return progress;
}
async function markStep(step, status = "COMPLETED") {
  await markSteps([{step, status}]);
}
async function markSteps(steps) {
  const progress = await api.request("me/onboarding", {method: "PATCH", body: {steps}});
  renderOnboarding(progress);
}
async function finishSavedStep(step, message, retry) {
  await finishSavedSteps([step], message, retry);
}
async function finishSavedSteps(steps, message, retry) {
  try {
    await markSteps(steps.map(step => ({step, status: "COMPLETED"})));
  } catch (error) {
    if (["UNAUTHENTICATED", "SESSION_EXPIRED"].includes(error.code)) setProfile(null);
    throw new Error(message + " 진행 상태 저장에 실패했습니다. " + retry + " " + error.message, {cause: error});
  }
  notice(message);
}
function selectedValues(select) {
  return [...select.selectedOptions].map(option => option.value);
}
function refreshOptionList(id, values, placeholder) {
  const selected = selectedValues($(id));
  options($(id), values, placeholder);
  for (const option of $(id).options) {
    if (selected.includes(option.value)) option.selected = true;
  }
}
function checkValues(id, selected) {
  for (const input of $(id).querySelectorAll("input")) input.checked = selected.includes(input.value);
}
function checkboxOption({code, name: text}, name) {
  const label = document.createElement("label");
  label.className = "check";
  const input = document.createElement("input");
  input.type = "checkbox";
  input.name = name;
  input.value = code;
  label.append(input, " " + text);
  return label;
}
function refreshCheckboxes(id, name, values) {
  const selected = [...$(id).querySelectorAll("input:checked")].map(input => input.value);
  $(id).replaceChildren(...values.map(value => checkboxOption(value, name)));
  checkValues(id, selected);
}
function showRegionSelection() {
  const selected = [...$("region-options").querySelectorAll("input:checked")];
  const names = selected.map(input => input.closest(".region-panel").getAttribute("aria-label") + " · " + input.parentElement.textContent.trim());
  $("region-selection").textContent = names.length ? names.join(", ") : "선택된 지역 없음";
}
function setRegionSelection(codes) {
  checkValues("regions", codes);
  showRegionSelection();
}
function renderRegions(groups) {
  const selected = [...$("region-options").querySelectorAll("input:checked")].map(input => input.value);
  const active = $("region-groups").querySelector('[aria-pressed="true"]')?.textContent;
  const buttons = [], panels = [];
  const show = index => {
    for (const [i, panel] of panels.entries()) {
      panel.hidden = i !== index;
      buttons[i].setAttribute("aria-pressed", String(i === index));
    }
  };
  for (const [index, {name, regions}] of groups.entries()) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = name;
    button.addEventListener("click", () => show(index));
    const panel = document.createElement("div");
    panel.id = "region-panel-" + index;
    panel.className = "region-panel";
    panel.setAttribute("role", "group");
    panel.setAttribute("aria-label", name);
    panel.append(...regions.map(region => checkboxOption(region, "regionCodes")));
    button.setAttribute("aria-controls", panel.id);
    buttons.push(button);
    panels.push(panel);
  }
  $("region-groups").replaceChildren(...buttons);
  $("region-options").replaceChildren(...panels);
  show(Math.max(0, groups.findIndex(group => group.name === active)));
  setRegionSelection(selected);
}
$("region-options").addEventListener("change", showRegionSelection);
$("region-clear").addEventListener("click", () => setRegionSelection([]));
function readOptional(entry) {
  return api.request("me/" + entry.path);
}
function savedCount(entry, result) {
  return entry.step === "CERTIFICATIONS" ? result.certificationCodes.length : result.length;
}
function recordPayload(entry, form) {
  const data = new FormData(form);
  const body = Object.fromEntries([...data].map(([key, value]) => [key, value.trim()]));
  if (entry.step === "COURSES") {
    body.academicYear = Number(body.academicYear);
    body.evaluationMethod = ["P", "NP"].includes(body.grade) ? "P/NP" : "GRADE";
  }
  if (entry.step === "PROJECTS") body.techStackCodes = data.getAll("techStackCodes");
  if (entry.step === "INTERNSHIPS" && body.startedOn > body.endedOn)
    throw new Error("종료일은 시작일 이후로 입력하세요.");
  return body;
}
for (const entry of optionalSteps) {
  click(entry.path + "-read", async () => {
    const result = await readOptional(entry);
    notice(entry.label + " 저장 기록 " + savedCount(entry, result) + "개를 조회했습니다. 아래 실행 결과에서 확인하세요.");
  });
  click(entry.path + "-continue", async () => {
    const result = await readOptional(entry);
    const exists = savedCount(entry, result) > 0;
    await markStep(entry.step, exists ? "COMPLETED" : "SKIPPED");
    notice(exists ? entry.label + " 단계를 저장된 기록으로 완료했습니다." : entry.label + " 단계를 건너뛰었습니다.");
  });
  submit(entry.path + "-form", async () => {
    const form = $(entry.path + "-form");
    if (entry.step === "CERTIFICATIONS") {
      const selected = new FormData(form).getAll("certificationCodes");
      if (!selected.length) throw new Error("추가할 자격증을 하나 이상 선택하세요.");
      const saved = await readOptional(entry);
      const certificationCodes = [...new Set([...saved.certificationCodes, ...selected])];
      await api.request("me/certifications", {method: "PUT", body: {certificationCodes}});
    } else {
      await api.request("me/" + entry.path, {method: "POST", body: recordPayload(entry, form)});
    }
    form.reset();
    await finishSavedStep(entry.step, entry.label + " 기록을 저장했습니다.", "‘저장 기록으로 완료 / 없으면 건너뛰기’를 눌러 상태 저장만 다시 시도하세요.");
  });
}
click("onboarding-finish", async () => {
  const progress = await readOnboarding();
  if (progress.finished) {
    notice("온보딩을 완료했습니다.");
    return;
  }
  const remaining = progress.steps.filter(({status}) => status === "NOT_STARTED").map(({step}) => stepLabels[step]);
  notice("남은 단계 · " + remaining.join(", ") + ". 입력을 저장하거나 선택 항목을 건너뛰세요.", true);
});


let transcript = null;
let pendingCourseStatus = "";
function clearTranscript() {
  transcript = null;
  $("transcript-rows").replaceChildren();
  $("transcript-summary").textContent = "";
  showTranscriptCourses([]);
}
function showTranscriptCourses(rows) {
  const items = rows.filter(row => row.course).map(({sheetName, sourceRow, course}) => {
    const tr = document.createElement("tr");
    for (const value of [sheetName + " " + sourceRow + "행", [course.academicYear, course.semester].filter(Boolean).join(" "),
      course.courseNumber, course.courseName, course.grade]) {
      tr.insertCell().textContent = value ?? "—";
    }
    return tr;
  });
  $("transcript-course-list").replaceChildren(...items);
  $("transcript-courses").hidden = !items.length;
}
function showTranscriptIssues(rowIssues) {
  const items = rowIssues.map(({sheetName, sourceRow, issues}) => {
    const item = document.createElement("li");
    item.textContent = sheetName + " " + sourceRow + "행 · " + issues.map(issue => issue.message).join(" / ");
    return item;
  });
  $("transcript-rows").replaceChildren(...items);
}
$("transcript-file").addEventListener("change", clearTranscript);
click("transcript-preview", async () => {
  clearTranscript();
  const file = $("transcript-file").files[0];
  if (!file || !/\.xlsx$/i.test(file.name) || !file.size || file.size > 5 * 1024 * 1024)
    throw new Error("비어 있지 않은 5MiB 이하 .xlsx 파일을 선택하세요.");
  const form = new FormData();
  form.append("file", file);
  const preview = await api.request("me/courses/import/preview", {method: "POST", body: form});
  if ($("transcript-file").files[0] !== file) return;
  const {ready, skipped, rowIssues} = preview.wholeImport;
  const count = "과목 " + (ready + skipped + rowIssues.length) + "행 · 신규 저장 " + ready
    + "개 · 동일 행 건너뛰기 " + skipped + "개 · 오류·충돌 " + rowIssues.length + "행.";
  const gpa = preview.gpa == null ? "파일 기준 평점을 계산할 수 없습니다." : "파일 기준 평점 " + preview.gpa + " / 4.5.";
  const semesters = preview.completedSemesters == null ? "정규 이수학기를 계산할 수 없습니다."
    : "정규 이수학기 " + preview.completedSemesters + "개.";
  $("transcript-summary").textContent = [count, gpa, semesters, ...preview.warnings.map(issue => issue.message)].join(" ");
  showTranscriptCourses(preview.rows);
  showTranscriptIssues(rowIssues);
  if (ready + skipped === 0) {
    notice("저장 가능한 과목이 없습니다. 오류나 충돌을 확인하세요.", true);
    return;
  }
  transcript = {previewId: preview.previewId};
  notice("과목 내역, 평점, 정규 이수학기를 확인한 뒤 다음을 누르세요.");
});
async function saveTranscript(preview, file) {
  const result = await api.request("me/courses/import/confirm-all", {
    method: "POST", body: {previewId: preview.previewId}
  });
  const hasCourses = result.inserted + result.skipped > 0;
  if (file === $("transcript-file").files[0]) {
    transcript = null;
    if (hasCourses) $("transcript-file").value = "";
    showTranscriptIssues(result.rowIssues);
  }
  const saved = "과목 " + result.inserted + "개 저장 · 동일 행 " + result.skipped + "개 건너뜀 · 오류·충돌 " + result.rowIssues.length + "행.";
  const gpa = result.savedGpa == null ? "" : " 평점 " + result.savedGpa + " / 4.5 자동 저장.";
  const semesters = result.savedCompletedSemesters == null ? "" : " 정규 이수학기 " + result.savedCompletedSemesters + "개 자동 저장.";
  if (!$("transcript-file").files.length || file === $("transcript-file").files[0])
    $("transcript-summary").textContent = saved + gpa + semesters;
  if (result.savedGpa != null) Object.assign(state.profile, {gpa: result.savedGpa, gpaScale: 4.5});
  if (result.savedCompletedSemesters != null) state.profile.semester = result.savedCompletedSemesters;
  showProfileTranscript(state.profile);
  return {message: saved + gpa + semesters, hasCourses};
}

run(async () => { await readCatalogs(); notice("코드 목록을 불러왔습니다. 이메일 인증 또는 로그인부터 시작하세요."); });
