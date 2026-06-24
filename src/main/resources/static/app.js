/* Front-end controller: gathers config, calls the REST API, polls live status,
 * and renders results via the shared renderResult(). */
(function () {
  const $ = id => document.getElementById(id);
  let pollTimer = null;

  function gatherConfig() {
    const sizes = new Set();
    document.querySelectorAll("#payloadChecks input:checked").forEach(c => sizes.add(parseInt(c.value)));
    ($("customSizes").value || "").split(",").forEach(s => {
      const v = parseInt(s.trim());
      if (!isNaN(v) && v > 0) sizes.add(v);
    });
    const payloadSizesKb = Array.from(sizes).sort((a, b) => a - b);

    return {
      primaryHost: $("primaryHost").value.trim(),
      primaryPort: int($("primaryPort"), 10000),
      password: $("password").value,
      useSsl: $("useSsl").checked,
      connectTimeoutMs: int($("connectTimeoutMs"), 10000),
      replicaHost: $("replicaHost").value.trim(),
      replicaPort: int($("replicaPort"), 10000),
      replicaPassword: $("replicaPassword").value,
      durationSeconds: int($("durationSeconds"), 30),
      warmupSeconds: int($("warmupSeconds"), 5),
      concurrency: int($("concurrency"), 16),
      readRatioPercent: int($("readRatioPercent"), 80),
      keyspaceSize: int($("keyspaceSize"), 10000),
      sessionTtlSeconds: int($("sessionTtlSeconds"), 1800),
      payloadSizesKb: payloadSizesKb.length ? payloadSizesKb : [1],
      availabilityProbeEnabled: $("availabilityProbeEnabled").checked,
      availabilityProbeIntervalMs: int($("availabilityProbeIntervalMs"), 500),
      replicationProbeEnabled: $("replicationProbeEnabled").checked,
      replicationProbeIntervalMs: int($("replicationProbeIntervalMs"), 1000),
      replicationTimeoutMs: int($("replicationTimeoutMs"), 5000),
      label: $("label").value.trim() || "redis-perf-run"
    };
  }

  function int(elm, dflt) { const v = parseInt(elm.value); return isNaN(v) ? dflt : v; }

  async function postJson(url, body) {
    const r = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    return { ok: r.ok, status: r.status, data: await r.json().catch(() => ({})) };
  }

  // ---- Test connection ----
  $("btnTest").addEventListener("click", async () => {
    const box = $("connResult");
    box.className = "conn-result";
    box.style.display = "block";
    box.textContent = "Connecting…";
    box.className = "conn-result ok";
    try {
      const { data } = await postJson("/api/test-connection", gatherConfig());
      if (data.ok) {
        let msg = "✓ Connected to " + data.endpoint + " — Redis " + data.redisVersion +
          " (" + data.role + "), uptime " + Math.round(data.uptimeSeconds / 3600) + "h, " +
          data.connectedClients + " clients, mem " + data.usedMemory;
        if (data.replicaOk === true) msg += "\n✓ Replica reachable: " + data.replicaEndpoint;
        if (data.replicaOk === false) msg += "\n✗ Replica error: " + data.replicaError;
        box.className = "conn-result ok";
        box.textContent = msg;
      } else {
        box.className = "conn-result bad";
        box.textContent = "✗ " + (data.error || "connection failed");
      }
    } catch (e) {
      box.className = "conn-result bad";
      box.textContent = "✗ " + e.message;
    }
  });

  // ---- Start test ----
  $("cfgForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const cfg = gatherConfig();
    if (!cfg.primaryHost) { alert("Primary host is required."); return; }
    $("btnStart").disabled = true;
    $("btnStart").textContent = "Running…";
    const { ok, status, data } = await postJson("/api/start", cfg);
    if (!ok) {
      alert("Could not start: " + (data.error || status));
      resetStartButton();
      return;
    }
    startPolling();
  });

  function resetStartButton() {
    $("btnStart").disabled = false;
    $("btnStart").textContent = "▶ Start performance test";
  }

  function startPolling() {
    if (pollTimer) clearInterval(pollTimer);
    poll();
    pollTimer = setInterval(poll, 1500);
  }

  async function poll() {
    try {
      const r = await fetch("/api/status");
      if (!r.ok) return;
      const result = await r.json();
      if (!result) return;
      renderLiveStatus(result);
      window.renderResult(result, $("results"));
      if (result.status === "COMPLETED" || result.status === "FAILED") {
        clearInterval(pollTimer);
        pollTimer = null;
        resetStartButton();
        loadReports();
      }
    } catch (e) { /* keep polling */ }
  }

  function renderLiveStatus(result) {
    const totalSizes = (result.config && result.config.payloadSizesKb ? result.config.payloadSizesKb.length : 1);
    const done = (result.payloadResults || []).length;
    const pct = result.status === "COMPLETED" ? 100 : Math.round((done / Math.max(1, totalSizes)) * 100);
    $("liveStatus").innerHTML =
      '<div class="card"><h2>Live status: ' + result.status +
      (result.status === "RUNNING" ? ' — payload ' + (done + 1) + ' / ' + totalSizes : '') + '</h2>' +
      '<div class="progress"><div style="width:' + pct + '%"></div></div>' +
      (result.reportFileName ?
        '<p>Report saved: <a href="/api/reports/' + result.reportFileName + '" target="_blank">' + result.reportFileName + '</a></p>' : '') +
      '</div>';
  }

  // ---- Reports list ----
  async function loadReports() {
    try {
      const r = await fetch("/api/reports");
      const list = await r.json();
      const ul = $("reportsList");
      if (!list.length) { ul.innerHTML = '<li class="muted">none yet</li>'; return; }
      ul.innerHTML = list.map(f =>
        '<li><a href="/api/reports/' + f.name + '" target="_blank">' + f.name + '</a> ' +
        '<span class="muted">(' + (f.size / 1024).toFixed(0) + ' KB)</span></li>').join("");
    } catch (e) { /* ignore */ }
  }

  loadReports();
})();
