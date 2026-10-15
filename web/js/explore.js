// 관광지 탐색 페이지
// 지도를 움직이면 화면 영역 안의 관광지를 마커로 표시하고, 마커나 목록을 누르면 상세 정보를 보여준다.

const state = {
  types: new Set([12, 14]),
  maps: null,      // kakao.maps (키가 없으면 null)
  map: null,
  overlays: [],
  popup: null,
  mode: 'map',     // map: 지도 영역 목록, search: 검색 결과 목록
  query: '',
};

const $ = (id) => document.getElementById(id);

init();

async function init() {
  renderChips();
  updateCartCount();

  attachAutocomplete($('searchInput'),
    (a) => selectAttraction(a.contentId, true),
    (text) => searchList(text));

  try {
    state.maps = await loadKakaoMap();
  } catch (e) {
    showMapNotice($('map'), e.message);
  }

  if (state.maps) {
    const { maps } = state;
    state.map = new maps.Map($('map'), {
      center: new maps.LatLng(37.5665, 126.9780), // 서울시청
      level: 6,
    });
    state.map.addControl(new maps.ZoomControl(), maps.ControlPosition.RIGHT);
    maps.event.addListener(state.map, 'idle', debounce(loadBounds, 250));
    loadBounds();
  } else {
    if (!$('map').innerHTML) showMapNotice($('map'));
    searchList('');
  }
}

// ------------------------------------------------------------ 관광타입 칩

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
      refresh();
    });
  });
}

function typesParam() {
  return [...state.types].join(',');
}

function refresh() {
  if (state.mode === 'search' || !state.map) {
    searchList(state.query);
  } else {
    loadBounds();
  }
}

// ------------------------------------------------------------ 목록

async function searchList(text) {
  state.mode = 'search';
  state.query = text;
  const list = await api(`/api/attractions/search?limit=100&types=${typesParam()}&q=${encodeURIComponent(text)}`);
  $('listTitle').innerHTML = text
    ? `"${escapeHtml(text)}" 검색 결과 ${state.map ? '<button class="btn small" id="backToMap">지도 영역 보기</button>' : ''}`
    : '추천 관광지';
  $('listCount').textContent = list.length + (list.length === 100 ? '+' : '') + '곳';
  renderList(list);
  if (state.map) {
    drawMarkers(list);
    if (list.length) fitTo(list);
    const back = $('backToMap');
    if (back) back.addEventListener('click', () => { state.mode = 'map'; state.query = ''; loadBounds(); });
  }
}

async function loadBounds() {
  if (!state.map || state.mode === 'search') return;
  const b = state.map.getBounds();
  const sw = b.getSouthWest();
  const ne = b.getNorthEast();
  const list = await api(`/api/attractions/bounds?limit=300&types=${typesParam()}`
    + `&swLat=${sw.getLat()}&swLng=${sw.getLng()}&neLat=${ne.getLat()}&neLng=${ne.getLng()}`);
  $('listTitle').textContent = '지도 영역 안의 관광지';
  $('listCount').textContent = list.length + (list.length === 300 ? '+' : '') + '곳';
  renderList(list.slice(0, 100));
  drawMarkers(list);
  setStatus(list.length === 300 ? '관광지가 많아 추천순 300곳만 표시합니다. 지도를 확대해 보세요.' : '');
}

function renderList(list) {
  if (!list.length) {
    $('list').innerHTML = '<li class="muted">표시할 관광지가 없습니다</li>';
    return;
  }
  $('list').innerHTML = list.map(a => `
    <li data-id="${a.contentId}">
      ${thumbHtml(a)}
      <div class="info">
        <div class="title">${escapeHtml(a.title)}</div>
        <div class="sub">${typeBadge(a.contentTypeId)} ${escapeHtml(a.addr1 || '')}</div>
      </div>
    </li>`).join('');
  $('list').querySelectorAll('li[data-id]').forEach(li => {
    li.addEventListener('click', () => selectAttraction(Number(li.dataset.id), true));
  });
}

// ------------------------------------------------------------ 지도 마커

function drawMarkers(list) {
  const { maps, map } = state;
  state.overlays.forEach(o => o.setMap(null));
  state.overlays = list.map(a => {
    const el = document.createElement('div');
    el.className = 'marker-dot';
    el.style.background = (TYPE_INFO[a.contentTypeId] || {}).color || '#999';
    el.title = a.title;
    el.addEventListener('click', () => selectAttraction(a.contentId, false));
    const overlay = new maps.CustomOverlay({
      position: new maps.LatLng(a.latitude, a.longitude),
      content: el,
      yAnchor: 0.5,
    });
    overlay.setMap(map);
    return overlay;
  });
}

function fitTo(list) {
  const { maps, map } = state;
  const bounds = new maps.LatLngBounds();
  list.slice(0, 50).forEach(a => bounds.extend(new maps.LatLng(a.latitude, a.longitude)));
  map.setBounds(bounds);
}

function showPopup(a) {
  const { maps, map } = state;
  if (state.popup) state.popup.setMap(null);
  const el = document.createElement('div');
  el.className = 'map-popup';
  el.innerHTML = `<div class="t">${escapeHtml(a.title)}</div>`;
  state.popup = new maps.CustomOverlay({
    position: new maps.LatLng(a.latitude, a.longitude),
    content: el,
    yAnchor: 1,
    zIndex: 10,
  });
  state.popup.setMap(map);
}

function setStatus(text) {
  $('mapStatus').hidden = !text;
  $('mapStatus').textContent = text;
}

// ------------------------------------------------------------ 상세

async function selectAttraction(contentId, moveMap) {
  const d = await api('/api/attractions/' + contentId);
  const a = d.attraction;
  const img = a.firstImage ? `<img src="${escapeHtml(a.firstImage)}" alt="">` : '';
  $('detail').innerHTML = `
    <div class="detail">
      ${img}
      <div class="body">
        ${typeBadge(a.contentTypeId)}
        <h3>${escapeHtml(a.title)}</h3>
        <p class="muted">${escapeHtml(a.addr1 || '')} ${escapeHtml(a.addr2 || '')}</p>
        ${a.tel ? `<p>전화 ${escapeHtml(a.tel)}</p>` : ''}
        ${d.overview ? `<p class="overview">${escapeHtml(stripHtml(d.overview))}</p>` : ''}
        ${homepageHtml(d.homepage)}
        <div style="display:flex; gap:6px; margin-top:10px">
          <button class="btn primary" id="addCart">경로에 담기</button>
          <a class="btn" href="plan.html">경로 만들러 가기</a>
          <button class="btn" id="closeDetail" style="margin-left:auto">닫기</button>
        </div>
      </div>
    </div>`;
  $('addCart').addEventListener('click', () => {
    const n = addToCart(a.contentId);
    $('addCart').textContent = '담았습니다 (' + n + ')';
    $('addCart').disabled = true;
    updateCartCount();
  });
  $('closeDetail').addEventListener('click', () => {
    $('detail').innerHTML = '';
    if (state.popup) state.popup.setMap(null);
  });
  $('detail').scrollIntoView({ block: 'nearest' });

  if (state.map) {
    if (moveMap) {
      state.map.setLevel(4);
      state.map.panTo(new state.maps.LatLng(a.latitude, a.longitude));
    }
    showPopup(a);
  }
}

function updateCartCount() {
  const n = readCart().length;
  $('cartCount').textContent = n ? '(' + n + ')' : '';
}
