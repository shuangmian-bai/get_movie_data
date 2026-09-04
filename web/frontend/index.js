// 前端逻辑：搜索 -> 详情 -> 原生播放
// 页面全局函数（doSearch / gotoPage / showDetail / playEpisode / backToSearch 等）
// 在全局作用域定义，供 index.html 中的内联 onclick 调用。
const PAGE_SIZE = 5;   // 每页条数（后端 start/count 分页）；改小以降低爬虫压力、加快响应
let currentKey = '';     // 当前搜索关键词
let currentPage = 0;     // 当前展示页（0-based，始终指向有数据的页）
let searchResults = [];  // 当前页数据
let hasNext = true;      // 是否还有下一页
let currentInfo = null;  // 当前详情

// HTML 转义，防止动态文本破坏结构 / XSS
function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

// 封面：有 URL 显示图片（加载失败自动隐藏），否则占位
function coverHtml(cover, cls, tip) {
  return cover
    ? `<img class="${cls}" src="${esc(cover)}" alt="" onerror="this.style.display='none'">`
    : `<div class="${cls} placeholder">${tip}</div>`;
}

// 数据源多选（默认全选）
fetch('/api/sources')
  .then(r => r.json())
  .then(list => {
    const box = document.getElementById('source-checks');
    box.innerHTML = list.map(s => `
      <label class="source-check">
        <input type="checkbox" data-url="${esc(s.base_url)}" checked> ${esc(s.source_name)}
      </label>`).join('');
  })
  .catch(() => document.getElementById('source-checks').textContent = '数据源加载失败');

// 读取当前勾选的数据源 base_url（无勾选返回空数组）
function selectedUrls() {
  return Array.from(document.querySelectorAll('#source-checks input:checked'))
    .map(el => el.dataset.url);
}

// 渲染单个结果卡片
function renderCard(item, i) {
  return `
    <div class="card" onclick="showDetail(${i})">
      ${coverHtml(item.cover, 'cover', '无图')}
      <div class="info">
        <h3>${esc(item.name)} <span class="meta">${esc(item.type)}${item.year ? ' · ' + esc(item.year) : ''}</span></h3>
        <div class="desc">${esc(item.desc)}</div>
      </div>
    </div>`;
}

// 渲染当前页结果 + 分页条
function renderResults() {
  document.getElementById('results').innerHTML = searchResults.map(renderCard).join('');
  renderPager();
}

function renderPager() {
  const pager = document.getElementById('pager');
  if (!currentKey || !searchResults.length) { pager.hidden = true; return; }
  pager.hidden = false;
  document.getElementById('page-info').textContent =
    '第 ' + (currentPage + 1) + ' 页' + (hasNext ? '' : ' · 已到最后一页');
  document.getElementById('prev-btn').disabled = currentPage === 0;
  document.getElementById('next-btn').disabled = !hasNext;
}

