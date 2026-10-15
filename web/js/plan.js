// 여행 경로 만들기 페이지
// 관광지를 검색하거나 지도에서 골라 담고, TSP 로 방문 순서를 만든다.

const MAX_POINTS = 30;
const RANDOM_TYPE_CHOICES = [12, 14, 28, 38, 39];

const state = {
  tab: 'build',
  picked: [],           // 선택한 관광지 (추가한 순서)
  types: new Set([12]), // 지도에 보여줄 관광타입
  maps: null,
  map: null,
  browseOverlays: [],
  routeOverlays: [],
  polyline: null,
  lastRoute: null,      // 저장할 때 쓰는 마지막 결과 { order, returnToStart, routeMode }
  lastRandom: null,     // 마지막 랜덤 요청 { baseIds, resultIds }
};

const $ = (id) => document.getElementById(id);

init();

async function init() {
  setupTabs();
  renderChips();
  renderRandomTypes();
  attachAutocomplete($('searchInput'), (a) => addPicked(a));

  $('clearPicked').addEventListener('click', () => {
    state.picked = [];
    renderPicked();
    clearRoute();
  });
  $('optimizeBtn').addEventListener('click', optimize);
  $('randomBtn').addEventListener('click', randomRoute);
  $('returnCheck').addEventListener('change', () => {
    $('endSelect').disabled = $('returnCheck').checked;
  });

  await loadCart();
  renderPicked();

  try {
    state.maps = await loadKakaoMap();
  } catch (e) {
    showMapNotice($('map'), e.message);
  }
  if (state.maps) {
    const { maps } = state;
    state.map = new maps.Map($('map'), { center: new maps.LatLng(37.5665, 126.9780), level: 6 });
    state.map.addControl(new maps.ZoomControl(), maps.ControlPosition.RIGHT);
    maps.event.addListener(state.map, 'idle', debounce(loadBounds, 250));
    if (state.picked.length) fitTo(state.picked);
    loadBounds();
  } else if (!$('map').innerHTML) {
    showMapNotice($('map'));
  }
}

// ------------------------------------------------------------ 탭

function setupTabs() {
  document.querySelectorAll('.tabs button').forEach(btn => {
    btn.addEventListener('click', () => {
      state.tab = btn.dataset.tab;
      document.querySelectorAll('.tabs button').forEach(b => b.classList.toggle('on', b === btn));
      document.querySelectorAll('[data-panel]').forEach(p => { p.hidden = p.dataset.panel !== state.tab; });
      $('addArea').hidden = state.tab === 'saved';
      if (state.tab === 'saved') loadSaved();
      renderPicked(false); // 탭에 따라 버튼만 바뀌므로 지도는 그대로 둔다
    });
  });
}

// ------------------------------------------------------------ 선택 목록

async function loadCart() {
  const ids = readCart();
  if (!ids.length) return;
  try {
    const list = await api('/api/attractions?ids=' + ids.join(','));
    list.forEach(a => addPicked(a, true));
    writeCart([]); // 가져왔으면 비운다
    showMessage(`탐색 페이지에서 담은 ${list.length}곳을 불러왔습니다.`);
  } catch (e) {
    showMessage(e.message, true);
  }
}

function addPicked(a, silent) {
  if (state.picked.some(p => p.contentId === a.contentId)) {
    if (!silent) showMessage(`"${a.title}" 은(는) 이미 선택되어 있습니다.`);
    return;
  }
  if (state.picked.length >= MAX_POINTS) {
    showMessage(`최대 ${MAX_POINTS}곳까지 선택할 수 있습니다.`, true);
    return;
  }
  state.picked.push(a);
  if (!silent) {
    renderPicked();
    showMessage('');
  }
}

function removePicked(contentId) {
  state.picked = state.picked.filter(p => p.contentId !== contentId);
  renderPicked();
}

