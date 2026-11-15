DASHBOARD_HTML = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>Pleos 차량이 만드는 데이터 · 트루파킹</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  :root { --bg:#F7F8FA; --surface:#FFFFFF; --high:#EDEEF2; --text:#131417; --muted:rgba(19,20,23,.64); --faint:rgba(19,20,23,.32);
          --active:#02C265; --positive:#0064FF; --negative:#FE3D16; }
  * { box-sizing:border-box; }
  html, body { height:100%; }
  body { margin:0; font-family:"Asta Sans","Pretendard","Apple SD Gothic Neo",sans-serif; font-size:16px; background:var(--bg); color:var(--text);
         display:grid; grid-template-rows:auto 384px auto minmax(0,1fr) minmax(0,1.45fr); gap:14px; padding:14px 20px 16px; overflow:hidden; }
  header { display:flex; align-items:center; gap:10px; }
  h1 { margin:0; font-size:23px; flex:none; }
  #status { flex:1; min-width:0; font-size:14px; color:var(--muted); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; text-align:right; }
  button { font:inherit; font-weight:700; font-size:15px; border:0; border-radius:14px; padding:10px 16px; background:#44464E; color:#fff; cursor:pointer; flex:none; }
  button:disabled { background:var(--high); color:var(--faint); cursor:default; }
  button.run:disabled { background:var(--positive); color:#fff; }
  section { position:relative; background:var(--surface); border-radius:20px; padding:14px 18px; box-shadow:0 6px 24px rgba(0,0,0,.08); min-height:0; display:flex; flex-direction:column; }
  section.flow::before { content:''; position:absolute; top:-12px; left:50%; margin-left:-8px; border:8px solid transparent; border-top:9px solid #C9CBD2; border-bottom:0; }
  section.pulse { animation:glow 1.3s ease-out; }
  @keyframes glow { from { box-shadow:0 0 0 5px rgba(2,194,101,.6), 0 6px 24px rgba(0,0,0,.08); } to { box-shadow:0 0 0 0 rgba(2,194,101,0), 0 6px 24px rgba(0,0,0,.08); } }
  .head { display:flex; align-items:center; gap:10px; margin-bottom:10px; }
  .step { width:26px; height:26px; border-radius:50%; background:var(--positive); color:#fff; display:inline-flex; align-items:center; justify-content:center; font-weight:800; font-size:15px; flex:none; }
  h2 { margin:0; font-size:20px; flex:none; }
  .tag { margin-left:auto; font-size:14px; font-weight:700; padding:5px 12px; border-radius:999px; background:rgba(254,61,22,.1); color:var(--negative); white-space:nowrap; }
  .tag.ok { background:rgba(2,194,101,.12); color:#018a48; }
  .muted { color:var(--muted); font-size:15px; }
  .head .muted { min-width:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  canvas { width:100%; height:100%; border-radius:14px; display:block; min-height:0; }

  .trio { display:grid; grid-template-columns:252px minmax(0,1fr) 262px; gap:16px; flex:1; min-height:0; }
  .cabin { display:flex; gap:12px; align-items:center; min-height:0; transition:opacity .3s; }
  .cabin svg { height:100%; width:156px; flex:none; }
  .body { fill:#fff; stroke:#C9CBD2; stroke-width:3; }
  .glass { fill:#E3E5EA; }
  .wheel { fill:none; stroke:#686A72; stroke-width:2.5; }
  .seat { fill:#fff; stroke:#C9CBD2; stroke-width:2; transition:fill .3s, stroke .3s; }
  .seat.on { fill:var(--positive); stroke:var(--positive); }
  .door { stroke:#A4A8AE; stroke-width:5; stroke-linecap:round; transition:transform .45s ease, stroke .3s; }
  .door.open { stroke:var(--negative); transform:rotate(42deg); }
  .door.right.open { transform:rotate(-42deg); }
  .dash { display:flex; flex-direction:column; gap:8px; flex:1; }
  .gears { display:flex; flex-direction:column; gap:6px; }
  .gears span { text-align:center; font-weight:800; font-size:18px; padding:7px 0; border-radius:12px; background:var(--bg); color:var(--faint); transition:background .25s, color .25s; }
  .gears span.on { background:var(--text); color:#fff; }
  .speed { text-align:center; margin-top:4px; }
  .speed b { font-size:34px; line-height:1; display:block; font-variant-numeric:tabular-nums; }
  .speed span { font-size:13px; color:var(--muted); }
  .legend { font-size:12px; color:var(--muted); text-align:center; line-height:1.6; }
  .legend i { display:inline-block; width:10px; height:10px; border-radius:3px; margin-right:4px; background:var(--positive); }
  .legend i.open { background:var(--negative); }

  .status { display:flex; flex-direction:column; min-height:0; transition:opacity .3s; }
  .phase { display:flex; align-items:baseline; justify-content:space-between; gap:8px; font-size:24px; font-weight:800; line-height:1.25; white-space:nowrap; }
  #phaseTime { color:var(--positive); font-variant-numeric:tabular-nums; }
  #phaseSub { min-height:22px; font-size:14px; color:var(--muted); margin:2px 0 8px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  ol { list-style:none; margin:0; padding:0; display:flex; flex-direction:column; gap:3px; }
  li { display:flex; align-items:center; gap:10px; padding:6px 10px; border-radius:12px; color:var(--faint); font-weight:700; font-size:16px; transition:background .25s, color .25s; }
  li::before { content:''; width:16px; height:16px; border-radius:50%; border:3px solid var(--high); flex:none; }
  li.done { color:var(--muted); }
  li.done::before { background:var(--active); border-color:var(--active); }
  li.on { background:rgba(0,100,255,.08); color:var(--text); }
  li.on::before { border-color:var(--positive); border-top-color:transparent; animation:spin .9s linear infinite; }
  li.on.still::before { animation:none; background:var(--positive); border-color:var(--positive); }
  @keyframes spin { to { transform:rotate(360deg); } }
  #pickupChip { margin-top:auto; padding:8px 12px; border-radius:12px; background:var(--bg); font-size:15px; font-weight:700; color:var(--muted); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  #pickupChip.on { background:rgba(0,100,255,.1); color:var(--positive); }
  .offline .cabin, .offline ol, .offline #pickupChip { opacity:.3; }

  .cards { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; }
  .event { background:var(--bg); border-radius:14px; padding:10px 14px; font-size:15px; border:2px solid transparent; }
  .event.new { border-color:var(--active); }
  .event.flash { animation:flash 2.4s ease-out, drop .5s ease-out; }
  .event h3 { margin:0 0 5px; font-size:17px; }
  .event div { display:flex; justify-content:space-between; gap:8px; line-height:1.45; white-space:nowrap; }
  .event span { color:var(--muted); }
  @keyframes flash { from { background:rgba(2,194,101,.3); } to { background:var(--bg); } }
  @keyframes drop { from { transform:translateY(-18px); opacity:0; } to { transform:none; opacity:1; } }

  .split { display:grid; grid-template-columns:minmax(0,.85fr) minmax(0,1.15fr); gap:16px; flex:1; min-height:0; }
  .stats { display:flex; gap:28px; }
  .big { font-size:34px; font-weight:800; line-height:1.15; font-variant-numeric:tabular-nums; }
  .delta { color:#018a48; font-weight:700; font-size:15px; margin-left:8px; }
  .row { display:flex; justify-content:space-between; align-items:baseline; gap:10px; padding:5px 0; border-top:1px solid var(--high); font-size:15px; white-space:nowrap; }
  .row > span:first-child { min-width:0; overflow:hidden; text-overflow:ellipsis; }
  .row b { font-size:18px; }
</style>
</head>
<body class="offline">
<header>
  <h1>Pleos 차량이 만드는 데이터</h1>
  <span id="status"></span>
  <button data-s="parking">주차 시나리오</button><button data-s="pickup">픽업 시나리오</button><button data-s="hotspot">혼잡 지점</button>
</header>
<section id="s1">
  <div class="head"><span class="step">1</span><h2>차량 안에서 일어나는 일</h2><span class="tag">차 안에서만 처리 · 서버로 보내지 않음</span></div>
  <div class="trio">
    <div class="cabin">
      <svg viewBox="-8 0 176 270">
        <rect class="body" x="34" y="8" width="92" height="254" rx="34"/>
        <rect class="glass" x="46" y="50" width="68" height="26" rx="10"/>
        <rect class="glass" x="46" y="216" width="68" height="20" rx="8"/>
        <rect class="seat" id="seat-DRIVER" x="44" y="92" width="32" height="42" rx="9"/>
        <rect class="seat" id="seat-PASSENGER" x="84" y="92" width="32" height="42" rx="9"/>
        <rect class="seat" id="seat-REAR_LEFT" x="44" y="156" width="32" height="42" rx="9"/>
        <rect class="seat" id="seat-REAR_RIGHT" x="84" y="156" width="32" height="42" rx="9"/>
        <circle class="wheel" cx="60" cy="86" r="7"/>
        <line class="door" id="door-DRIVER" x1="34" y1="88" x2="34" y2="140" style="transform-origin:34px 88px"/>
        <line class="door right" id="door-PASSENGER" x1="126" y1="88" x2="126" y2="140" style="transform-origin:126px 88px"/>
        <line class="door" id="door-REAR_LEFT" x1="34" y1="150" x2="34" y2="202" style="transform-origin:34px 150px"/>
        <line class="door right" id="door-REAR_RIGHT" x1="126" y1="150" x2="126" y2="202" style="transform-origin:126px 150px"/>
      </svg>
      <div class="dash">
        <div class="gears"><span data-g="P">P</span><span data-g="R">R</span><span data-g="N">N</span><span data-g="D">D</span></div>
        <div class="speed"><b id="speed">-</b><span>km/h</span></div>
        <div class="legend"><i></i>착석<br><i class="open"></i>문 열림</div>
      </div>
    </div>
    <canvas id="live"></canvas>
    <div class="status">
      <div class="phase"><span id="phaseTitle">차량 앱 연결 대기 중</span><span id="phaseTime"></span></div>
      <div id="phaseSub">디버그 빌드 앱을 실행하면 표시됩니다</div>
      <ol id="steps"><li>주행 중</li><li>주차장 진입 확인</li><li>자리 찾는 중</li><li>주차 완료</li><li>출차 중</li></ol>
      <div id="pickupChip">승하차 · -</div>
    </div>
  </div>
</section>
<section id="s2" class="flow">
  <div class="head"><span class="step">2</span><h2>차량이 서버로 보낸 것</h2><span class="tag ok">익명 5개 항목뿐 · 경로, 차량 ID, 정확한 시각 없음</span></div>
  <div class="cards" id="events"></div>
</section>
<section id="s3" class="flow">
  <div class="head"><span class="step">3</span><h2>서버가 모은 것</h2><span class="muted" id="profileTitle"></span></div>
  <div class="split">
    <div>
      <div class="stats">
        <div><div class="muted">이 시간대 기록</div><div><span class="big" id="samples">-</span><span class="delta" id="samplesDelta"></span></div></div>
        <div><div class="muted">이 시간대 예측</div><div class="big" id="nowMinutes">-</div></div>
      </div>
      <div class="muted" style="margin-top:10px">구역 × 평일/주말 × 15분 단위로만 모읍니다. 진한 막대는 차량 기록, 옅은 막대는 기록이 부족해 기본값을 쓰는 시간대입니다.</div>
    </div>
    <canvas id="chart"></canvas>
  </div>
</section>
<section id="s4" class="flow">
  <div class="head"><span class="step">4</span><h2>다음 차가 받는 것</h2><span class="muted">예측 시간 · 승하차 혼잡 지점(지자체 제공)</span></div>
  <div class="split">
    <div id="predictions" style="overflow:hidden"></div>
    <canvas id="map"></canvas>
  </div>
</section>
<script>
const $ = id => document.getElementById(id);
const get = u => fetch(u).then(r => r.ok ? r.json() : null).catch(() => null);
const EVENT = { park_enter:'주차장 진입', park_complete:'주차 완료', park_fail:'주차 실패', exit_complete:'출차 완료', pickup_complete:'픽업 완료', dropoff_complete:'하차 완료', boarding_unknown:'승하차' };
const KIND = { park_complete:'park', park_enter:'park', pickup_complete:'pickup', dropoff_complete:'pickup', boarding_unknown:'pickup' };
const KIND_LABEL = { park:'자리 찾기 시간', exit:'출차 시간', pickup:'승하차 소요 시간' };
const DAY = ['', '월', '화', '수', '목', '금', '토', '일'];
const SEATS = ['DRIVER', 'PASSENGER', 'REAR_LEFT', 'REAR_RIGHT'];
const PHASES = ['idle', 'entering', 'inside', 'parked', 'exiting'];
const PHASE_TITLE = ['주행 중', '주차장 진입', '자리 찾는 중', '주차 완료', '출차 중'];
const COLOR = { parking:'#686A72', pickup_official:'#0064FF', pickup_hotspot:'#FE3D16' };
const M_PER_UNIT = 31.79e6;
let zones = [], names = {}, roads = [], tele = null, seen = null, focus = { zoneId:'lot-gangnam-a', kind:'park' }, lastSamples = null, lastPred = {}, lastFrame = 0;
const freshUntil = new Map();
const live = { from:null, to:null, at:0, heading:0, trail:[], cx:null, cy:null, span:360 };
const mx = lon => (lon + 180) / 360, my = lat => { const s = Math.sin(lat * Math.PI / 180); return 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI); };
const mmss = s => Math.floor(s / 60) + ':' + String(Math.floor(s % 60)).padStart(2, '0');
const bucket = b => { const f = m => String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0'); return f(b * 15) + '~' + f((b * 15 + 15) % 1440); };
const zoneLabel = z => z.type === 'pickup_hotspot' ? '혼잡 지점' : z.type === 'pickup_official' ? '승하차구역' : z.name;

function fit(canvas) {
  const r = canvas.getBoundingClientRect(), d = devicePixelRatio || 1, w = Math.round(r.width * d), h = Math.round(r.height * d);
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  const c = canvas.getContext('2d'); c.setTransform(d, 0, 0, d, 0, 0);
  return [c, r.width, r.height];
}

function drawRoads(c, X, Y, pxPerM, view) {
  c.strokeStyle = '#fff'; c.lineCap = c.lineJoin = 'round';
  for (const r of roads) {
    if (r.box[0] > view[2] || r.box[2] < view[0] || r.box[1] > view[3] || r.box[3] < view[1]) continue;
    c.lineWidth = Math.max(1.2, [16, 14, 11, 8, 6, 3.5][r.r] * pxPerM); c.beginPath();
    for (let i = 0; i < r.m.length; i += 2) { if (i) c.lineTo(X(r.m[i]), Y(r.m[i + 1])); else c.moveTo(X(r.m[i]), Y(r.m[i + 1])); }
    c.stroke();
  }
}

function placeLabels(c, items) {
  const hit = (a, b) => a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3], placed = items.map(i => i.box);
  for (const it of items) {
    const b = it.box, tw = c.measureText(it.text).width / 2 + 3, midX = (b[0] + b[2]) / 2, midY = (b[1] + b[3]) / 2;
    const spot = [[midX, b[1] - 6], [midX, b[3] + 17], [b[2] + tw + 5, midY + 5], [b[0] - tw - 5, midY + 5], [midX, b[1] - 24], [midX, b[3] + 35]]
      .map(([x, y]) => [x - tw, y - 13, x + tw, y + 4]).find(r => !placed.some(q => hit(r, q)));
    if (!spot) continue;
    placed.push(spot); c.fillStyle = it.color; c.fillText(it.text, (spot[0] + spot[2]) / 2, spot[3] - 4);
  }
}

function drawZones(c, X, Y, list, activeId) {
  c.font = '600 13px sans-serif'; c.textAlign = 'center';
  placeLabels(c, list.map(z => {
    const pts = z.polygon.map(p => [X(mx(p[1])), Y(my(p[0]))]), on = z.id === activeId, xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
    c.beginPath(); pts.forEach((p, i) => { if (i) c.lineTo(p[0], p[1]); else c.moveTo(p[0], p[1]); }); c.closePath();
    c.fillStyle = COLOR[z.type] + (on ? '5c' : '33'); c.fill(); c.strokeStyle = COLOR[z.type]; c.lineWidth = on ? 4 : 2; c.stroke();
    return { text:zoneLabel(z), color:COLOR[z.type], box:[Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)] };
  }));
}

function livePos(now) {
  const t = Math.min(1, (now - live.at) / 1000);
  return [live.from[0] + (live.to[0] - live.from[0]) * t, live.from[1] + (live.to[1] - live.from[1]) * t];
}

function onLocation(lat, lon) {
  const p = [mx(lon), my(lat)], now = performance.now();
  if (!live.to) { live.from = live.to = p; live.at = now; return; }
  if (p[0] === live.to[0] && p[1] === live.to[1]) return;
  const cur = livePos(now);
  if (Math.hypot(p[0] - live.to[0], p[1] - live.to[1]) * M_PER_UNIT > 400) { live.trail = []; live.from = p; live.cx = null; }
  else { live.from = cur; live.heading = Math.atan2(p[1] - cur[1], p[0] - cur[0]); }
  live.to = p; live.at = now;
}

function bubbleText(v) {
  if (v.parkingPhase === 'entering') return '주차장 진입 확인 중';
  if (v.parkingPhase === 'inside') return '자리 찾는 중 ' + mmss(v.parkingElapsedSec ?? 0);
  if (v.parkingPhase === 'parked') return '주차 완료';
  if (v.parkingPhase === 'exiting') return '출차 중 ' + mmss(v.parkingElapsedSec ?? 0);
  return v.pickupStopped ? '정차 중 · 승하차 확인' : '';
}

function drawLive(now) {
  const [c, w, h] = fit($('live'));
  c.fillStyle = '#EDEEF2'; c.fillRect(0, 0, w, h);
  const pos = live.to ? livePos(now) : null;
  const zone = tele && tele.parkingZoneId ? zones.find(z => z.id === tele.parkingZoneId) : null;
  const target = zone ? [mx(zone.center[1]), my(zone.center[0])] : pos || [mx(127.0276), my(37.4979)], span = zone ? 190 : 360;
  if (live.cx === null) { live.cx = target[0]; live.cy = target[1]; live.span = span; }
  live.cx += (target[0] - live.cx) * 0.08; live.cy += (target[1] - live.cy) * 0.08; live.span += (span - live.span) * 0.06;
  const k = w * M_PER_UNIT / live.span, X = x => (x - live.cx) * k + w / 2, Y = y => (y - live.cy) * k + h / 2;
  const view = [live.cx - w / 2 / k, live.cy - h / 2 / k, live.cx + w / 2 / k, live.cy + h / 2 / k];
  drawRoads(c, X, Y, w / live.span, view);
  drawZones(c, X, Y, zones.filter(z => { const x = mx(z.center[1]), y = my(z.center[0]); return x > view[0] && x < view[2] && y > view[1] && y < view[3]; }), zone ? zone.id : null);
  if (!pos || !tele) { c.fillStyle = 'rgba(19,20,23,.64)'; c.font = '600 15px sans-serif'; c.textAlign = 'center'; c.fillText('차량 위치 대기 중', w / 2, h / 2); return; }
  const last = live.trail[live.trail.length - 1];
  if (!last || Math.hypot(pos[0] - last[0], pos[1] - last[1]) * M_PER_UNIT > 0.7) live.trail.push([pos[0], pos[1], now]);
  while (live.trail.length && now - live.trail[0][2] > 90000) live.trail.shift();
  c.lineWidth = 4; c.lineCap = 'round';
  for (let i = 1; i < live.trail.length; i++) {
    const a = live.trail[i - 1], b = live.trail[i];
    c.strokeStyle = 'rgba(0,100,255,' + Math.max(0, 0.75 * (1 - (now - b[2]) / 90000)).toFixed(3) + ')';
    c.beginPath(); c.moveTo(X(a[0]), Y(a[1])); c.lineTo(X(b[0]), Y(b[1])); c.stroke();
  }
  const x = X(pos[0]), y = Y(pos[1]), parked = tele.parkingPhase === 'parked', busy = ['entering', 'inside', 'exiting'].includes(tele.parkingPhase) || tele.pickupStopped;
  if (busy) for (const shift of [0, 0.5]) {
    const f = (now / 1500 + shift) % 1;
    c.beginPath(); c.arc(x, y, 14 + 30 * f, 0, 7); c.strokeStyle = 'rgba(0,100,255,' + (0.7 * (1 - f)).toFixed(3) + ')'; c.lineWidth = 3; c.stroke();
  }
  c.beginPath(); c.arc(x, y, 15, 0, 7); c.fillStyle = '#fff'; c.fill();
  c.beginPath(); c.arc(x, y, 12, 0, 7); c.fillStyle = parked ? '#02C265' : '#0064FF'; c.fill();
  c.fillStyle = '#fff';
  if (parked) { c.font = '800 15px sans-serif'; c.textAlign = 'center'; c.fillText('P', x, y + 5); }
  else { c.save(); c.translate(x, y); c.rotate(live.heading); c.beginPath(); c.moveTo(8, 0); c.lineTo(-5, -6); c.lineTo(-2, 0); c.lineTo(-5, 6); c.closePath(); c.fill(); c.restore(); }
  const text = bubbleText(tele);
  if (text) {
    c.font = '700 14px sans-serif'; c.textAlign = 'center';
    const bw = c.measureText(text).width + 24, bx = Math.min(Math.max(x - bw / 2, 6), w - bw - 6), by = y - 70 < 6 ? y + 24 : y - 70;
    c.fillStyle = '#131417'; c.beginPath(); c.roundRect(bx, by, bw, 30, 15); c.fill();
    c.fillStyle = '#fff'; c.fillText(text, bx + bw / 2, by + 20);
  }
}

function drawMap() {
  const [c, w, h] = fit($('map'));
  c.fillStyle = '#EDEEF2'; c.fillRect(0, 0, w, h);
  const east = live.to ? live.to[0] > mx(127.04) : true, area = zones.filter(z => (z.center[1] > 127.04) === east);
  if (!area.length) return;
  const xs = area.map(z => mx(z.center[1])), ys = area.map(z => my(z.center[0]));
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2, cy = (Math.min(...ys) + Math.max(...ys)) / 2;
  const k = Math.min(w / (Math.max(Math.max(...xs) - Math.min(...xs), 1.6e-5) * 1.7), h / (Math.max(Math.max(...ys) - Math.min(...ys), 1.6e-5) * 2));
  const X = x => (x - cx) * k + w / 2, Y = y => (y - cy) * k + h / 2;
  drawRoads(c, X, Y, k / M_PER_UNIT, [cx - w / 2 / k, cy - h / 2 / k, cx + w / 2 / k, cy + h / 2 / k]);
  drawZones(c, X, Y, area, null);
  if (live.to) { const x = X(live.to[0]), y = Y(live.to[1]); c.beginPath(); c.arc(x, y, 9, 0, 7); c.fillStyle = '#fff'; c.fill(); c.beginPath(); c.arc(x, y, 6, 0, 7); c.fillStyle = '#0064FF'; c.fill(); }
}

function drawChart(p) {
  const [c, w, h] = fit($('chart'));
  c.clearRect(0, 0, w, h);
  if (!p || !p.hours.length) return;
  const max = Math.max(...p.hours.map(x => x.minutes), 1) * 1.3, bw = w / 24, base = h - 22;
  c.textAlign = 'center';
  p.hours.forEach((x, i) => {
    const bh = (base - 18) * x.minutes / max, now = i === p.currentHour;
    c.fillStyle = now ? '#0064FF' : x.stage === 'prior' ? '#E0E1E6' : '#A4A8AE'; c.fillRect(i * bw + 2, base - bh, bw - 4, bh);
    c.font = '13px sans-serif'; c.fillStyle = 'rgba(19,20,23,.64)'; if (i % 3 === 0) c.fillText(i + '시', i * bw + bw / 2, h - 5);
    if (now) { c.fillStyle = '#0064FF'; c.font = '700 14px sans-serif'; c.fillText(x.minutes.toFixed(1) + '분', Math.min(Math.max(i * bw + bw / 2, 24), w - 24), base - bh - 5); }
  });
}

function pulse() {
  ['s2', 's3', 's4'].forEach((id, i) => setTimeout(() => { const el = $(id); el.classList.remove('pulse'); void el.offsetWidth; el.classList.add('pulse'); }, i * 450));
}

function showVehicle(v) {
  document.body.classList.toggle('offline', !v);
  if (!v) { $('phaseTitle').textContent = '차량 앱 연결 대기 중'; $('phaseTime').textContent = ''; $('phaseSub').textContent = '디버그 빌드 앱을 실행하면 표시됩니다'; return; }
  SEATS.forEach(k => { $('seat-' + k).classList.toggle('on', v.seats[k] === true); $('door-' + k).classList.toggle('open', v.doors[k] === true); });
  document.querySelectorAll('.gears span').forEach(g => g.classList.toggle('on', g.dataset.g === v.gear));
  $('speed').textContent = Math.round(v.speedKph ?? 0);
  const i = Math.max(PHASES.indexOf(v.parkingPhase), 0), moving = (v.speedKph ?? 0) >= 1, usual = lastPred[v.parkingZoneId + 'park'];
  $('phaseTitle').textContent = i > 0 ? PHASE_TITLE[i] : v.pickupStopped ? '승하차 정차 중' : moving ? '주행 중' : '대기 중';
  $('phaseTime').textContent = v.parkingElapsedSec != null ? mmss(v.parkingElapsedSec) : '';
  $('phaseSub').textContent = v.parkingZoneId ? (names[v.parkingZoneId] || '') + (i === 2 && usual ? ' · 이 시간대엔 보통 ' + usual.m + '분' : '') : '';
  document.querySelectorAll('#steps li').forEach((li, n) => { if (n === 0) li.textContent = i > 0 ? PHASE_TITLE[0] : $('phaseTitle').textContent; li.className = n < i ? 'done' : n === i ? 'on' + (n === 3 || (n === 0 && !moving) ? ' still' : '') : ''; });
  $('pickupChip').textContent = '승하차 · ' + v.pickup; $('pickupChip').classList.toggle('on', v.pickupStopped === true);
}

async function slow() {
  const [z, s] = await Promise.all([get('/v1/zones'), get('/v1/stats')]);
  if (!z || !s) return;
  zones = z.zones; names = Object.fromEntries(zones.map(x => [x.id, x.name]));
  const justIn = seen === null ? [] : s.lastHour.filter(e => !seen.has(e.id)).map(e => e.id);
  justIn.forEach(id => freshUntil.set(id, Date.now() + 12000));
  if (justIn.length) pulse();
  seen = new Set(s.lastHour.map(e => e.id));
  const mark = id => justIn.includes(id) ? 'new flash' : freshUntil.get(id) > Date.now() ? 'new' : '';
  const first = s.lastHour.find(e => KIND[e.event] && names[e.zone_id]);
  if (first) focus = { zoneId:first.zone_id, kind:KIND[first.event] };
  $('events').innerHTML = s.lastHour.slice(0, 3).map(e => `<div class="event ${mark(e.id)}"><h3>${EVENT[e.event] || e.event}</h3>
    <div><span>구역</span>${names[e.zone_id] || '격자 셀 ' + e.zone_id.replace('cell:', '')}</div><div><span>시간대</span>${DAY[e.weekday]} ${bucket(e.time_bucket)}</div>
    <div><span>소요 시간</span>${Math.floor(e.duration_sec / 60)}분 ${e.duration_sec % 60}초</div><div><span>일일 익명 토큰</span>${e.token}…</div></div>`).join('')
    || '<span class="muted">최근 1시간 동안 받은 이벤트가 없습니다. 위의 시나리오 버튼을 눌러 보세요.</span>';
  const ids = zones.filter(x => x.type !== 'pickup_hotspot').map(x => x.id);
  const [hs, pr, pf] = await Promise.all([get('/v1/hotspots'), get('/v1/predictions?offsets=0&zoneIds=' + ids.join(',')), get(`/v1/profile?zoneId=${focus.zoneId}&kind=${focus.kind}`)]);
  if (pf && pf.hours.length) {
    const cur = pf.hours[pf.currentHour], key = focus.zoneId + focus.kind;
    $('profileTitle').textContent = `${names[focus.zoneId] || focus.zoneId} · ${KIND_LABEL[focus.kind]} (시간대별)`; drawChart(pf);
    if (lastSamples !== null && lastSamples.key === key && cur.samples > lastSamples.n) $('samplesDelta').textContent = `▲ ${lastSamples.n} → ${cur.samples}건`;
    if (lastSamples !== null && (lastSamples.key !== key || cur.samples < lastSamples.n)) $('samplesDelta').textContent = '';
    $('samples').textContent = cur.samples + '건'; $('nowMinutes').textContent = cur.minutes + '분'; lastSamples = { key, n:cur.samples };
  }
  const hot = hs && hs.hotspots.length ? '<div class="muted" style="margin-top:10px">승하차 혼잡 지점 · 승하차구역 설치 검토 근거</div>' + hs.hotspots.map(x => `<div class="row"><span style="color:var(--negative);font-weight:700">${names[x.id] || x.id}</span><span class="muted">${x.peak} · 차량 ${x.distinctVehicles}대 · ${x.distinctDays}일</span></div>`).join('') : '';
  if (pr) $('predictions').innerHTML = pr.predictions.filter(p => p.kind !== 'exit').map(p => {
    const key = p.zoneId + p.kind, old = lastPred[key], same = old && old.b === p.timeBucket, changed = same && old.m !== p.minutes;
    const cur = lastPred[key] = { m:p.minutes, b:p.timeBucket, from:changed ? old.m : same ? old.from : null, until:changed ? Date.now() + 15000 : same ? old.until : 0 };
    const d = cur.from !== null && cur.until > Date.now() ? `<span class="delta">${cur.from} → ${p.minutes}분</span>` : '';
    return `<div class="row"><span>${names[p.zoneId]} · ${p.kind === 'park' ? '자리 찾기' : '진입·대기'}</span><span><b>${p.minutes}분</b>${d} <span class="muted">${p.samples ? '기록 ' + p.samples + '건' : '기본값'}</span></span></div>`;
  }).join('') + hot;
  drawMap();
}

async function fast() {
  const [t, sc] = await Promise.all([get('/demo/telemetry'), get('/demo/scenario')]);
  document.querySelectorAll('button').forEach(b => { b.disabled = !sc || sc.running; b.classList.toggle('run', !!sc && sc.running && sc.name === b.dataset.s); });
  $('status').textContent = !sc ? '시연 기능 꺼짐 (L10_DEMO=1로 서버 실행)' : sc.log.length ? sc.log[sc.log.length - 1] : '';
  tele = t && t.telemetry && t.ageSec < 6 ? t.telemetry : null;
  showVehicle(tele);
  if (tele && tele.lat != null) onLocation(tele.lat, tele.lon);
}

function tick(now) { lastFrame = now; drawLive(now); requestAnimationFrame(tick); }

document.querySelectorAll('button').forEach(b => b.onclick = () => fetch('/demo/scenario/' + b.dataset.s, { method:'POST' }).then(fast));
get('/demo/roads').then(r => {
  if (!r) return;
  roads = r.roads.map(road => {
    const m = [], box = [9, 9, -9, -9];
    for (let i = 0; i < road.p.length; i += 2) { const x = mx(road.p[i + 1]), y = my(road.p[i]); m.push(x, y); box[0] = Math.min(box[0], x); box[1] = Math.min(box[1], y); box[2] = Math.max(box[2], x); box[3] = Math.max(box[3], y); }
    return { r:road.r, m, box };
  }).sort((a, b) => b.r - a.r);
  drawMap();
});
slow(); fast(); setInterval(slow, 2500); setInterval(fast, 1000); addEventListener('resize', slow);
requestAnimationFrame(tick);
setInterval(() => { const now = performance.now(); if (now - lastFrame > 250) drawLive(now); }, 250);
</script>
</body>
</html>
"""