// 发起一次分页请求：只抓 [start, start+PAGE_SIZE) 这一页，按需爬取
async function fetchPage(page) {
  const box = document.getElementById('results');
  const urls = selectedUrls();
  if (!urls.length) {
    box.innerHTML = '<div class="empty">请至少选择一个数据源</div>';
    document.getElementById('pager').hidden = true;
    return;
  }
  box.innerHTML = '搜索中…';
  document.getElementById('pager').hidden = true;
  try {
    const start = page * PAGE_SIZE;
    const qs = urls.map(u => '&base_urls=' + encodeURIComponent(u)).join('');
    const resp = await fetch('/api/search?key=' + encodeURIComponent(currentKey) + '&start=' + start + '&count=' + PAGE_SIZE + qs);
    const list = await resp.json();
    if (!resp.ok) { box.innerHTML = '<div class="empty">请求失败：' + resp.status + '</div>'; return; }
    if (!list.length) {
      if (page === 0) {
        currentKey = '';
        searchResults = [];
        box.innerHTML = '<div class="empty">无结果</div>';
      } else {
        // 翻过头：停留在上一页，禁用下一页
        hasNext = false;
        renderResults();
      }
      return;
    }
    currentPage = page;
    searchResults = list;
    hasNext = true;
    renderResults();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (e) {
    box.innerHTML = '<div class="empty">搜索失败：' + esc(e) + '</div>';
  }
}

// 新搜索（固定从第 0 页开始）
async function doSearch() {
  const key = document.getElementById('key').value.trim();
  if (!key) return;
  currentKey = key;
  currentPage = 0;
  hasNext = true;
  searchResults = [];
  await fetchPage(0);
}

// 翻页（上一页/下一页）
async function gotoPage(page) {
  if (!currentKey) return;
  await fetchPage(page);
}

// 点击结果 -> 详情 + 选集
async function showDetail(i) {
  const item = searchResults[i];
  if (!item) return;
  document.getElementById('search-view').hidden = true;
  document.getElementById('detail-view').hidden = false;
  document.getElementById('home-bg').hidden = true;  // 进入详情隐藏背景图片
  const detail = document.getElementById('detail');
  detail.innerHTML = '加载详情中…';
  try {
    const resp = await fetch('/api/info?base_url=' + encodeURIComponent(item.base_url) + '&link=' + encodeURIComponent(item.link));
    const info = await resp.json();
    if (!resp.ok) { detail.innerHTML = '<div class="empty">详情加载失败：' + resp.status + '</div>'; return; }
    currentInfo = info;
    detail.innerHTML = renderDetail(info);
  } catch (e) {
    detail.innerHTML = '<div class="empty">详情加载失败：' + esc(e) + '</div>';
  }
}

function renderDetail(info) {
  const eps = info.episodes || [];
  const epBtns = eps.length
    ? '<div class="eps">' + eps.map(ep => `<button class="ep" onclick="playEpisode(${ep.index})">${esc(ep.name)}</button>`).join('') + '</div>'
    : '<div class="empty">暂无分集</div>';
  return `
    <div class="detail-head">
      ${coverHtml(info.cover, 'cover-lg', '无图')}
      <div class="detail-info">
        <h2>${esc(info.name)}</h2>
        <div class="meta">${esc(info.type)}${info.year ? ' · ' + esc(info.year) : ''}</div>
        <div class="desc">${esc(info.desc)}</div>
      </div>
    </div>
    <div id="play-result">
      <div class="play player-shell">
        <div class="player-placeholder">请选择分集开始播放</div>
      </div>
    </div>
    <h3>选集（${eps.length}）</h3>
    ${epBtns}`;
}

// 点击选集 -> 原生直连播放（直接播原始 m3u8/mp4，不转流、不缓存、不去广告）
async function playEpisode(epIndex) {
  if (!currentInfo) return;
  const box = document.getElementById('play-result');
  box.innerHTML = '<div class="play player-shell"><div class="meta">获取播放地址中…</div></div>';
  try {
    const resp = await fetch('/api/play?base_url=' + encodeURIComponent(currentInfo.base_url) + '&link=' + encodeURIComponent(currentInfo.link) + '&episode_index=' + epIndex);
    const play = await resp.json();
    if (!resp.ok) {
      box.innerHTML = '<div class="play player-shell"><div class="empty">播放地址获取失败：' + resp.status + '</div></div>';
      return;
    }
    const type = String(play.type || 'm3u8').toLowerCase();
    box.innerHTML = `
      <div class="play">
        <div class="meta">类型：${esc(play.type || 'm3u8')} · 原生直连</div>
        <video id="player" class="player-video" controls></video>
        <div class="hint" id="player-err"></div>
      </div>`;
    if (type === 'mp4') {
      document.getElementById('player').src = play.url;   // mp4 直链
    } else {
      playHls(play.url);                                   // m3u8 交给 hls.js
    }
  } catch (e) {
    box.innerHTML = '<div class="play player-shell"><div class="empty">播放失败：' + esc(e) + '</div></div>';
  }
}

// 用 hls.js 播放 m3u8（Safari 原生回退）
function playHls(hlsUrl) {
  const video = document.getElementById('player');
  const errEl = document.getElementById('player-err');
  const showErr = msg => { if (errEl) errEl.textContent = msg; };
  if (window.Hls && Hls.isSupported()) {
    const hls = new Hls();
    hls.loadSource(hlsUrl);
    hls.attachMedia(video);
    hls.on(Hls.Events.ERROR, function (e, data) { if (data.fatal) showErr('HLS 播放错误：' + data.type); });
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = hlsUrl; // Safari 原生 HLS
  } else {
    showErr('当前浏览器不支持 HLS，请使用支持 HLS 的浏览器');
  }
}

// 返回搜索
function backToSearch() {
  document.getElementById('detail-view').hidden = true;
  document.getElementById('search-view').hidden = false;
  document.getElementById('home-bg').hidden = false;  // 返回搜索恢复背景图片
  currentInfo = null;
}

// 回车触发搜索
document.getElementById('key').addEventListener('keydown', e => {
  if (e.key === 'Enter') doSearch();
});

