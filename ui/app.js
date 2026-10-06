"use strict";
const $ = (id) => document.getElementById(id);
const state = { datasets: [], jobs: [], selected: "sample", job: null, tab: "input", ready: false,
  input: { page: 1, search: "", data: null }, results: { page: 1, search: "", data: null },
  defaults: null, pending: false, selectionRequest: 0, request: { input: 0, results: 0 } };
const statuses = { queued: "Đang chuẩn bị", running: "Đang chạy Hadoop", cancelling: "Đang hủy", cancelled: "Đã hủy", succeeded: "Hoàn thành", failed: "Thất bại" };
const active = (job) => job && ["queued", "running", "cancelling"].includes(job.status);
const n = (value) => value == null ? "—" : Number(value).toLocaleString("vi-VN");
const money = (value) => { const [integer, decimal] = String(value).split("."); return integer.replace(/\B(?=(\d{3})+(?!\d))/g, ".") + (decimal ? "," + decimal : ""); };
const esc = (value) => String(value ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const icons = () => window.lucide?.createIcons();
const dataset = () => state.datasets.find(d => d.id === state.selected);
function notice(message = "") {
  const changed = $("notice").textContent !== message;
  $("notice").textContent = message; $("notice").hidden = !message;
  if (message && changed) {
    const rect = $("notice").getBoundingClientRect();
    if (rect.top < 0 || rect.top > innerHeight) $("notice").scrollIntoView({behavior:"smooth",block:"nearest"});
  }
}
let toastTimer;
function toast(message) { $("toast").textContent = message; $("toast").hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => $("toast").hidden = true, 4500); }
async function api(path, data) {
  const response = await fetch(path, data === undefined ? {} : { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify(data) });
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || "Không thể thực hiện yêu cầu.");
  return body;
}
function getRules() {
  const data = Object.fromEntries(new FormData($("filter-form")));
  data.categories = [...document.querySelectorAll('#categories input:checked')].map(e => e.value).join(",");
  data.statuses = [...document.querySelectorAll('#statuses input:checked')].map(e => e.value).join(",");
  return data;
}
function checks(id, options, selected) {
  const current = selected.split(",");
  $(id).innerHTML = [...new Set([...current, ...options])].filter(Boolean).map(value => `<label><input type="checkbox" value="${esc(value)}" ${current.includes(value) ? "checked" : ""}><span>${esc(value)}</span></label>`).join("");
}
function setRules(rules) {
  for (const [key, value] of Object.entries(rules)) {
    const input = $(key.replaceAll("_", "-"));
    if (input && !["categories", "statuses"].includes(key)) input.value = value;
  }
  const source = dataset();
  checks("categories", source?.categories || [], rules.categories);
  checks("statuses", source?.statuses || [], rules.statuses);
  updateFormState();
}
function updateFormState() {
  if (!state.defaults) return;
  const rules = getRules();
  const reference = state.job?.rules || state.defaults;
  const changed = Object.keys(reference).some(key => String(rules[key]) !== String(reference[key]));
  $("form-state").textContent = changed ? "Chưa chạy thay đổi" : state.job ? "Theo lần chạy" : "Mặc định";
  $("form-state").classList.toggle("dirty", changed);
  const validMoney = value => /^[0-9]+(?:\.[0-9]{1,2})?$/.test(value);
  $("amount-preview").textContent = validMoney(rules.min_amount) && validMoney(rules.max_amount) ? `${money(rules.min_amount)} – ${money(rules.max_amount)} VND` : "";
  $("total-preview").textContent = validMoney(rules.min_total) ? `${money(rules.min_total)} VND` : "";
}
function toggleConfiguration(open) {
  document.querySelector(".workspace").classList.toggle("config-open", open);
  $("toggle-config").setAttribute("aria-expanded", String(open));
}
function revealResult() {
  toggleConfiguration(false);
  if (matchMedia("(max-width: 800px)").matches) document.querySelector(".result-heading").scrollIntoView({behavior:"smooth",block:"start"});
}
function catalog() {
  $("dataset").innerHTML = state.datasets.map(d => `<option value="${esc(d.id)}">${esc(d.name)}</option>`).join("");
  $("dataset").value = state.selected;
  const source = dataset();
  if (!source) return;
  const size = source.bytes < 1024 ? `${n(source.bytes)} B` : source.bytes < 1024 * 1024 ? `${(source.bytes / 1024).toLocaleString("vi-VN", {maximumFractionDigits:1})} KB` : `${(source.bytes / (1024 * 1024)).toLocaleString("vi-VN", {maximumFractionDigits:2})} MB`;
  $("dataset-name").textContent = source.name;
  $("dataset-trigger").title = source.name;
  $("dataset-trigger").setAttribute("aria-label", "Bộ dữ liệu: " + source.name);
  $("dataset-size").textContent = `${n(source.records)} bản ghi · ${size}`;
  $("dataset-trigger").disabled = false;
  $("dataset-meta").innerHTML = source.invalid ? `<i data-lucide="triangle-alert"></i><span class="invalid-info">${n(source.invalid)} bản ghi lỗi</span>` : '<i data-lucide="shield-check"></i><span>Dữ liệu hợp lệ</span>';
  $("dataset-meta").classList.toggle("has-errors", !!source.invalid);
  let uploaded = false;
  $("dataset-options").innerHTML = '<div class="dataset-group" role="presentation">Dữ liệu mẫu</div>' + state.datasets.map(d => {
    const group = d.uploaded && !uploaded ? '<div class="dataset-group" role="presentation">Đã tải lên</div>' : "";
    if (d.uploaded) uploaded = true;
    return `${group}<button type="button" role="option" aria-selected="${d.id === state.selected}" tabindex="-1" class="dataset-option" data-dataset="${esc(d.id)}"><i data-lucide="${d.uploaded ? "upload" : "file-spreadsheet"}"></i><span><strong>${esc(d.name)}</strong><small>${n(d.records)} bản ghi${d.invalid ? ` · ${n(d.invalid)} lỗi` : ""}</small></span><i data-lucide="check" class="selection-check"></i></button>`;
  }).join("");
  icons();
}
function closeDatasetPicker(restoreFocus = false) {
  $("dataset-options").hidden = true;
  $("dataset-trigger").setAttribute("aria-expanded", "false");
  if (restoreFocus) $("dataset-trigger").focus();
}
function openDatasetPicker(last = false) {
  $("dataset-options").hidden = false;
  $("dataset-trigger").setAttribute("aria-expanded", "true");
  const rect = $("dataset-trigger").getBoundingClientRect();
  $("dataset-picker").classList.toggle("opens-up", innerHeight - rect.bottom < $("dataset-options").offsetHeight + 10 && rect.top > innerHeight - rect.bottom);
  const options = [...$("dataset-options").querySelectorAll('[role="option"]')];
  const selected = options.find(option => option.dataset.dataset === state.selected);
  (last ? options.at(-1) : selected || options[0])?.focus();
}
$("dataset-trigger").addEventListener("click", () => $("dataset-options").hidden ? openDatasetPicker() : closeDatasetPicker(true));
$("dataset-trigger").addEventListener("keydown", event => {
  if (["ArrowDown", "ArrowUp"].includes(event.key)) { event.preventDefault(); openDatasetPicker(event.key === "ArrowUp"); }
});
$("dataset-options").addEventListener("click", event => {
  const option = event.target.closest("[data-dataset]"); if (!option) return;
  $("dataset").value = option.dataset.dataset;
  closeDatasetPicker(true);
  $("dataset").dispatchEvent(new Event("change"));
});
$("dataset-options").addEventListener("keydown", event => {
  const options = [...$("dataset-options").querySelectorAll('[role="option"]')];
  const index = options.indexOf(document.activeElement);
  if (event.key === "Escape") { event.preventDefault(); closeDatasetPicker(true); }
  else if (event.key === "Tab") closeDatasetPicker();
  else if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
    event.preventDefault();
    const next = event.key === "Home" ? 0 : event.key === "End" ? options.length - 1 : (index + (event.key === "ArrowDown" ? 1 : options.length - 1)) % options.length;
    options[next]?.focus();
  } else if (event.key.length === 1 && event.key !== " ") {
    const ordered = [...options.slice(index + 1), ...options.slice(0, index + 1)];
    const match = ordered.find(option => option.textContent.trim().toLocaleLowerCase("vi").startsWith(event.key.toLocaleLowerCase("vi")));
    if (match) { event.preventDefault(); match.focus(); }
  }
});
document.addEventListener("pointerdown", event => { if (!$("dataset-picker").contains(event.target)) closeDatasetPicker(); });
document.addEventListener("focusin", event => { if (!$("dataset-picker").contains(event.target)) closeDatasetPicker(); });
function emptyRow(message, icon = "inbox") { return `<tr><td colspan="6" class="empty-cell"><i data-lucide="${icon}"></i><p>${esc(message)}</p></td></tr>`; }
function renderTable(kind, data) {
  const tableState = state[kind];
  tableState.data = data;
  $(kind + "-body").innerHTML = data.rows.length ? data.rows.map(row => {
    const index = data.rows.indexOf(row);
    const attributes = `data-record="${kind}:${index}" tabindex="0" aria-label="Xem bản ghi dòng ${row.line}"`;
    if (row.values.length !== 6) return `<tr class="row-invalid" ${attributes}><td colspan="6"><div class="invalid-record"><i data-lucide="triangle-alert"></i><span><strong>${esc(row.error)} · dòng ${row.line}</strong><small>${esc(row.values.join(","))}</small></span></div></td></tr>`;
    return `<tr class="${row.error ? "row-invalid" : ""}" ${attributes} title="${esc(row.error || "")}">${row.values.map((v, i) => `<td class="${i === 3 ? "numeric" : ""}" title="${esc(v)}">${!v.trim() ? '<span class="missing-value">Thiếu dữ liệu</span>' : i === 3 && /^[0-9]+(\.[0-9]{1,2})?$/.test(v) ? money(v) : i === 5 ? `<span class="state-badge ${v === "CANCELLED" ? "cancelled" : v !== "COMPLETED" ? "other" : ""}">${esc(v === "COMPLETED" ? "Hoàn thành" : v === "CANCELLED" ? "Đã hủy" : v)}</span>` : esc(v)}</td>`).join("")}</tr>`;
  }).join("") : emptyRow(tableState.search ? "Không có bản ghi khớp tìm kiếm." : "Không có bản ghi đạt điều kiện.");
  $(kind + "-count").textContent = tableState.search ? `${n(data.matched)} / ${n(data.total)} bản ghi` : n(data.total) + " bản ghi";
  const start = data.rows.length ? (data.page - 1) * data.limit + 1 : 0;
  $(kind + "-range").textContent = `${n(start)}–${n(data.rows.length ? start + data.rows.length - 1 : 0)} / ${n(data.matched)}`;
  $(kind + "-page").textContent = `${data.page} / ${data.pages}`;
  $(kind + "-prev").disabled = data.page <= 1;
  $(kind + "-next").disabled = data.page >= data.pages;
  icons();
}
async function loadTable(kind) {
  const seq = ++state.request[kind];
  if (kind === "results" && state.job?.status !== "succeeded") {
    renderTable("results", { rows: [], total: 0, matched: 0, page: 1, pages: 1, limit: 25 });
    $("results-body").innerHTML = emptyRow(active(state.job) ? "Đang xử lý dữ liệu…" : state.job?.status === "failed" ? "Job thất bại · Không có kết quả" : state.job?.status === "cancelled" ? "Job đã hủy" : "Chưa có kết quả", active(state.job) ? "loader-circle" : state.job?.status === "failed" ? "circle-alert" : "inbox");
    icons(); return;
  }
  const context = kind === "input" ? state.job?.id || state.selected : state.job.id;
  state[kind].data = null;
  $(kind + "-body").innerHTML = emptyRow("Đang tải dữ liệu…", "loader-circle");
  $(kind + "-prev").disabled = true; $(kind + "-next").disabled = true;
  icons();
  const url = kind === "input" ? state.job ? `/api/jobs/${context}/input` : `/api/datasets/${context}` : `/api/jobs/${context}/rows`;
  const data = await api(`${url}?page=${state[kind].page}&search=${encodeURIComponent(state[kind].search)}`);
  if (seq !== state.request[kind] || context !== (kind === "input" ? state.job?.id || state.selected : state.job?.id)) return;
  renderTable(kind, data);
}
function switchTab(tab) {
  state.tab = tab;
  document.querySelectorAll("[data-tab]").forEach(button => { const selected = button.dataset.tab === tab; button.setAttribute("aria-selected", selected); button.tabIndex = selected ? 0 : -1; });
  ["input", "results", "stats", "log"].forEach(name => $("panel-" + name).hidden = name !== tab);
  if (tab === "input" || tab === "results") loadTable(tab).catch(error => notice(error.message));
  if (tab === "log") $("job-log").scrollTop = $("job-log").scrollHeight;
}
const counterLabels = { INPUT_RECORDS: "Bản ghi đầu vào", HEADER_LINES: "Dòng tiêu đề", EMPTY_LINES: "Dòng trống", INVALID_RECORDS: "Bản ghi lỗi", MAP_MATCHED: "Qua bộ lọc Map", MAP_REJECTED: "Bị loại ở Map", CUSTOMERS_ACCEPTED: "Khách hàng đạt điều kiện", CUSTOMERS_REJECTED: "Khách hàng bị loại", OUTPUT_RECORDS: "Bản ghi đầu ra", GROUP_REJECTED_RECORDS: "Bị loại ở Reduce" };
function renderStats(job) {
  const c = job?.metrics?.counters;
  if (!c) { $("stats-content").innerHTML = '<p class="history-empty">Chưa có thống kê của job hoàn thành.</p>'; return; }
  const total = c.INPUT_RECORDS || 1;
  const bars = [["Bản ghi lỗi", c.INVALID_RECORDS, "invalid"], ["Bị loại ở Map", c.MAP_REJECTED, "reject"], ["Qua Map", c.MAP_MATCHED, ""], ["Bị loại ở Reduce", c.GROUP_REJECTED_RECORDS, "reject"], ["Đầu ra", c.OUTPUT_RECORDS, "output"]];
  $("stats-content").innerHTML = `<h3>Phân bố bản ghi</h3>${bars.map(([label,value,cls]) => `<div class="flow-row ${cls}"><span>${label}</span><div class="flow-bar"><span></span></div><b>${n(value)}</b></div>`).join("")}<div class="counter-list"><h3>Counters Hadoop</h3>${Object.entries(c).map(([key,value]) => `<div class="counter-row"><span>${counterLabels[key]}<br><code>${key}</code></span><strong>${n(value)}</strong></div>`).join("")}</div><div class="counter-row"><span>Thời gian toàn tiến trình</span><strong>${n(job.process_seconds)} giây</strong></div><a class="stat-download" href="/api/jobs/${job.id}/report" download><i data-lucide="download"></i>Tải thống kê và cấu hình JSON</a>`;
  document.querySelectorAll(".flow-bar span").forEach((bar, index) => { bar.removeAttribute("style"); bar.style.width = (bars[index][1] / total * 100) + "%"; });
}
function renderJob() {
  const job = state.job;
  const c = job?.metrics?.counters;
  $("result-title").textContent = job ? job.dataset.name : dataset()?.name || "Dữ liệu đơn hàng";
  $("result-subtitle").textContent = job ? `${new Date(job.created_at).toLocaleString("vi-VN")} · ${statuses[job.status]}` : `${dataset()?.uploaded ? dataset()?.name : dataset()?.filename || "CSV"} · Dữ liệu đầu vào`;
  $("metric-input").textContent = n(c?.INPUT_RECORDS ?? job?.dataset.records ?? dataset()?.records);
  $("metric-map").textContent = n(c?.MAP_MATCHED);
  $("metric-output").textContent = n(c?.OUTPUT_RECORDS);
  $("metric-time").textContent = job?.metrics ? (job.metrics.job_elapsed_ms / 1000).toLocaleString("vi-VN", {maximumFractionDigits:3}) : "—";
  for (const id of ["metric-map", "metric-output", "metric-time"]) {
    const missing = !job?.metrics;
    $(id).classList.toggle("pending-metric", missing);
    if (missing) $(id).textContent = active(job) ? "Đang xử lý" : !job ? "Chưa chạy" : "Không có kết quả";
  }
  $("run-state").hidden = !job;
  $("run-state").dataset.status = job?.status || "";
  $("cancel").hidden = !active(job);
  $("cancel").disabled = job?.status === "cancelling";
  $("download").hidden = job?.status !== "succeeded";
  $("download-log").hidden = !job;
  $("run-rules").hidden = !job;
  if (job) {
    $("download").href = `/api/jobs/${job.id}/download`;
    $("download").setAttribute("download", "");
    $("download-log").href = `/api/jobs/${job.id}/log`;
    $("job-status").textContent = statuses[job.status] || job.status;
    $("job-progress").textContent = job.progress ? `Map ${job.progress[0]}% · Reduce ${job.progress[1]}%` : active(job) ? "Đang khởi động" : "";
    $("progress-fill").style.width = job.status === "succeeded" ? "100%" : job.progress ? (job.progress[0] + job.progress[1]) / 2 + "%" : "0%";
    $("stage-map").classList.toggle("done", job.status === "succeeded" || job.progress?.[0] === 100);
    $("stage-reduce").classList.toggle("done", job.status === "succeeded" || job.progress?.[1] === 100);
    $("stage-verify").classList.toggle("done", !!job.verified);
    $("verification").innerHTML = job.verified ? '<i data-lucide="shield-check"></i>Đã đối chiếu toàn bộ kết quả và counters' : job.error ? esc(job.error) : "";
    $("verification").style.color = job.error ? "var(--red)" : "var(--green)";
    const r = job.rules;
    $("run-rules-content").innerHTML = `<div class="rule-lines"><span>Giá trị: ${money(r.min_amount)} – ${money(r.max_amount)} VND</span><span>Ngày: ${r.start_date} – ${r.end_date}</span><span>Loại: ${esc(r.categories)} · Trạng thái: ${esc(r.statuses)}</span><span>Khách hàng: ≥ ${n(r.min_orders)} đơn và ≥ ${money(r.min_total)} VND</span></div>`;
  }
  $("job-log").textContent = job?.log || (job ? "Đang chờ log Hadoop…" : "Chưa có lần chạy.");
  if (state.tab === "log" && active(job)) $("job-log").scrollTop = $("job-log").scrollHeight;
  renderStats(job);
  updateFormState();
  renderRunButton(); icons();
}
function renderRunButton() {
  const busy = state.pending || state.jobs.some(active);
  $("run").disabled = !state.ready || busy;
  if (state.runBusy !== busy) {
    state.runBusy = busy;
    $("run").innerHTML = `<i data-lucide="${busy ? "loader-circle" : "play"}"></i><span>${busy ? "Đang xử lý" : "Chạy bộ lọc"}</span>`;
  }
}
function renderHistory() {
  $("history-count").textContent = `${state.jobs.length} lần chạy`;
  $("open-active").hidden = !state.jobs.some(job => active(job) && job.id !== state.job?.id);
  const signature = JSON.stringify([state.job?.id, state.jobs.map(j => [j.id, j.status, j.metrics?.counters.OUTPUT_RECORDS])]);
  if (state.historySignature !== signature) {
    state.historySignature = signature;
    $("history-list").innerHTML = state.jobs.length ? state.jobs.map(job => `<button class="history-item ${job.status} ${state.job?.id === job.id ? "selected" : ""}" data-job="${job.id}"><i data-lucide="${job.status === "succeeded" ? "circle-check" : job.status === "failed" ? "circle-alert" : active(job) ? "loader-circle" : "circle-minus"}"></i><span><strong>${esc(job.dataset.name)}</strong><small>${new Date(job.created_at).toLocaleString("vi-VN")} · ≥ ${n(job.rules.min_orders)} đơn</small></span><span class="history-result">${job.metrics ? `<b>${n(job.metrics.counters.OUTPUT_RECORDS)} bản ghi</b><br>` : ""}${statuses[job.status]}</span></button>`).join("") : '<p class="history-empty">Chưa có lần chạy.</p>';
  }
  renderRunButton(); icons();
}
async function chooseJob(id) {
  const request = ++state.selectionRequest;
  const job = await api(`/api/jobs/${id}`);
  if (request !== state.selectionRequest) return;
  state.job = job;
  state.selected = job.dataset.id;
  catalog(); setRules(job.rules);
  state.results.page = 1; state.results.search = ""; $("results-search").value = "";
  state.input.page = 1; state.input.search = ""; $("input-search").value = "";
  renderJob(); renderHistory(); switchTab("results");
  revealResult();
}
let polling = false;
async function poll() {
  if (polling) return;
  polling = true;
  try {
    state.jobs = await api("/api/jobs");
    const id = state.job?.id;
    if (id && active(state.job)) {
      const job = await api(`/api/jobs/${id}`);
      if (state.job?.id === id) {
        const finished = !active(job);
        state.job = job; renderJob();
        if (finished) { if (state.tab === "results") await loadTable("results"); toast(job.status === "succeeded" ? `Hoàn thành · ${n(job.metrics.counters.OUTPUT_RECORDS)} bản ghi` : statuses[job.status]); }
      }
    }
    renderHistory();
    if ($("notice").textContent.startsWith("Mất kết nối máy chủ.")) notice();
  } catch (error) { notice("Mất kết nối máy chủ. " + error.message); }
  finally { polling = false; }
}
$("filter-form").addEventListener("submit", async event => {
  event.preventDefault(); notice();
  const rules = getRules();
  if (!rules.categories || !rules.statuses) { notice("Chọn ít nhất một loại sản phẩm và một trạng thái."); return; }
  state.pending = true; renderRunButton(); icons();
  const selection = ++state.selectionRequest;
  try {
    const job = await api("/api/jobs", { dataset: state.selected, rules });
    state.jobs.unshift(job);
    if (selection !== state.selectionRequest) { renderHistory(); return; }
    state.job = job; state.selected = job.dataset.id; catalog(); setRules(job.rules);
    state.results.page = 1; state.results.search = ""; $("results-search").value = "";
    renderJob(); renderHistory(); switchTab("results");
    revealResult();
  } catch (error) { notice(error.message); }
  finally { state.pending = false; renderRunButton(); icons(); }
});
$("dataset").addEventListener("change", () => {
  state.selectionRequest++;
  const rules = getRules(); state.selected = $("dataset").value; state.job = null;
  state.input.page = 1; state.input.search = ""; $("input-search").value = "";
  catalog(); setRules(rules); renderJob(); renderHistory(); switchTab("input");
});
$("reset").addEventListener("click", () => { setRules(state.defaults); notice(); toast("Đã khôi phục điều kiện mặc định."); });
$("filter-form").addEventListener("input", updateFormState);
$("toggle-config").addEventListener("click", () => toggleConfiguration($("toggle-config").getAttribute("aria-expanded") !== "true"));
$("open-history").addEventListener("click", () => $("history-section").scrollIntoView({ behavior: "smooth", block: "start" }));
$("open-active").addEventListener("click", () => { const job = state.jobs.find(active); if (job) chooseJob(job.id).catch(error => notice(error.message)); });
$("close-record").addEventListener("click", () => $("record-dialog").close());
$("record-dialog").addEventListener("click", event => { if (event.target === $("record-dialog")) { const r = event.target.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) event.target.close(); } });
function showRecord(target) {
  const record = target.closest("[data-record]"); if (!record) return;
  const [kind, index] = record.dataset.record.split(":");
  const row = state[kind].data?.rows[Number(index)]; if (!row) return;
  $("record-title").textContent = row.values.length === 6 ? row.values[0] : `Dòng ${row.line}`;
  const labels = ["Mã đơn", "Khách hàng", "Ngày đặt", "Giá trị (VND)", "Loại sản phẩm", "Trạng thái"];
  $("record-content").innerHTML = (row.error ? `<div class="record-error">${esc(row.error)}</div>` : "") + (row.values.length === 6 ? row.values.map((value,i) => `<div class="record-field"><span>${labels[i]}</span><strong>${esc(value || "(rỗng)")}</strong></div>`).join("") : `<pre class="record-raw">${esc(row.values.join(","))}</pre>`);
  $("record-dialog").showModal();
}
for (const kind of ["input", "results"]) {
  $(kind + "-body").addEventListener("click", event => showRecord(event.target));
  $(kind + "-body").addEventListener("keydown", event => { if (["Enter", " "].includes(event.key)) { event.preventDefault(); showRecord(event.target); } });
}
$("cancel").addEventListener("click", async () => { try { state.job = await api(`/api/jobs/${state.job.id}/cancel`, {}); renderJob(); await poll(); } catch (error) { notice(error.message); } });
$("refresh").addEventListener("click", async () => { try { await poll(); if (["input", "results"].includes(state.tab)) await loadTable(state.tab); } catch (error) { notice(error.message); } });
$("history-list").addEventListener("click", event => { const button = event.target.closest("[data-job]"); if (button) chooseJob(button.dataset.job).catch(error => notice(error.message)); });
document.querySelectorAll("[data-tab]").forEach(button => {
  button.addEventListener("click", () => switchTab(button.dataset.tab));
  button.addEventListener("keydown", event => {
    if (!["ArrowLeft", "ArrowRight"].includes(event.key)) return;
    event.preventDefault(); const tabs = [...document.querySelectorAll("[data-tab]")];
    const next = tabs[(tabs.indexOf(button) + (event.key === "ArrowRight" ? 1 : 3)) % 4];
    next.focus(); switchTab(next.dataset.tab);
  });
});
for (const kind of ["input", "results"]) {
  for (const [action, delta] of [["prev", -1], ["next", 1]]) $(kind + "-" + action).addEventListener("click", () => { state[kind].page += delta; loadTable(kind).catch(error => notice(error.message)); });
  let timer;
  $(kind + "-search").addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(() => { state[kind].page = 1; state[kind].search = $(kind + "-search").value; loadTable(kind).catch(error => notice(error.message)); }, 250); });
}
$("upload-button").addEventListener("click", () => $("file-input").click());
$("file-input").addEventListener("change", async () => {
  const file = $("file-input").files[0]; if (!file) return;
  if (file.size > 16 * 1024 * 1024) { notice("File không được vượt quá 16 MB."); $("file-input").value = ""; return; }
  $("upload-button").disabled = true;
  try {
    const content = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result.split(",")[1]); reader.onerror = reject; reader.readAsDataURL(file); });
    const entry = await api("/api/uploads", {name: file.name, content});
    state.selectionRequest++; state.datasets.push(entry); state.selected = entry.id; state.job = null; state.input.page = 1;
    state.input.search = ""; $("input-search").value = "";
    catalog(); setRules(state.defaults); renderJob(); renderHistory(); switchTab("input"); toast("Đã tải lên " + file.name);
  } catch (error) { notice(error.message || "Không đọc được file."); }
  finally { $("upload-button").disabled = false; $("file-input").value = ""; }
});
async function init() {
  icons();
  try {
    const [status, datasets, jobs] = await Promise.all([api("/api/status"), api("/api/datasets"), api("/api/jobs")]);
    state.ready = status.ready; state.defaults = status.defaults; state.datasets = datasets; state.jobs = jobs;
    if (!datasets.some(d => d.id === state.selected)) state.selected = datasets[0]?.id;
    $("engine").classList.toggle("ready", status.ready);
    $("engine").lastElementChild.textContent = status.ready ? `${status.engine} · ${status.mode}` : "Môi trường chưa sẵn sàng";
    catalog(); setRules(state.defaults); renderJob(); renderHistory(); await loadTable("input");
    const running = jobs.find(active); if (running) await chooseJob(running.id);
    if (!status.ready) notice("Chưa có Hadoop hoặc JAR. Chạy cài đặt và build để tiếp tục.");
  } catch (error) { notice(error.message); }
  setInterval(poll, 1200);
}
init();
