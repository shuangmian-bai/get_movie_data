const API_BASE = "http://47.121.119.43:8080"; // 你的后端地址
const searchInput = document.getElementById("searchInput");
const searchBtn = document.getElementById("searchBtn");
const resultsList = document.getElementById("resultsList");
const movieDetail = document.getElementById("movieDetail");

// 搜索电影
async function searchMovies() {
    const keyword = searchInput.value.trim();
    if (!keyword) return alert("请输入关键词");

    try {
        const res = await fetch(`${API_BASE}/api/movie/search/all?keyword=${encodeURIComponent(keyword)}`);
        const movies = await res.json();

        if (movies.length === 0) {
            resultsList.innerHTML = "<p>未找到相关影片</p>";
            return;
        }

        resultsList.innerHTML = "";
        movies.forEach(movie => {
            const item = document.createElement("div");
            item.className = "movie-item";
            item.innerHTML = `
        <h3>${movie.name}</h3>
        <p>${movie.description || '暂无简介'}</p>
        <p class="source">来源: ${new URL(movie.baseUrl).hostname}</p>
      `;
            item.onclick = () => loadMovieDetail(movie);
            resultsList.appendChild(item);
        });
    } catch (err) {
        resultsList.innerHTML = `<p style="color:red;">搜索失败: ${err.message}</p>`;
    }
}

// 加载电影详情
async function loadMovieDetail(movie) {
    const { baseUrl, playUrl } = movie;

    try {
        // 获取剧集列表
        const res = await fetch(`${API_BASE}/api/movie/episodes?baseUrl=${encodeURIComponent(baseUrl)}&playUrl=${encodeURIComponent(playUrl)}`);
        const episodes = await res.json();

        // 渲染右侧详情
        movieDetail.innerHTML = `
      <div style="display:flex;gap:20px;align-items:flex-start;">
        <img class="movie-poster" src="${movie.poster || 'https://via.placeholder.com/150'}" alt="${movie.name}" />
        <div class="movie-info">
          <h2>${movie.name}</h2>
          <p><strong>状态:</strong> ${movie.finished ? '已完结' : '连载中'}</p>
          <p><strong>集数:</strong> ${movie.episodes || episodes.length}</p>
          <p>${movie.description || '暂无简介'}</p>
        </div>
      </div>
      <h3>剧集列表</h3>
      <div class="episode-list"></div>
      <video id="videoPlayer" controls></video>
    `;

        const episodeList = document.querySelector(".episode-list");
        const video = document.getElementById("videoPlayer");

        episodes.forEach(ep => {
            const btn = document.createElement("button");
            btn.className = "episode-btn";
            btn.textContent = ep.title;
            btn.onclick = async () => {
                try {
                    const m3u8Res = await fetch(`${API_BASE}/api/movie/m3u8?baseUrl=${encodeURIComponent(baseUrl)}&episodeUrl=${encodeURIComponent(ep.episodeUrl)}`);
                    const m3u8Url = await m3u8Res.text();

                    if (m3u8Url && m3u8Url.includes(".m3u8")) {
                        if (Hls.isSupported()) {
                            const hls = new Hls();
                            hls.loadSource(m3u8Url);
                            hls.attachMedia(video);
                            hls.on(Hls.Events.MANIFEST_PARSED, () => video.play());
                        } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
                            video.src = m3u8Url;
                            video.addEventListener("loadedmetadata", () => video.play());
                        } else {
                            alert("当前浏览器不支持M3U8播放");
                        }
                    } else {
                        alert("未能获取有效的播放地址");
                    }
                } catch (err) {
                    alert("获取播放地址失败: " + err.message);
                }
            };
            episodeList.appendChild(btn);
        });

    } catch (err) {
        movieDetail.innerHTML = `<p style="color:red;">加载详情失败: ${err.message}</p>`;
    }
}

// 事件绑定
searchBtn.addEventListener("click", searchMovies);
searchInput.addEventListener("keypress", e => {
    if (e.key === "Enter") searchMovies();
});