function renderPicked(redrawMap = true) {
  $('pickedTitle').textContent = `선택한 관광지 ${state.picked.length} / ${MAX_POINTS}`;
  if (!state.picked.length) {
    $('picked').innerHTML = '<li class="muted">검색하거나 지도에서 관광지를 골라 추가하세요.</li>';
  } else {
    $('picked').innerHTML = state.picked.map((a, i) => `
      <li data-id="${a.contentId}">
        <span class="num" style="${i === 0 && state.tab === 'random' ? 'background:#111827' : ''}">${i + 1}</span>
        ${thumbHtml(a)}
        <div class="info">
          <div class="title">${escapeHtml(a.title)}</div>
          <div class="sub">${typeBadge(a.contentTypeId)} ${escapeHtml(a.addr1 || '')}</div>
        </div>
        <div class="actions">
          ${i > 0 && state.tab === 'random' ? `<button class="btn small" data-first="${a.contentId}" title="랜덤 경로의 기준으로 삼기">기준으로</button>` : ''}
          ${i === 0 && state.tab === 'random' ? '<span class="muted" style="font-size:12px">기준</span>' : ''}
          <button class="btn small danger" data-remove="${a.contentId}">삭제</button>
        </div>
      </li>`).join('');
    $('picked').querySelectorAll('[data-remove]').forEach(b =>
      b.addEventListener('click', (e) => { e.stopPropagation(); removePicked(Number(b.dataset.remove)); }));
    $('picked').querySelectorAll('[data-first]').forEach(b =>
      b.addEventListener('click', (e) => { e.stopPropagation(); moveToFirst(Number(b.dataset.first)); }));
    $('picked').querySelectorAll('li[data-id]').forEach(li =>
      li.addEventListener('click', () => showPickedInfo(Number(li.dataset.id))));
  }
  renderEndpointSelects();
  if (redrawMap) drawPickedOnMap();
}

// 이미 선택한 관광지 정보. 빼기 버튼을 같이 보여준다
function showPickedInfo(contentId) {
  openAttractionModal(contentId, [
    { label: '경로에서 빼기', danger: true, onClick: () => removePicked(contentId) },
  ]);
}

// 아직 선택하지 않은 관광지 정보. 추가 버튼을 같이 보여준다
function showCandidateInfo(a) {
  if (state.picked.some(p => p.contentId === a.contentId)) {
    showPickedInfo(a.contentId);
    return;
  }
  openAttractionModal(a.contentId, [
    { label: '경로에 추가', primary: true, onClick: () => { addPicked(a); loadBounds(); } },
  ]);
}

// 랜덤 경로는 첫 번째 관광지가 기준이라 순서를 바꿀 수 있게 한다
function moveToFirst(contentId) {
  const i = state.picked.findIndex(p => p.contentId === contentId);
  if (i > 0) {
    const [a] = state.picked.splice(i, 1);
    state.picked.unshift(a);
    renderPicked();
  }
}

function renderEndpointSelects() {
  const options = state.picked.map(a => `<option value="${a.contentId}">${escapeHtml(a.title)}</option>`).join('');
  const keep = (sel) => sel.value;
  const start = keep($('startSelect'));
  const end = keep($('endSelect'));
  $('startSelect').innerHTML = '<option value="">자동 (가장 짧은 경로)</option>' + options;
  $('endSelect').innerHTML = '<option value="">자동</option>' + options;
  // 목록이 바뀌어도 아직 있는 곳이면 선택 유지
  if (state.picked.some(a => String(a.contentId) === start)) $('startSelect').value = start;
  if (state.picked.some(a => String(a.contentId) === end)) $('endSelect').value = end;
}

// ------------------------------------------------------------ 경로 생성

async function optimize() {
  if (state.picked.length < 2) {
    showMessage('관광지를 2곳 이상 선택해 주세요.', true);
    return;
  }
  const body = {
    contentIds: state.picked.map(a => a.contentId),
    startId: $('startSelect').value ? Number($('startSelect').value) : null,
    endId: !$('returnCheck').checked && $('endSelect').value ? Number($('endSelect').value) : null,
    returnToStart: $('returnCheck').checked,
  };
  try {
    const r = await api('/api/routes/optimize', { method: 'POST', body });
    showMessage('');
    showRoute(r);
  } catch (e) {
    showMessage(e.message, true);
  }
}

