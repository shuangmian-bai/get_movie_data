// 默认API基础地址
let API_BASE = localStorage.getItem('apiBaseUrl') || "http://127.0.0.1:8080";
let currentMovies = [];
let isMovieListVisible = true;

// DOM加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
    setupEventListeners();
});

// 初始化应用
function initializeApp() {
    const apiBaseUrlInput = document.getElementById("apiBaseUrl");
    if (apiBaseUrlInput) {
        apiBaseUrlInput.value = API_BASE;
    }
    
    // 设置滚动效果
    window.addEventListener('scroll', () => {
        const navbar = document.querySelector('.navbar');
        if (window.scrollY > 10) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
    });
    
    // 添加移动端切换按钮事件
    const mobileToggleBtn = document.getElementById('mobileToggleBtn');
    if (mobileToggleBtn) {
        mobileToggleBtn.addEventListener('click', toggleMobileView);
    }
    
    // 初始化视图
    updateMobileView();
}

// 设置事件监听器
function setupEventListeners() {
    const searchInput = document.getElementById("searchInput");
    const searchBtn = document.getElementById("searchBtn");
    
    searchBtn.addEventListener("click", searchMovies);
    searchInput.addEventListener("keypress", e => {
        if (e.key === "Enter") searchMovies();
    });
    
    // 监听窗口大小变化
    window.addEventListener('resize', updateMobileView);
}

// 更新移动端视图
function updateMobileView() {
    const moviesSection = document.querySelector('.movies-section');
    const detailsSection = document.querySelector('.details-section');
    const mobileToggleBtn = document.getElementById('mobileToggleBtn');
    
    if (window.innerWidth <= 992) {
        if (isMovieListVisible) {
            moviesSection.style.display = 'block';
            detailsSection.style.display = 'none';
        } else {
            moviesSection.style.display = 'none';
            detailsSection.style.display = 'block';
        }
        if (mobileToggleBtn) mobileToggleBtn.style.display = 'block';
    } else {
        moviesSection.style.display = 'block';
        detailsSection.style.display = 'block';
        if (mobileToggleBtn) mobileToggleBtn.style.display = 'none';
    }
}

// 切换移动端视图
function toggleMobileView() {
    isMovieListVisible = !isMovieListVisible;
    updateMobileView();
    
    const mobileToggleBtn = document.getElementById('mobileToggleBtn');
    if (mobileToggleBtn) {
        mobileToggleBtn.innerHTML = isMovieListVisible ? 
            '<i class="fas fa-film"></i>' : '<i class="fas fa-list"></i>';
    }
}

// 加载数据源选项
async function loadDataSourceOptions() {
    const dataSourceSelect = document.getElementById("dataSourceSelect");
    if (!dataSourceSelect) return;
    
    try {
        const res = await fetch(`${API_BASE}/api/movie/dataSources`);
        const dataSources = await res.json();
        
        dataSources.forEach(source => {
            const option = document.createElement("option");
            option.value = source.id;
            option.textContent = source.name;
            dataSourceSelect.appendChild(option);
        });
    } catch (err) {
        console.error("加载数据源失败:", err);
    }
}

// 搜索电影
async function searchMovies() {
    const keyword = document.getElementById("searchInput").value.trim();
    const searchBtn = document.getElementById("searchBtn");
    
    // 禁用搜索按钮，防止连续点击
    searchBtn.disabled = true;
    const originalText = searchBtn.innerHTML;
    searchBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    
    const sectionTitle = document.getElementById("sectionTitle");
    const resultsList = document.getElementById("resultsList");
    
    if (!keyword) {
        sectionTitle.textContent = "热门影片";
        resultsList.innerHTML = `
            <div class="placeholder-message">
                <p>请输入关键词搜索影片</p>
            </div>
        `;
        // 恢复搜索按钮
        searchBtn.disabled = false;
        searchBtn.innerHTML = originalText;
        return;
    }
    
    sectionTitle.textContent = `搜索结果: ${keyword}`;
    
    try {
        // 直接搜索所有数据源
        const url = `${API_BASE}/api/movie/search/all?keyword=${encodeURIComponent(keyword)}`;
        
        const res = await fetch(url);
        const movies = await res.json();
        currentMovies = movies;
        
        if (movies.length === 0) {
            resultsList.innerHTML = `
                <div class="placeholder-message">
                    <p>未找到相关影片</p>
                </div>
            `;
            // 恢复搜索按钮
            searchBtn.disabled = false;
            searchBtn.innerHTML = originalText;
            return;
        }
        
        renderMovies(movies);
    } catch (err) {
        resultsList.innerHTML = `
            <div class="placeholder-message">
                <p>搜索失败: ${err.message}</p>
            </div>
        `;
    } finally {
        // 恢复搜索按钮
        searchBtn.disabled = false;
        searchBtn.innerHTML = originalText;
    }
}

