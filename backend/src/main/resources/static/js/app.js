/* College Feedback Management System: browser tier.
   Talks ONLY to the REST API (HTTP + JSON). It never touches the database. */
const COLLEGE_NAME = "South Asian University";   // change here if the slides use another name

const STATUS_ORDER = ["SUBMITTED", "UNDER_REVIEW", "ACTION_TAKEN", "CLOSED"];
const STATUS_LABEL = { SUBMITTED: "Submitted", UNDER_REVIEW: "Under review", ACTION_TAKEN: "Action taken", CLOSED: "Closed" };
const ROLES = ["STUDENT", "FACULTY", "STAFF", "GUARD", "HELPER", "OTHER"];
const MOODS = ["", "Very poor", "Poor", "Okay", "Good", "Excellent"];
/** No emoji: each category gets a plain-text letter badge instead. */
const mono = (name) => `<span class="ico" aria-hidden="true">${esc((String(name || "?").trim()[0] || "?").toUpperCase())}</span>`;
const REDUCED = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const $app = document.getElementById("app");
const $panel = document.getElementById("notif-panel"), $bell = document.getElementById("bell");
let me = null, categories = [], heroTimer = null;

/* ---------- helpers ---------- */
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const fmt = (t) => t ? new Date(t).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "";
const stars = (n) => `<span class="stars-static" role="img" aria-label="${n} out of 5 stars">${"★".repeat(n)}<span class="off">${"★".repeat(5 - n)}</span></span>`;
const badge = (s) => `<span class="badge badge-${s}">${STATUS_LABEL[s] || esc(s)}</span>`;
const firstName = (n) => (String(n || "").replace(/^(dr|prof|mr|mrs|ms)\.?\s+/i, "").split(/\s+/)[0]) || "there";
const greeting = () => { const h = new Date().getHours(); return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening"; };
const roleLabel = (r) => r === "ADMIN" ? "Management" : r.charAt(0) + r.slice(1).toLowerCase();

/** Status bar: four labelled steps; the current one is highlighted and announced. */
function track(status, size = "") {
  const i = Math.max(0, STATUS_ORDER.indexOf(status));
  const steps = STATUS_ORDER.map((s, k) => {
    const state = k < i ? "done" : k === i ? `now s${k}` : "";
    const sr = k < i ? " (done)" : k === i ? " (current)" : "";
    return `<li class="${state}"${k === i ? ' aria-current="step"' : ""}><span class="lbl">${STATUS_LABEL[s]}<span class="sr">${sr}</span></span></li>`;
  }).join("");
  const note = size === "big" ? `<p class="steps-note">Step ${i + 1} of ${STATUS_ORDER.length}</p>` : "";
  return `<ol class="steps ${size}" aria-label="Status: ${STATUS_LABEL[status]}, step ${i + 1} of ${STATUS_ORDER.length}">${steps}</ol>${note}`;
}

async function api(method, url, body) {
  const res = await fetch(url, { method, headers: body ? { "Content-Type": "application/json" } : {}, body: body ? JSON.stringify(body) : undefined });
  let data = null;
  try { data = await res.json(); } catch (_) { /* empty body */ }
  if (!res.ok) {
    const err = new Error(data?.error || `Something went wrong (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return data;
}

/** Errors only: a visible bar. Routine successes are announced quietly to screen readers. */
let toastTimer;
function toast(msg) {
  const t = document.getElementById("toast");
  t.textContent = msg; t.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 4500);
}
function announce(msg) {
  const a = document.getElementById("announce");
  a.textContent = ""; setTimeout(() => (a.textContent = msg), 50);
}

function countUp(el, to, decimals = 0) {
  if (to == null || isNaN(to)) { el.textContent = "–"; return; }
  if (REDUCED) { el.textContent = to.toFixed(decimals); return; }
  const t0 = performance.now(), dur = 800;
  const tick = (now) => {
    const p = Math.min(1, (now - t0) / dur), e = 1 - Math.pow(1 - p, 3);
    el.textContent = (to * e).toFixed(decimals);
    if (p < 1) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

function chrome() {
  document.getElementById("college-name").textContent = COLLEGE_NAME;
  const bar = document.getElementById("topbar");
  bar.hidden = !me;
  if (me) {
    document.getElementById("user-name").textContent = me.fullName;
    document.getElementById("user-role").textContent = roleLabel(me.role);
    document.getElementById("avatar").textContent = (firstName(me.fullName)[0] || "?").toUpperCase();
  }
}
document.getElementById("logout-btn").onclick = async () => { try { await api("POST", "/api/auth/logout"); } catch (_) {} me = null; stopAlerts(); start(); };

function nav(items, active, onPick) {
  const el = document.createElement("nav");
  el.className = "nav"; el.setAttribute("aria-label", "Sections");
  items.forEach(([key, label]) => {
    const b = document.createElement("button");
    b.type = "button"; b.textContent = label;
    if (key === active) b.setAttribute("aria-current", "page");
    b.onclick = () => onPick(key);
    el.appendChild(b);
  });
  return el;
}
/** Full-width layout: greeting + navigation in a left rail, content on the wide stage. <main> is the grid itself. */
function shell(title, sub, navEl) {
  $app.className = "page dash";
  $app.innerHTML = `<aside class="rail"><h1>${title}</h1><p class="sub">${sub}</p></aside><div class="stage-main"></div>`;
  const rail = $app.firstElementChild;
  rail.appendChild(navEl);
  const extra = document.createElement("section"); extra.className = "rail-extra"; extra.setAttribute("aria-label", "Summary"); rail.appendChild(extra);
  window.scrollTo(0, 0);
  return { extra, body: $app.lastElementChild };
}
const skeleton = (n = 3) => Array.from({ length: n }, () => '<p class="skeleton" aria-hidden="true"></p>').join("");
const kpiList = (rows) => `<ul class="kpis">${rows.map(([n, l, attrs = ""]) => `<li class="kpi"><strong class="n" ${attrs}>${n}</strong><span class="l">${l}</span></li>`).join("")}</ul>`;

/* ====================================================================
   Sign in / register
   ==================================================================== */

function showAuth(mode = "login", msg = "") {
  clearInterval(heroTimer);
  stopAlerts();
  chrome();
  const isLogin = mode === "login";
  $app.className = "auth";
  $app.innerHTML = `
    <section class="hero" aria-label="About this system">
      <p class="brand">
        <svg class="logo" viewBox="0 0 32 32" aria-hidden="true"><rect width="32" height="32" rx="9"/><path d="M9 11.5h14a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-6.5L12 25v-4.5H9A1.5 1.5 0 0 1 7.5 19v-6A1.5 1.5 0 0 1 9 11.5z" fill="none" stroke-width="1.8" stroke-linejoin="round"/><path d="M12.5 16l2.4 2.3 4.6-4.6" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
        <span><strong>${esc(COLLEGE_NAME)}</strong><small>Feedback Management System</small></span>
      </p>
      <h1>Say it once. See it through.</h1>
      <p class="lede">Report a problem on campus, follow it from first report to closed, and read management's reply.</p>
    </section>

    <section class="auth-panel" aria-label="${isLogin ? "Log in" : "Create account"}">
      <article class="auth-box">
        <h2>${isLogin ? "Log in" : "Create your account"}</h2>
        <p class="sub">${isLogin ? "Use your college ID to give or track feedback." : "Register once with your college ID."}</p>
        ${msg ? `<p class="alert alert-ok" role="status">${esc(msg)}</p>` : ""}
        <form id="auth-form" novalidate>
          <label for="iid">College ID</label>
          <input id="iid" autocomplete="username" maxlength="30" autocapitalize="characters" spellcheck="false" required>
          ${isLogin ? "" : `
          <label for="fname">Full name</label><input id="fname" autocomplete="name" maxlength="100" required>
          <p class="field-row">
            <span><label for="role">I am a</label><select id="role">${ROLES.map((r) => `<option value="${r}">${roleLabel(r)}</option>`).join("")}</select></span>
            <span><label for="dept">Department <small>(optional)</small></label><input id="dept" maxlength="100"></span>
          </p>`}
          <label for="pw">Password</label>
          <span class="pwd"><input id="pw" type="password" autocomplete="${isLogin ? "current-password" : "new-password"}" required><button type="button" id="pw-toggle" aria-label="Show password">Show</button></span>
          ${isLogin ? "" : `<p class="hint">At least 8 characters. We store only a BCrypt hash, never the password itself.</p>`}
          <p id="auth-error" class="alert alert-error" role="alert" hidden></p>
          <button class="btn btn-block btn-lg" style="margin-top:1.3rem" type="submit">${isLogin ? "Log in" : "Create account"}</button>
        </form>
        <p class="switch">${isLogin ? "New here?" : "Already registered?"} <a href="#" id="switch">${isLogin ? "Create an account" : "Log in"}</a></p>
      </article>
    </section>`;

  const pw = document.getElementById("pw");
  document.getElementById("pw-toggle").onclick = (e) => { const show = pw.type === "password"; pw.type = show ? "text" : "password"; e.target.textContent = show ? "Hide" : "Show"; e.target.setAttribute("aria-label", show ? "Hide password" : "Show password"); };
  document.getElementById("switch").onclick = (e) => { e.preventDefault(); showAuth(isLogin ? "register" : "login"); };
  document.getElementById("auth-form").onsubmit = async (e) => {
    e.preventDefault();
    const err = document.getElementById("auth-error"), btn = e.target.querySelector("button[type=submit]");
    err.hidden = true;
    const v = (id) => document.getElementById(id)?.value.trim();
    if (!v("iid") || !pw.value) { err.textContent = "Enter your college ID and password."; err.hidden = false; return; }
    btn.disabled = true;
    try {
      if (isLogin) {
        me = await api("POST", "/api/auth/login", { institutionalId: v("iid"), password: pw.value });
        start();
      } else {
        if (!v("fname")) throw new Error("Enter your full name.");
        await api("POST", "/api/auth/register", { institutionalId: v("iid"), fullName: v("fname"), role: v("role"), department: v("dept"), password: pw.value });
        showAuth("login", "Account created. Log in to continue.");
      }
    } catch (ex) { err.textContent = ex.message; err.hidden = false; btn.disabled = false; }
  };
  document.getElementById("iid").focus();
}

/* ====================================================================
   Member screens
   ==================================================================== */
function showUser(tab = "new") {
  clearInterval(heroTimer);
  chrome();
  const { extra, body } = shell(`${greeting()}, ${esc(firstName(me.fullName))}`,
    tab === "new" ? "Tell management what is working and what is not. It takes about a minute." : "Everything you have reported, and where it stands.",
    nav([["new", "Give feedback"], ["mine", "My feedback"]], tab, showUser));
  api("GET", "/api/feedback/mine").then((list) => {
    const open = list.filter((f) => f.status !== "CLOSED").length;
    extra.innerHTML = kpiList([[list.length, "Feedback you have sent"], [open, "Still open"], [list.length - open, "Closed"]]);
  }).catch(() => {});
  tab === "new" ? renderForm(body) : renderMine(body);
}

function renderForm(root) {
  const st = { cat: null, rating: 0, anon: false };
  root.innerHTML = `
    <form id="fb-form" class="compose" novalidate>
      <section class="step" id="s1">
        <h2 class="step-title"><span class="step-no">1</span>What is this about?</h2>
        <input class="tile-search" id="tile-q" type="text" placeholder="Find an area, e.g. WiFi" aria-label="Find an area">
        <span class="tiles" id="tiles" role="radiogroup" aria-label="Category"></span>
        <p class="cat-desc" id="cat-desc"></p>
      </section>
      <section class="step" id="s2">
        <h2 class="step-title"><span class="step-no">2</span>How was it?</h2>
        <p class="rater">
          <span class="stars" id="stars" role="radiogroup" aria-label="Rating">${[1, 2, 3, 4, 5].map((n) => `<button type="button" class="star" data-n="${n}" role="radio" aria-checked="false" aria-label="${n} star${n > 1 ? "s" : ""}">★</button>`).join("")}</span>
          <output class="mood" id="mood" aria-live="polite">Tap a star</output>
        </p>
      </section>
      <section class="step" id="s3">
        <h2 class="step-title"><span class="step-no">3</span>Tell us more</h2>
        <label class="sr" for="comments">Comment</label>
        <textarea id="comments" maxlength="2000" placeholder="What happened? Where and when? The more specific, the faster it gets fixed."></textarea>
        <p class="counter"><span id="count">0</span> / 2000</p>
        <label class="switch-row" id="anon-row">
          <input type="checkbox" id="anon"><span class="toggle" aria-hidden="true"></span>
          <span><b>Post anonymously</b><span>Management will see "Anonymous" instead of your name. You can still follow it under My feedback.</span></span>
        </label>
        <p id="fb-error" class="alert alert-error" role="alert" hidden></p>
        <p class="submit-bar"><button class="btn btn-lg" type="submit" id="send">Send feedback</button><span class="hint" id="ready-hint"></span></p>
      </section>
      <aside class="preview" aria-label="Preview">
        <h2 class="preview-title">How it will look</h2>
        <article class="pcard">
          <header class="row"><span id="p-ico">${mono("?")}</span><span><strong class="t" id="p-cat">Pick an area</strong><span class="s" id="p-stars">${stars(0)}</span></span></header>
          <p class="body empty" id="p-body">Your comment appears here.</p>
          <p id="p-anon"></p>
          ${track("SUBMITTED")}
        </article>
      </aside>
    </form>`;
  const $ = (s) => root.querySelector(s);

  const drawTiles = () => {
    const q = $("#tile-q").value.trim().toLowerCase();
    const list = categories.filter((c) => c.categoryName.toLowerCase().includes(q));
    $("#tiles").innerHTML = list.length ? list.map((c) =>
      `<button type="button" class="tile" role="radio" aria-checked="${st.cat?.categoryId === c.categoryId}" data-id="${c.categoryId}"><span class="e" aria-hidden="true">${esc(c.categoryName[0].toUpperCase())}</span>${esc(c.categoryName)}</button>`).join("")
      : `<p class="no-match">No area matches "${esc(q)}".</p>`;
    $("#tiles").querySelectorAll(".tile").forEach((t) => (t.onclick = () => { st.cat = categories.find((c) => String(c.categoryId) === t.dataset.id); sync(); drawTiles(); }));
  };
  const sync = () => {
    $("#cat-desc").textContent = st.cat?.description || "";
    $("#p-ico").innerHTML = mono(st.cat ? st.cat.categoryName : "?");
    $("#p-cat").textContent = st.cat ? st.cat.categoryName : "Pick an area";
    $("#p-stars").innerHTML = stars(st.rating);
    $("#mood").textContent = MOODS[st.rating] || "Tap a star";
    root.querySelectorAll(".star").forEach((s) => { s.classList.toggle("on", +s.dataset.n <= st.rating); s.setAttribute("aria-checked", String(+s.dataset.n === st.rating)); });
    const text = $("#comments").value.trim();
    $("#p-body").textContent = text || "Your comment appears here.";
    $("#p-body").classList.toggle("empty", !text);
    $("#p-anon").innerHTML = st.anon ? '<span class="badge badge-anon">Anonymous</span>' : "";
    $("#anon-row").classList.toggle("on", st.anon);
    $("#s1").classList.toggle("done", !!st.cat); $("#s2").classList.toggle("done", st.rating > 0); $("#s3").classList.toggle("done", text.length > 0);
    $("#ready-hint").textContent = !st.cat ? "Pick an area to continue." : !st.rating ? "Choose a star rating." : "";
  };
  root.querySelectorAll(".star").forEach((s) => {
    s.onclick = () => { st.rating = +s.dataset.n; sync(); };
    s.onmouseenter = () => { $("#mood").textContent = MOODS[+s.dataset.n]; root.querySelectorAll(".star").forEach((x) => x.classList.toggle("on", +x.dataset.n <= +s.dataset.n)); };
    s.onmouseleave = sync;
  });
  $("#tile-q").oninput = drawTiles;
  $("#comments").oninput = () => { $("#count").textContent = $("#comments").value.length; sync(); };
  $("#anon").onchange = () => { st.anon = $("#anon").checked; sync(); };
  $("#fb-form").onsubmit = async (e) => {
    e.preventDefault();
    const err = $("#fb-error"); err.hidden = true;
    if (!st.cat) { err.textContent = "Pick an area first."; err.hidden = false; $("#s1").scrollIntoView({ behavior: "smooth", block: "center" }); return; }
    if (!st.rating) { err.textContent = "Choose a star rating."; err.hidden = false; $("#s2").scrollIntoView({ behavior: "smooth", block: "center" }); return; }
    const btn = $("#send"); btn.disabled = true; btn.textContent = "Sending...";
    try {
      await api("POST", "/api/feedback", { categoryId: st.cat.categoryId, rating: st.rating, comments: $("#comments").value, anonymous: st.anon });
      announce("Feedback sent. It is now listed under My feedback.");
      showUser("mine");
    } catch (ex) { err.textContent = ex.message; err.hidden = false; btn.disabled = false; btn.textContent = "Send feedback"; }
  };
  drawTiles(); sync();
}

async function renderMine(root) {
  root.innerHTML = skeleton(3);
  try {
    const list = await api("GET", "/api/feedback/mine");
    if (!list.length) {
      root.innerHTML = `<section class="panel empty"><h3>You have not given any feedback yet</h3><p>Your first report takes about a minute.</p><button class="btn" id="go-new" type="button">Give feedback</button></section>`;
      root.querySelector("#go-new").onclick = () => showUser("new");
      return;
    }
    root.innerHTML = `<ul class="feed"></ul>`;
    const feed = root.firstElementChild;
    list.forEach((f) => {
      const li = document.createElement("li");
      li.innerHTML = `<article class="fcard">
        <header class="row">${mono(f.categoryName)}
          <span class="grow"><h2 class="t"><button type="button" class="open">${esc(f.categoryName)}</button></h2><span class="s">${stars(f.rating)}&nbsp; ${fmt(f.createdAt)}</span></span>
          ${f.anonymous ? '<span class="badge badge-anon">Anonymous</span>' : ""}
        </header>
        <p class="txt">${esc(f.comments) || '<span class="muted">No comment</span>'}</p>
        ${track(f.status, "mini")}</article>`;
      li.querySelector(".open").onclick = () => showDetail(root, `/api/feedback/${f.feedbackId}`, false, () => renderMine(root));
      feed.appendChild(li);
    });
  } catch (ex) { root.innerHTML = `<p class="alert alert-error" role="alert">${esc(ex.message)}</p>`; }
}

/* ---------- detail (member: read only, admin: can act) ---------- */
async function showDetail(root, url, isAdmin, backTo) {
  root.innerHTML = skeleton(2);
  window.scrollTo(0, 0);
  try {
    const f = await api("GET", url);
    const replies = f.responses.length
      ? f.responses.map((r) => `<article class="bubble"><p class="by">${esc(r.adminName)}, ${fmt(r.createdAt)}</p><p>${esc(r.responseText)}</p></article>`).join("")
      : `<p class="waiting">${isAdmin ? "No reply yet. Write one below." : "No reply yet. Management will answer here."}</p>`;
    root.innerHTML = `
      <button class="btn btn-quiet back" id="back" type="button">Back</button>
      <article class="detail-card">
        <header class="detail-head">
          ${mono(f.categoryName)}
          <hgroup class="grow"><h2>${esc(f.categoryName)}</h2><p>${stars(f.rating)} &nbsp;<span class="muted">${fmt(f.createdAt)}</span></p></hgroup>
          ${f.anonymous ? '<span class="badge badge-anon">Anonymous</span>' : ""}
        </header>
        <section class="status-block" aria-label="Status">${track(f.status, "big")}</section>
        <blockquote class="quote">${esc(f.comments) || '<span class="muted">No comment</span>'}</blockquote>
        ${isAdmin ? `<p class="hint" style="margin-top:.8rem">From ${esc(f.submitterName)}${f.submitterRole ? ", " + esc(f.submitterRole.toLowerCase()) : ""}${f.submitterDepartment ? ", " + esc(f.submitterDepartment) : ""}</p>` : ""}
        <section class="cols">
          <section><h3>Replies</h3>${replies}
            ${isAdmin ? `<form id="reply-form" class="admin-actions"><label for="reply" style="margin-top:0">Reply to the sender</label><textarea id="reply" maxlength="2000" style="min-height:90px"></textarea><button class="btn" style="margin-top:.7rem" type="submit">Send reply</button></form>` : ""}
          </section>
          <section><h3>History</h3>
            <ul class="timeline">${f.history.map((h) => `<li><b>${h.oldStatus ? STATUS_LABEL[h.oldStatus] + " to " : ""}${STATUS_LABEL[h.newStatus]}</b>
              <p class="when">${fmt(h.changedAt)}, by ${esc(h.byWhom)}</p>${h.note ? `<p>${esc(h.note)}</p>` : ""}</li>`).join("")}</ul>
            ${isAdmin ? statusForm(f) : ""}
          </section>
        </section>
      </article>`;
    root.querySelector("#back").onclick = () => (backTo ? backTo() : renderMine(root));
    if (isAdmin) {
      root.querySelector("#reply-form").onsubmit = async (e) => {
        e.preventDefault();
        const text = root.querySelector("#reply").value.trim();
        if (!text) return toast("Write a reply first.");
        try { await api("POST", `/api/admin/feedback/${f.feedbackId}/responses`, { responseText: text }); announce("Reply sent."); showDetail(root, url, true, backTo); }
        catch (ex) { toast(ex.message); }
      };
      const sf = root.querySelector("#status-form");
      if (sf) sf.onsubmit = async (e) => {
        e.preventDefault();
        try {
          const next = sf.querySelector("select").value;
          await api("PATCH", `/api/admin/feedback/${f.feedbackId}/status`, { newStatus: next, note: sf.querySelector("input").value });
          announce(`Status changed to ${STATUS_LABEL[next]}.`); showDetail(root, url, true, backTo);
        } catch (ex) { toast(ex.message); }
      };
    }
  } catch (ex) { root.innerHTML = `<p class="alert alert-error" role="alert">${esc(ex.message)}</p>`; }
}

function statusForm(f) {
  const later = STATUS_ORDER.slice(STATUS_ORDER.indexOf(f.status) + 1);
  if (!later.length) return `<p class="muted admin-actions">This feedback is closed. Status cannot move back.</p>`;
  return `<form id="status-form" class="admin-actions"><label for="ns" style="margin-top:0">Change status to</label>
    <select id="ns">${later.map((s) => `<option value="${s}">${STATUS_LABEL[s]}</option>`).join("")}</select>
    <label for="note">Note <small>(optional)</small></label><input id="note" maxlength="255">
    <button class="btn" style="margin-top:.7rem" type="submit">Update status</button></form>`;
}

/* ====================================================================
   Management screens
   ==================================================================== */
function showAdmin(tab = "dash", filter = {}) {
  clearInterval(heroTimer);
  chrome();
  const { extra, body } = shell(greeting(),
    tab === "dash" ? "Where feedback stands today, and which areas need you first." : "Every feedback in one place. Open one to reply or change its status.",
    nav([["dash", "Dashboard"], ["all", "All feedback"]], tab, (t) => showAdmin(t)));
  tab === "dash" ? renderDashboard(body, extra) : renderAll(body, filter);
}

/** The attention bar appears only when action is needed: a category rated below this and feedback still waiting. */
const ATTENTION_BELOW = 2.5;

async function renderDashboard(root, rail) {
  root.innerHTML = skeleton(3);
  try {
    const d = await api("GET", "/api/admin/dashboard");
    const rated = d.categories.filter((c) => c.total > 0 && c.avgRating != null);
    const worst = rated.length ? rated[0] : null;      // already sorted worst first
    const attention = worst && worst.avgRating < ATTENTION_BELOW && d.pending > 0
      ? `<aside class="attention" aria-label="Needs attention"><p class="grow"><strong>${esc(worst.categoryName)} needs attention first</strong>Average rating ${worst.avgRating} from ${worst.total} feedback${worst.total > 1 ? "s" : ""}. ${d.pending} feedback${d.pending === 1 ? " is" : "s are"} still waiting on management.</p><button class="btn" id="see-worst" type="button">See ${esc(worst.categoryName)} feedback</button></aside>`
      : "";

    root.innerHTML = `
      ${attention}
      <ul class="pipeline" aria-label="Feedback by status">${STATUS_ORDER.map((s) => `<li><button type="button" class="stage st-${s}" data-s="${s}"><span class="n" data-to="${d.byStatus[s] ?? 0}">0</span><span class="l">${STATUS_LABEL[s]}</span></button></li>`).join("")}</ul>
      <section class="panel"><h2>Rating by category</h2><p>Lowest rated first. Click a row to see its feedback.</p>
        <div class="table-wrap"><table><thead><tr><th><span class="sr">Rank</span></th><th>Category</th><th class="num">Average</th><th><span class="sr">Bar</span></th><th class="num">Feedback</th></tr></thead><tbody>
        ${d.categories.map((c, i) => {
          const a = c.avgRating, cls = a == null ? "" : a < 2.5 ? "bad" : a < 3.5 ? "warn" : "";
          return `<tr class="click" tabindex="0" data-id="${c.categoryId}"><td class="rank">${i + 1}</td>
            <td><span class="catcell"><span class="e" aria-hidden="true">${esc(c.categoryName[0].toUpperCase())}</span>${esc(c.categoryName)}</span></td>
            <td class="num avg ${cls}">${a ?? "–"}</td>
            <td><span class="meter ${cls}"><span data-w="${(a || 0) * 20}" style="width:0"></span></span></td>
            <td class="num">${c.total}</td></tr>`;
        }).join("")}</tbody></table></div></section>`;
    if (rail) rail.innerHTML = kpiList([
      [0, "Total feedback", `data-to="${d.totalFeedback}"`],
      [0, "Average rating out of 5", `data-to="${d.averageRating ?? ""}" data-dec="2"`],
      [0, "Waiting on management", `data-to="${d.pending}"`]]);
    [root, rail].filter(Boolean).forEach((r) => r.querySelectorAll("[data-to]").forEach((el) => countUp(el, el.dataset.to === "" ? null : parseFloat(el.dataset.to), +(el.dataset.dec || 0))));
    requestAnimationFrame(() => root.querySelectorAll("[data-w]").forEach((el) => (el.style.width = el.dataset.w + "%")));
    root.querySelectorAll(".stage").forEach((b) => (b.onclick = () => showAdmin("all", { status: b.dataset.s })));
    root.querySelectorAll("tr.click").forEach((tr) => { const go = () => showAdmin("all", { categoryId: tr.dataset.id }); tr.onclick = go; tr.onkeydown = (e) => { if (e.key === "Enter") go(); }; });
    const sw = root.querySelector("#see-worst");
    if (sw) sw.onclick = () => showAdmin("all", { categoryId: String(worst.categoryId) });
  } catch (ex) { root.innerHTML = `<p class="alert alert-error" role="alert">${esc(ex.message)}</p>`; }
}

async function renderAll(root, filter = {}) {
  const f = { status: "", categoryId: "", pending: false, ...filter };
  root.innerHTML = `
    <section class="filters" aria-label="Filters">
      <fieldset><legend class="label">Status</legend><span class="chips" id="chips">
        <button type="button" class="chip" data-s="" aria-pressed="${!f.status}">All</button>
        ${STATUS_ORDER.map((s) => `<button type="button" class="chip" data-s="${s}" aria-pressed="${f.status === s}">${STATUS_LABEL[s]}</button>`).join("")}</span></fieldset>
      <p class="field"><label class="label" for="f-cat">Category</label><select id="f-cat"><option value="">All categories</option>${categories.map((c) => `<option value="${c.categoryId}">${esc(c.categoryName)}</option>`).join("")}</select></p>
      <fieldset><legend class="label">Sort</legend><button type="button" class="chip" id="f-pending" aria-pressed="${f.pending}">Waiting only, oldest first</button></fieldset>
    </section>
    <section class="panel" id="list">${skeleton(3)}</section>`;
  root.querySelector("#f-cat").value = f.categoryId;
  const reload = (patch) => renderAll(root, { ...f, ...patch });
  root.querySelectorAll("#chips .chip").forEach((c) => (c.onclick = () => reload({ status: c.dataset.s })));
  root.querySelector("#f-cat").onchange = (e) => reload({ categoryId: e.target.value });
  root.querySelector("#f-pending").onclick = () => reload({ pending: !f.pending });

  const qs = new URLSearchParams(); if (f.status) qs.set("status", f.status); if (f.categoryId) qs.set("categoryId", f.categoryId); if (f.pending) qs.set("pending", "true");
  try {
    const pg = await api("GET", "/api/admin/feedback?" + qs);
    const list = root.querySelector("#list");
    if (!pg.items.length) { list.innerHTML = `<section class="empty"><h3>Nothing matches these filters</h3><p>Try another status or category.</p><button class="btn btn-quiet" id="clear" type="button">Clear filters</button></section>`; list.querySelector("#clear").onclick = () => renderAll(root, {}); return; }
    list.innerHTML = `<div class="table-wrap"><table><thead><tr><th>#</th><th>From</th><th>Category</th><th>Rating</th><th>Status</th><th>Sent</th></tr></thead><tbody>
      ${pg.items.map((x) => `<tr class="click" tabindex="0" data-id="${x.feedbackId}"><td>${x.feedbackId}</td><td>${esc(x.submitterName)}</td>
        <td><span class="catcell"><span class="e" aria-hidden="true">${esc(x.categoryName[0].toUpperCase())}</span>${esc(x.categoryName)}</span></td><td>${stars(x.rating)}</td><td>${badge(x.status)}</td><td>${fmt(x.createdAt)}</td></tr>`).join("")}</tbody></table></div>
      <p class="hint" style="margin-top:.8rem">Showing ${pg.items.length} of ${pg.total}</p>`;
    list.querySelectorAll("tr.click").forEach((tr) => {
      const open = () => showDetail(root, `/api/admin/feedback/${tr.dataset.id}`, true, () => renderAll(root, f));
      tr.onclick = open; tr.onkeydown = (e) => { if (e.key === "Enter") open(); };
    });
  } catch (ex) { root.querySelector("#list").innerHTML = `<p class="alert alert-error" role="alert">${esc(ex.message)}</p>`; }
}

/* ====================================================================
   Alerts: the bell (in-app), contact settings (email and SMS), and browser notifications.
   The server sends email and SMS itself; this code only shows the bell and saves the contact details.
   ==================================================================== */
let alertTimer = null, lastUnread = null, alerts = { unread: 0, items: [] };

function paintBell() {
  const c = document.getElementById("bell-count");
  c.hidden = !alerts.unread; c.textContent = alerts.unread > 99 ? "99+" : alerts.unread;
  $bell.setAttribute("aria-label", alerts.unread ? `Alerts, ${alerts.unread} unread` : "Alerts");
}

async function pollAlerts() {
  try {
    const d = await api("GET", "/api/notifications");
    if (lastUnread !== null && d.unread > lastUnread) {
      const fresh = d.items.find((n) => !n.read);
      if (fresh) {
        announce(fresh.title);
        if ("Notification" in window && Notification.permission === "granted") {
          try { new Notification(fresh.title, { body: fresh.message }); } catch (_) { /* some phones only allow service-worker notifications */ }
        }
      }
    }
    lastUnread = d.unread; alerts = d; paintBell();
    if (!$panel.hidden) paintAlertList();
  } catch (_) { /* logged out or offline: try again on the next tick */ }
}
function startAlerts() { stopAlerts(); pollAlerts(); alertTimer = setInterval(pollAlerts, 30000); }
function stopAlerts() {
  clearInterval(alertTimer); alertTimer = null; lastUnread = null; alerts = { unread: 0, items: [] };
  $panel.hidden = true; $bell.setAttribute("aria-expanded", "false"); paintBell();
}

function paintAlertList() {
  const list = $panel.querySelector("#notif-list");
  if (!list) return;
  list.innerHTML = alerts.items.length ? alerts.items.map((n) =>
    `<li class="${n.read ? "" : "unread"}"><button type="button" class="notif-item" data-id="${n.notificationId}"><strong>${esc(n.title)}</strong><span>${esc(n.message)}</span><small>${fmt(n.createdAt)}${n.read ? "" : " (new)"}</small></button></li>`).join("")
    : `<li class="notif-empty">Nothing yet. You will see updates here.</li>`;
  list.querySelectorAll(".notif-item").forEach((b) => (b.onclick = async () => {
    try { alerts = await api("POST", `/api/notifications/${b.dataset.id}/read`); lastUnread = alerts.unread; paintBell(); } catch (_) {}
    closeAlerts(false);
    me.role === "ADMIN" ? showAdmin("all") : showUser("mine");
  }));
  const all = $panel.querySelector("#n-readall"); if (all) all.disabled = !alerts.unread;
}

function permissionText() {
  if (!("Notification" in window)) return "This browser cannot show pop-up alerts.";
  return { granted: "Pop-up alerts are on while this page is open.", denied: "Pop-up alerts are blocked in your browser settings.", default: "" }[Notification.permission];
}

async function openAlerts() {
  $panel.innerHTML = `
    <header class="notif-head"><h2>Alerts</h2><button type="button" class="btn btn-quiet" id="n-readall">Mark all read</button></header>
    <ul id="notif-list" class="notif-list"></ul>
    <form id="contact-form" novalidate>
      <h3>Where should we alert you?</h3>
      <p class="hint">Email and SMS are sent when your feedback changes or management replies. Leave a box empty to turn that channel off.</p>
      <label for="c-email">Email</label><input id="c-email" type="email" autocomplete="email" maxlength="150" placeholder="you@example.com">
      <label for="c-phone">Mobile number</label><input id="c-phone" type="tel" autocomplete="tel" maxlength="16" placeholder="+919876543210">
      <p class="hint">Start the number with + and the country code, no spaces.</p>
      <p id="c-msg" class="alert" role="status" hidden></p>
      <p class="notif-actions"><button class="btn" type="submit">Save</button> <button class="btn btn-quiet" type="button" id="n-perm">Allow pop-up alerts</button></p>
      <p class="hint" id="n-perm-note">${esc(permissionText())}</p>
    </form>`;
  $panel.hidden = false; $bell.setAttribute("aria-expanded", "true");
  paintAlertList();
  $panel.querySelector("#n-readall").onclick = async () => { try { alerts = await api("POST", "/api/notifications/read-all"); lastUnread = 0; paintBell(); paintAlertList(); } catch (_) {} };
  const perm = $panel.querySelector("#n-perm");
  perm.hidden = !("Notification" in window) || Notification.permission !== "default";
  perm.onclick = async () => { try { await Notification.requestPermission(); } catch (_) {} perm.hidden = true; $panel.querySelector("#n-perm-note").textContent = permissionText(); };
  const msg = $panel.querySelector("#c-msg");
  try { const c = await api("GET", "/api/me/contact"); $panel.querySelector("#c-email").value = c.email || ""; $panel.querySelector("#c-phone").value = c.phone || ""; } catch (_) {}
  $panel.querySelector("#contact-form").onsubmit = async (e) => {
    e.preventDefault(); msg.hidden = true;
    try {
      const c = await api("PUT", "/api/me/contact", { email: $panel.querySelector("#c-email").value.trim(), phone: $panel.querySelector("#c-phone").value.trim().replace(/[\s-]/g, "") });
      $panel.querySelector("#c-email").value = c.email || ""; $panel.querySelector("#c-phone").value = c.phone || "";
      msg.className = "alert alert-ok"; msg.textContent = "Saved."; msg.hidden = false;
    } catch (ex) { msg.className = "alert alert-error"; msg.textContent = ex.message; msg.hidden = false; }
  };
  pollAlerts();
}
function closeAlerts(refocus = true) { $panel.hidden = true; $bell.setAttribute("aria-expanded", "false"); if (refocus) $bell.focus(); }
$bell.onclick = () => ($panel.hidden ? openAlerts() : closeAlerts());
document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !$panel.hidden) closeAlerts(); });
document.addEventListener("click", (e) => { if (!$panel.hidden && !$panel.contains(e.target) && !$bell.contains(e.target)) closeAlerts(false); });

/* ---------- boot ---------- */
async function start() {
  try { if (!me) me = await api("GET", "/api/auth/me"); } catch (_) { me = null; }
  if (!me) return showAuth();
  try { categories = await api("GET", "/api/categories"); } catch (_) { categories = []; }
  me.role === "ADMIN" ? showAdmin() : showUser();
  startAlerts();
}
start();