async function randomRoute() {
  if (!state.picked.length) {
    showMessage('기준이 될 관광지를 먼저 1곳 이상 추가해 주세요.', true);
    return;
  }
  const count = Number($('randomCount').value) || 5;
  const types = [...document.querySelectorAll('#randomTypes input:checked')].map(c => Number(c.value));
  // 랜덤 결과가 목록에 그대로 있으면 "다시 뽑기"로 보고, 처음 사용자가 고른 기준만 다시 보낸다
  const pickedIds = state.picked.map(a => a.contentId);
  const last = state.lastRandom;
  const sameAsLast = last && last.resultIds.length === pickedIds.length
    && last.resultIds.every((id, i) => id === pickedIds[i]);
  const base = sameAsLast ? last.baseIds : pickedIds;
  try {
    const r = await api('/api/routes/random', { method: 'POST', body: { baseIds: base, count, types } });
    state.lastRandom = { baseIds: base, resultIds: r.route.order.map(a => a.contentId) };
    state.picked = r.route.order.slice();
    renderPicked();
    showMessage(r.message || `반경 5km 안 후보 ${r.candidateCount}곳 중에서 골랐습니다. 다시 누르면 새로 뽑습니다.`);
    showRoute(r.route);
  } catch (e) {
    showMessage(e.message, true);
  }
}

// ------------------------------------------------------------ 결과 표시

function showRoute(r) {
  const unitIsMeter = r.unit === 'm';
  state.lastRoute = {
    order: r.order,
    returnToStart: r.returnToStart,
    routeMode: r.distanceProvider,
  };
  const legs = r.legs.map(l => l.cost);
  renderResult(r.order, legs, r.totalCost, r.returnToStart, {
    algorithm: r.algorithm,
    exact: r.exact,
    elapsedMs: r.elapsedMs,
    unitIsMeter,
  });
}

function renderResult(order, legs, total, returnToStart, meta) {
  const metaText = meta
    ? `${meta.exact ? '최적 경로' : '근사 경로'} · ${meta.algorithm} · ${meta.elapsedMs.toFixed(1)}ms`
    : '저장된 순서';
  const stops = order.map((a, i) => `
    <div class="stop" data-id="${a.contentId}">
      <span class="num">${i + 1}</span>
      <div class="info" style="min-width:0">
        <div class="title">${escapeHtml(a.title)}</div>
        <div class="sub muted">${escapeHtml(a.addr1 || '')}</div>
      </div>
    </div>
    ${i < legs.length ? `<div class="leg">↓ ${formatDistance(legs[i])}</div>` : ''}`).join('');
  const back = returnToStart && order.length > 1
    ? `<div class="stop"><span class="num">1</span><div class="title">${escapeHtml(order[0].title)} (복귀)</div></div>` : '';

  $('result').innerHTML = `
    <div class="result">
      <div class="summary">
        <span>총 이동 <b>${formatDistance(total)}</b></span>
        <span class="muted">도보 약 ${formatMinutes(walkMinutes(total))} (직선거리 기준)</span>
        <span class="muted" style="width:100%">${escapeHtml(metaText)}</span>
      </div>
      ${stops}${back}
      <div class="save-row">
        <input type="text" id="planTitle" maxlength="100" placeholder="경로 이름 (예: 서울 궁궐 산책)">
        <button class="btn primary" id="saveBtn">저장</button>
      </div>
    </div>`;
  $('saveBtn').addEventListener('click', savePlan);
  $('result').querySelectorAll('.stop[data-id]').forEach(el =>
    el.addEventListener('click', () => openAttractionModal(Number(el.dataset.id))));
  drawRoute(order, returnToStart);
}

