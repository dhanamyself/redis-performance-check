/* Shared renderer for both the live UI and the standalone HTML reports.
 * Defines window.renderResult(result, container). Depends only on the DOM and,
 * optionally, Chart.js (global `Chart`). If Chart is absent, charts are skipped
 * and tables still render. */
(function () {
  function n(v, d) { return (v === null || v === undefined || isNaN(v)) ? "-" : Number(v).toFixed(d === undefined ? 2 : d); }
  function int(v) { return (v === null || v === undefined) ? "-" : Number(v).toLocaleString(); }
  function bytes(b) {
    if (b === null || b === undefined) return "-";
    const u = ["B", "KB", "MB", "GB"]; let i = 0, x = Number(b);
    while (x >= 1024 && i < u.length - 1) { x /= 1024; i++; }
    return x.toFixed(x < 10 && i > 0 ? 1 : 0) + " " + u[i];
  }
  function uptime(s) {
    if (!s) return "-";
    const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
    return (d ? d + "d " : "") + h + "h " + m + "m";
  }
  function el(tag, cls, html) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html !== undefined) e.innerHTML = html;
    return e;
  }
  function statusBadge(s) {
    const c = s === "COMPLETED" ? "ok" : s === "FAILED" ? "bad" : "run";
    return '<span class="badge ' + c + '">' + s + "</span>";
  }

  function kpiCard(label, value, sub) {
    return '<div class="kpi"><div class="kpi-val">' + value + '</div><div class="kpi-label">' + label +
      '</div>' + (sub ? '<div class="kpi-sub">' + sub + '</div>' : '') + '</div>';
  }

  function table(headers, rows) {
    let h = '<table><thead><tr>';
    headers.forEach(x => h += '<th>' + x + '</th>');
    h += '</tr></thead><tbody>';
    rows.forEach(r => {
      h += '<tr>';
      r.forEach(c => h += '<td>' + c + '</td>');
      h += '</tr>';
    });
    return h + '</tbody></table>';
  }

  function section(title, bodyHtml) {
    const s = el("section", "card");
    s.appendChild(el("h2", null, title));
    const body = el("div");
    body.innerHTML = bodyHtml;
    s.appendChild(body);
    return s;
  }

  window.renderResult = function (result, container) {
    container.innerHTML = "";
    if (!result) {
      container.appendChild(el("p", "muted", "No run yet."));
      return;
    }

    // Tear down any prior charts (live re-render).
    if (container._charts) { container._charts.forEach(c => { try { c.destroy(); } catch (e) {} }); }
    container._charts = [];

    const payloads = result.payloadResults || [];

    // ---- Header ----
    const header = el("div", "run-header");
    header.innerHTML =
      '<h1>' + (result.label || "Redis Performance Run") + ' ' + statusBadge(result.status || "RUNNING") + '</h1>' +
      '<div class="meta">' +
      '<span><b>Run:</b> ' + (result.runId || "-") + '</span>' +
      '<span><b>Endpoint:</b> ' + (result.primaryEndpoint || "-") + (result.ssl ? ' (TLS)' : '') + '</span>' +
      '<span><b>Started:</b> ' + (result.startedAt || "-") + '</span>' +
      '<span><b>Finished:</b> ' + (result.finishedAt || "-") + '</span>' +
      '</div>' +
      (result.errorMessage ? '<div class="error-box">Error: ' + result.errorMessage + '</div>' : '');
    container.appendChild(header);

    // ---- KPI summary ----
    let bestTput = 0, worstP99 = 0;
    payloads.forEach(p => {
      bestTput = Math.max(bestTput, p.throughputOpsPerSec || 0);
      worstP99 = Math.max(worstP99, (p.overallLatency && p.overallLatency.p99Ms) || 0);
    });
    const av = result.availability;
    const rep = result.replication;
    const infoAfter = result.serverInfoAfter || result.serverInfoBefore || {};

    let kpis = "";
    kpis += kpiCard("Peak throughput", int(Math.round(bestTput)) + " ops/s", "across payload sizes");
    kpis += kpiCard("Worst p99 latency", n(worstP99) + " ms", "highest across sizes");
    if (av && av.enabled)
      kpis += kpiCard("Availability", n(av.availabilityPercent, 3) + " %", int(av.failedPings) + " / " + int(av.totalPings) + " pings failed");
    if (rep && rep.enabled)
      kpis += kpiCard("Replication lag", n(rep.meanLagMs) + " ms", "mean; p99 " + n(rep.p99LagMs) + " ms");
    kpis += kpiCard("Server uptime", uptime(infoAfter.uptimeSeconds), "redis " + (infoAfter.redisVersion || "?") + " / " + (infoAfter.role || "?"));
    if (av && av.enabled)
      kpis += kpiCard("Max outage", n(av.maxOutageMs, 0) + " ms", int(av.outageCount) + " outage window(s)");
    container.appendChild(section("KPI Summary", '<div class="kpi-grid">' + kpis + '</div>'));

    // ---- Payload sweep table ----
    if (payloads.length) {
      const rows = payloads.map(p => [
        p.payloadSizeKb + " KB",
        bytes(p.actualPayloadBytes),
        int(p.totalOps),
        int(Math.round(p.throughputOpsPerSec)),
        n(p.getLatency && p.getLatency.p50Ms), n(p.getLatency && p.getLatency.p95Ms), n(p.getLatency && p.getLatency.p99Ms),
        n(p.putLatency && p.putLatency.p50Ms), n(p.putLatency && p.putLatency.p95Ms), n(p.putLatency && p.putLatency.p99Ms),
        n(p.overallLatency && p.overallLatency.maxMs),
        int(p.errors)
      ]);
      container.appendChild(section("Throughput & Latency by Payload Size",
        table(["Size", "Bytes", "Total ops", "Ops/sec",
          "GET p50", "GET p95", "GET p99", "PUT p50", "PUT p95", "PUT p99", "Max", "Errors"], rows) +
        '<p class="muted">Latency in milliseconds. GET/PUT split by the configured read ratio.</p>'));

      // ---- Charts ----
      const chartWrap = el("div", "card");
      chartWrap.appendChild(el("h2", null, "Charts"));
      const grid = el("div", "chart-grid");
      const c1 = el("canvas"), c2 = el("canvas"), c3 = el("canvas");
      [c1, c2, c3].forEach(c => { const w = el("div", "chart-box"); w.appendChild(c); grid.appendChild(w); });
      chartWrap.appendChild(grid);
      container.appendChild(chartWrap);

      if (typeof Chart !== "undefined") {
        const labels = payloads.map(p => p.payloadSizeKb + "KB");
        container._charts.push(new Chart(c1, {
          type: "bar",
          data: { labels: labels, datasets: [{ label: "Throughput (ops/sec)", data: payloads.map(p => Math.round(p.throughputOpsPerSec)), backgroundColor: "#3b82f6" }] },
          options: chartOpts("Throughput vs Payload Size", "ops/sec")
        }));
        container._charts.push(new Chart(c2, {
          type: "line",
          data: {
            labels: labels, datasets: [
              ds("p50", payloads.map(p => p.overallLatency && p.overallLatency.p50Ms), "#22c55e"),
              ds("p95", payloads.map(p => p.overallLatency && p.overallLatency.p95Ms), "#f59e0b"),
              ds("p99", payloads.map(p => p.overallLatency && p.overallLatency.p99Ms), "#ef4444")
            ]
          },
          options: chartOpts("Latency Percentiles vs Payload Size", "ms")
        }));
        container._charts.push(new Chart(c3, {
          type: "bar",
          data: {
            labels: labels, datasets: [
              { label: "GET p99 (ms)", data: payloads.map(p => p.getLatency && p.getLatency.p99Ms), backgroundColor: "#0ea5e9" },
              { label: "PUT p99 (ms)", data: payloads.map(p => p.putLatency && p.putLatency.p99Ms), backgroundColor: "#a855f7" }
            ]
          },
          options: chartOpts("GET vs PUT p99 by Size", "ms")
        }));
      } else {
        grid.appendChild(el("p", "muted", "Chart.js not loaded — tables above contain all data."));
      }
    }

    // ---- Replication ----
    if (rep) {
      let body;
      if (rep.enabled) {
        body = table(["Replica endpoint", "Samples", "Timeouts", "Min", "Mean", "p50", "p95", "p99", "Max"],
          [[rep.replicaEndpoint || "-", int(rep.samples), int(rep.timeouts),
            n(rep.minLagMs), n(rep.meanLagMs), n(rep.p50LagMs), n(rep.p95LagMs), n(rep.p99LagMs), n(rep.maxLagMs)]]) +
          '<p class="muted">Lag in ms = time from primary write until the value is readable on the replica endpoint.' +
          (rep.note ? ' ' + rep.note : '') + '</p>';
      } else {
        body = '<p class="muted">' + (rep.note || "Replication probe disabled.") + '</p>';
      }
      container.appendChild(section("Geo-Replication Lag", body));
    }

    // ---- Availability ----
    if (av && av.enabled) {
      let body = table(["Total pings", "Failed", "Availability %", "Ping mean", "Ping p99", "Ping max", "Outages", "Max outage", "Total downtime"],
        [[int(av.totalPings), int(av.failedPings), n(av.availabilityPercent, 3),
          n(av.pingMeanMs), n(av.pingP99Ms), n(av.pingMaxMs),
          int(av.outageCount), n(av.maxOutageMs, 0) + " ms", n(av.totalDowntimeMs, 0) + " ms"]]);
      if (av.outages && av.outages.length) {
        body += '<h3>Outage windows</h3>' +
          table(["Started", "Duration (ms)"], av.outages.map(o => [o.startedAt, n(o.durationMs, 0)]));
      }
      container.appendChild(section("Availability / Uptime Probe", body));
    }

    // ---- Server INFO ----
    if (result.serverInfoBefore || result.serverInfoAfter) {
      const b = result.serverInfoBefore || {}, a = result.serverInfoAfter || {};
      const rows = [
        ["Redis version", b.redisVersion || "-", a.redisVersion || "-"],
        ["Role", b.role || "-", a.role || "-"],
        ["Mode", b.mode || "-", a.mode || "-"],
        ["Uptime", uptime(b.uptimeSeconds), uptime(a.uptimeSeconds)],
        ["Connected clients", int(b.connectedClients), int(a.connectedClients)],
        ["Used memory", b.usedMemoryHuman || "-", a.usedMemoryHuman || "-"],
        ["Mem fragmentation", n(b.memFragmentationRatio), n(a.memFragmentationRatio)],
        ["Total commands", int(b.totalCommandsProcessed), int(a.totalCommandsProcessed)],
        ["Keyspace hits", int(b.keyspaceHits), int(a.keyspaceHits)],
        ["Keyspace misses", int(b.keyspaceMisses), int(a.keyspaceMisses)],
        ["Evicted keys", int(b.evictedKeys), int(a.evictedKeys)],
        ["Expired keys", int(b.expiredKeys), int(a.expiredKeys)]
      ];
      container.appendChild(section("Server INFO (before / after run)",
        table(["Metric", "Before", "After"], rows)));
    }
  };

  function ds(label, data, color) {
    return { label: label, data: data, borderColor: color, backgroundColor: color, tension: 0.25, fill: false };
  }
  function chartOpts(title, yLabel) {
    return {
      responsive: true, maintainAspectRatio: false,
      plugins: { title: { display: true, text: title }, legend: { display: true } },
      scales: { y: { beginAtZero: true, title: { display: true, text: yLabel } } }
    };
  }
})();