// 渲染电影列表
function renderMovies(movies) {
    const resultsList = document.getElementById("resultsList");
    resultsList.innerHTML = "";
    
    movies.forEach((movie, index) => {
        const movieCard = document.createElement("div");
        movieCard.className = "movie-card";
        movieCard.innerHTML = `
            <img class="movie-poster" src="${movie.poster || 'https://via.placeholder.com/200x300/333333/CCCCCC?text=No+Image'}" alt="${movie.name}" />
            <div class="movie-info">
                <div class="movie-title">${movie.name}</div>
                <div class="movie-meta">
                    <span>${movie.year || '未知年份'}</span>
                    <span>${movie.duration || '未知时长'}</span>
                </div>
                <div class="movie-source">来源: ${new URL(movie.baseUrl).hostname}</div>
            </div>
        `;
        movieCard.addEventListener("click", () => {
            loadMovieDetail(movie);
            // 在移动端点击电影卡片后切换到详情视图
            if (window.innerWidth <= 992) {
                isMovieListVisible = false;
                updateMobileView();
                document.getElementById('mobileToggleBtn').innerHTML = '<i class="fas fa-list"></i>';
            }
        });
        resultsList.appendChild(movieCard);
    });
}

// 加载电影详情
async function loadMovieDetail(movie) {
    const { baseUrl, playUrl } = movie;
    const movieDetail = document.getElementById("movieDetail");
    
    try {
        // 获取剧集列表
        const res = await fetch(`${API_BASE}/api/movie/episodes?baseUrl=${encodeURIComponent(baseUrl)}&playUrl=${encodeURIComponent(playUrl)}`);
        const episodes = await res.json();
        
        // 渲染详情
        movieDetail.innerHTML = `
            <div class="movie-detail-content">
                <div class="detail-header">
                    <img class="detail-poster" src="${movie.poster || 'https://via.placeholder.com/200x300/333333/CCCCCC?text=No+Image'}" alt="${movie.name}" />
                    <div class="detail-info">
                        <h2 class="detail-title">${movie.name}</h2>
                        <div class="detail-meta">
                            <span>${movie.year || '未知年份'}</span>
                            <span>${movie.duration || '未知时长'}</span>
                            <span>${movie.finished ? '已完结' : '连载中'}</span>
                        </div>
                        <p class="detail-description">${movie.description || '暂无简介'}</p>
                    </div>
                </div>
                
                <div class="episode-section">
                    <h3>剧集列表 (${episodes.length} 集)</h3>
                    <div class="episode-list"></div>
                </div>
                
                <div class="video-section">
                    <h3>播放器</h3>
                    <video id="videoPlayer" controls></video>
                </div>
            </div>
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
                    const m3u8Data = await m3u8Res.json();
                    const m3u8Url = m3u8Data.movie;
                    
                    if (m3u8Url && m3u8Url.includes(".m3u8")) {
                        if (Hls.isSupported()) {
                            if (video.hls) {
                                video.hls.destroy();
                            }
                            const hls = new Hls();
                            hls.loadSource(m3u8Url);
                            hls.attachMedia(video);
                            hls.on(Hls.Events.MANIFEST_PARSED, () => video.play());
                            video.hls = hls;
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
        movieDetail.innerHTML = `
            <div class="placeholder-message">
                <p>加载详情失败: ${err.message}</p>
            </div>
        `;
    }
}