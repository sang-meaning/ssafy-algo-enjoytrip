// 두 페이지에서 같이 쓰는 함수 모음

// 서버 주소. 같은 서버에서 화면을 열면 빈 문자열로 두면 된다.
// VS Code Live Server 처럼 다른 포트에서 열 때는 'http://localhost:8080' 으로 바꾼다.
const API_BASE = location.port === '8080' ? '' : 'http://localhost:8080';

// 관광타입별 이름과 색
const TYPE_INFO = {
  12: { name: '관광지', color: '#1d6fd8' },
  14: { name: '문화시설', color: '#8b5cf6' },
  15: { name: '축제공연행사', color: '#ec4899' },
  28: { name: '레포츠', color: '#10b981' },
  32: { name: '숙박', color: '#6b7280' },
  38: { name: '쇼핑', color: '#f59e0b' },
  39: { name: '음식점', color: '#ef4444' },
};

async function api(path, options = {}) {
  const opts = { ...options };
  if (opts.body && typeof opts.body !== 'string') {
    opts.body = JSON.stringify(opts.body);
    opts.headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  }
  const res = await fetch(API_BASE + path, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error || ('요청 실패 (' + res.status + ')'));
  }
  return data;
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

function typeBadge(typeId) {
  const t = TYPE_INFO[typeId] || { name: '기타', color: '#9ca3af' };
  return `<span class="type-badge" style="background:${t.color}">${t.name}</span>`;
}

function formatDistance(m) {
  if (m < 1000) return Math.round(m) + 'm';
  return (m / 1000).toFixed(m < 10000 ? 2 : 1) + 'km';
}

// 직선거리 기준 도보 시간 (시속 4km)
function walkMinutes(m) {
  return Math.max(1, Math.round(m / 1000 / 4 * 60));
}

function formatMinutes(min) {
  if (min < 60) return min + '분';
  return Math.floor(min / 60) + '시간 ' + (min % 60) + '분';
}

function debounce(fn, ms) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

function thumbHtml(a) {
  const src = a.firstImage2 || a.firstImage;
  return src ? `<img src="${escapeHtml(src)}" alt="" loading="lazy">` : '<div class="noimg"></div>';
}

// ------------------------------------------------------------ 자동완성

// input 에 자동완성을 붙인다. 항목을 고르면 onSelect(attraction) 이 불린다.
// 키보드 위/아래, Enter, Esc 를 지원한다.
function attachAutocomplete(input, onSelect, onEnterText) {
  const box = document.createElement('ul');
  box.className = 'suggest';
  box.hidden = true;
  input.parentElement.appendChild(box);

  let items = [];
  let active = -1;
  let timer = null;
  let generation = 0; // 닫을 때마다 올려서, 닫힌 뒤 도착한 응답이 목록을 다시 여는 것을 막는다

  const render = () => {
    if (!items.length) {
      box.innerHTML = '<li class="empty">검색 결과가 없습니다</li>';
      return;
    }
    box.innerHTML = items.map((a, i) => `
      <li data-i="${i}" class="${i === active ? 'on' : ''}">
        ${typeBadge(a.contentTypeId)}
        <span>${escapeHtml(a.title)}</span>
        <span class="addr">${escapeHtml(a.addr1 || '')}</span>
      </li>`).join('');
  };

  const close = () => {
    clearTimeout(timer);
    generation++;
    box.hidden = true;
    active = -1;
  };

  const fetchSuggest = async () => {
    const q = input.value.trim();
    const gen = ++generation;
    if (!q) { close(); return; }
    try {
      const result = await api('/api/attractions/suggest?limit=10&q=' + encodeURIComponent(q));
      if (gen !== generation) return; // 그 사이 새 입력이 있었거나 닫혔으면 버린다
      items = result;
      active = -1;
      render();
      box.hidden = false;
    } catch (e) {
      close();
    }
  };

  const search = () => {
    clearTimeout(timer);
    timer = setTimeout(fetchSuggest, 150);
  };

  const choose = (i) => {
    const a = items[i];
    if (!a) return;
    close();
    input.value = '';
    onSelect(a);
  };

  input.addEventListener('input', search);
  input.addEventListener('keydown', (e) => {
    if (e.isComposing) return; // 한글 조합 중 Enter 가 두 번 처리되는 것 방지
    if (e.key === 'ArrowDown' && !box.hidden) {
      active = Math.min(items.length - 1, active + 1); render(); e.preventDefault();
    } else if (e.key === 'ArrowUp' && !box.hidden) {
      active = Math.max(0, active - 1); render(); e.preventDefault();
    } else if (e.key === 'Enter') {
      if (!box.hidden && active >= 0) {
        choose(active);
      } else if (onEnterText) {
        close();
        onEnterText(input.value.trim());
      }
      e.preventDefault();
    } else if (e.key === 'Escape') {
      close();
    }
  });
  box.addEventListener('mousedown', (e) => {
    const li = e.target.closest('li[data-i]');
    if (li) {
      e.preventDefault();
      choose(Number(li.dataset.i));
    }
  });
  input.addEventListener('blur', () => setTimeout(close, 100));
}

// ------------------------------------------------------------ 카카오맵

// /api/config 에서 키를 받아 SDK 를 불러온다. 키가 없으면 null.
async function loadKakaoMap() {
  const config = await api('/api/config');
  if (!config.kakaoJsKey) return null;
  await new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=' + encodeURIComponent(config.kakaoJsKey);
    s.onload = () => window.kakao.maps.load(resolve);
    s.onerror = () => reject(new Error('카카오맵 SDK 를 불러오지 못했습니다'));
    document.head.appendChild(s);
  });
  return window.kakao.maps;
}

function showMapNotice(el, error) {
  el.innerHTML = `
    <div class="map-notice">
      <div>
        ${error ? escapeHtml(error) + '<br>' : ''}
        지도를 보려면 <code>config/app.properties</code> 의 <code>kakao.js.key</code> 에<br>
        카카오 JavaScript 키를 넣고 서버를 다시 실행해 주세요.<br>
        카카오 개발자 콘솔의 플랫폼 &gt; Web 에 <code>http://localhost:8080</code> 도메인을 등록해야 합니다.<br>
        지도 없이도 검색과 경로 만들기는 사용할 수 있습니다.
      </div>
    </div>`;
}

// ------------------------------------------------------------ 탐색 페이지 -> 경로 페이지 전달

const CART_KEY = 'enjoytrip.cart';

function readCart() {
  try {
    return JSON.parse(localStorage.getItem(CART_KEY) || '[]');
  } catch (e) {
    return [];
  }
}

function writeCart(ids) {
  try {
    localStorage.setItem(CART_KEY, JSON.stringify(ids));
  } catch (e) {
    // 저장소를 쓸 수 없는 환경이면 무시한다
  }
}

function addToCart(contentId) {
  const ids = readCart();
  if (!ids.includes(contentId)) ids.push(contentId);
  writeCart(ids);
  return ids.length;
}