function clearRoute() {
  $('result').innerHTML = '';
  state.lastRoute = null;
  if (state.polyline) state.polyline.setMap(null);
  state.routeOverlays.forEach(o => o.setMap(null));
  state.routeOverlays = [];
}

function showMessage(text, isError) {
  $('message').innerHTML = text ? `<div class="message ${isError ? 'error' : ''}">${escapeHtml(text)}</div>` : '';
}

// ------------------------------------------------------------ 저장 / 불러오기

async function savePlan() {
  if (!state.lastRoute) return;
  const title = $('planTitle').value.trim();
  if (!title) {
    $('planTitle').focus();
    showMessage('경로 이름을 입력해 주세요.', true);
    return;
  }
  try {
    await api('/api/plans', {
      method: 'POST',
      body: {
        title,
        routeMode: state.lastRoute.routeMode,
        returnToStart: state.lastRoute.returnToStart,
        contentIds: state.lastRoute.order.map(a => a.contentId),
      },
    });
    showMessage(`"${title}" 경로를 저장했습니다. 저장된 경로 탭에서 다시 볼 수 있습니다.`);
  } catch (e) {
    showMessage(e.message, true);
  }
}

async function loadSaved() {
  try {
    const plans = await api('/api/plans');
    if (!plans.length) {
      $('savedList').innerHTML = '<li class="muted">저장된 경로가 없습니다.</li>';
      return;
    }
    $('savedList').innerHTML = plans.map(p => `
      <li data-id="${p.planId}">
        <div class="info">
          <div class="title">${escapeHtml(p.title)}</div>
          <div class="sub">${p.count}곳 · ${formatDistance(p.totalDistanceM)} · ${escapeHtml(p.createdAt || '')}</div>
          <div class="sub">${escapeHtml(p.titles.join(' → '))}</div>
        </div>
        <div class="actions" style="display:flex; gap:4px">
          <button class="btn small" data-load="${p.planId}">불러오기</button>
          <button class="btn small danger" data-del="${p.planId}">삭제</button>
        </div>
      </li>`).join('');
    $('savedList').querySelectorAll('[data-load]').forEach(b =>
      b.addEventListener('click', () => openPlan(Number(b.dataset.load))));
    $('savedList').querySelectorAll('[data-del]').forEach(b =>
      b.addEventListener('click', () => deletePlan(Number(b.dataset.del))));
  } catch (e) {
    showMessage(e.message, true);
  }
}

async function openPlan(planId) {
  try {
    const p = await api('/api/plans/' + planId);
    state.picked = p.attractions.slice();
    renderPicked();
    // 저장된 순서 그대로 보여준다. 구간 거리는 직선거리로 다시 계산
    const legs = [];
    for (let i = 0; i + 1 < p.attractions.length; i++) {
      legs.push(haversine(p.attractions[i], p.attractions[i + 1]));
    }
    state.lastRoute = { order: p.attractions, returnToStart: p.returnToStart, routeMode: p.routeMode };
    renderResult(p.attractions, legs, p.totalDistanceM, p.returnToStart, null);
    $('planTitle').value = p.title;
    showMessage(`"${p.title}" 경로를 불러왔습니다.`);
  } catch (e) {
    showMessage(e.message, true);
  }
}

async function deletePlan(planId) {
  try {
    await api('/api/plans/' + planId, { method: 'DELETE' });
    loadSaved();
  } catch (e) {
    showMessage(e.message, true);
  }
}

function haversine(a, b) {
  const R = 6371000;
  const toRad = (d) => d * Math.PI / 180;
  const dLat = toRad(b.latitude - a.latitude);
  const dLng = toRad(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(toRad(a.latitude)) * Math.cos(toRad(b.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

// ------------------------------------------------------------ 랜덤 경로 옵션

function renderRandomTypes() {
  $('randomTypes').innerHTML = RANDOM_TYPE_CHOICES.map(id => `
    <label><input type="checkbox" value="${id}" ${id === 12 || id === 14 ? 'checked' : ''}> ${TYPE_INFO[id].name}</label>
  `).join('');
}

// ------------------------------------------------------------ 지도

function renderChips() {
  $('typeChips').innerHTML = Object.entries(TYPE_INFO).map(([id, t]) => `
    <button class="chip ${state.types.has(Number(id)) ? 'on' : ''}" data-id="${id}" style="${state.types.has(Number(id)) ? 'color:' + t.color : ''}">
      <span class="dot" style="background:${t.color}"></span>${t.name}
    </button>`).join('');
  $('typeChips').querySelectorAll('.chip').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = Number(btn.dataset.id);
      if (state.types.has(id)) state.types.delete(id); else state.types.add(id);
      renderChips();
      loadBounds();
    });
  });
}

async function loadBounds() {
  if (!state.map) return;
  const { maps, map } = state;
  state.browseOverlays.forEach(o => o.setMap(null));
  state.browseOverlays = [];
  if (!state.types.size) return;

  const b = map.getBounds();
  const sw = b.getSouthWest();
  const ne = b.getNorthEast();
  const list = await api(`/api/attractions/bounds?limit=200&types=${[...state.types].join(',')}`
    + `&swLat=${sw.getLat()}&swLng=${sw.getLng()}&neLat=${ne.getLat()}&neLng=${ne.getLng()}`);
  const pickedIds = new Set(state.picked.map(p => p.contentId));
  state.browseOverlays = list.filter(a => !pickedIds.has(a.contentId)).map(a => {
    const el = document.createElement('div');
    el.className = 'marker-dot';
    el.style.background = (TYPE_INFO[a.contentTypeId] || {}).color || '#999';
    el.title = a.title;
    el.addEventListener('click', () => showCandidateInfo(a));
    const o = new maps.CustomOverlay({ position: new maps.LatLng(a.latitude, a.longitude), content: el, yAnchor: 0.5 });
    o.setMap(map);
    return o;
  });
  $('mapStatus').hidden = list.length < 200;
  $('mapStatus').textContent = '관광지가 많아 추천순 200곳만 표시합니다. 지도를 확대해 보세요.';
}

// 선택한 관광지를 번호 핀으로 표시 (경로 생성 전)
function drawPickedOnMap() {
  if (!state.map) return;
  drawPins(state.picked);
  if (state.polyline) state.polyline.setMap(null);
}

// 생성된 경로를 번호 핀 + 선으로 표시
function drawRoute(order, returnToStart) {
  if (!state.map) return;
  const { maps, map } = state;
  drawPins(order);
  if (state.polyline) state.polyline.setMap(null);
  const path = order.map(a => new maps.LatLng(a.latitude, a.longitude));
  if (returnToStart && order.length > 1) path.push(path[0]);
  state.polyline = new maps.Polyline({
    path,
    strokeWeight: 4,
    strokeColor: '#1d6fd8',
    strokeOpacity: 0.85,
    strokeStyle: 'solid',
  });
  state.polyline.setMap(map);
  fitTo(order);
}

function drawPins(list) {
  const { maps, map } = state;
  state.routeOverlays.forEach(o => o.setMap(null));
  state.routeOverlays = list.map((a, i) => {
    const el = document.createElement('div');
    el.className = 'route-pin';
    el.textContent = i + 1;
    el.title = a.title;
    el.addEventListener('click', () => showPickedInfo(a.contentId));
    const o = new maps.CustomOverlay({ position: new maps.LatLng(a.latitude, a.longitude), content: el, yAnchor: 0.5, zIndex: 5 });
    o.setMap(map);
    return o;
  });
}

function fitTo(list) {
  if (!state.map || !list.length) return;
  const { maps, map } = state;
  const bounds = new maps.LatLngBounds();
  list.forEach(a => bounds.extend(new maps.LatLng(a.latitude, a.longitude)));
  map.setBounds(bounds);
}
