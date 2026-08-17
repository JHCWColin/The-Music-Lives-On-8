<script>
    // ============ 方案二：独立键名（每个ID单独存） ============
    const FlagDB2 = {
        // 生成唯一键名（统一加前缀，方便管理）
        _getKey(id) {
            return "flag_" + id; // 例如：flag_1001
        },

        // 1. 存/改：设置某个ID的状态
        set(id, value) {
            localStorage.setItem(this._getKey(id), JSON.stringify(value));
        },

        // 2. 查：获取某个ID的状态
        //    未设置返回 undefined，设置过返回 true/false
        get(id) {
            const val = localStorage.getItem(this._getKey(id));
            return val !== null ? JSON.parse(val) : undefined;
        },

        // 3. 删：删除某个ID的记录
        delete(id) {
            localStorage.removeItem(this._getKey(id));
        },

        // 4. 清空：删除所有 flag_ 开头的键（批量清空）
        clear() {
            const keysToRemove = [];
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                if (key && key.startsWith("flag_")) {
                    keysToRemove.push(key);
                }
            }
            keysToRemove.forEach((key) => localStorage.removeItem(key));
        },
    };

    import { tick } from "svelte";
    import { page } from "./page.svelte.js";
    import { fade } from "svelte/transition";
    import { invoke } from "@tauri-apps/api/core";
    let result = $state([]);
    let name = $state();
    let limit = $state(30);
    let showInofOfStatus = $state(false);
    let isBusy = $state(false);
    let isSearch = $state(false);
    let reset = $state(false);
    let queue = [];
    let searchInfo = $state("");
    let br = $state(+localStorage.getItem("br") || 320);
    let availableBr = [2000, 320, 192, 128];
    let searchType = $state("单曲");
    let availablesSearchType = ["单曲", "歌单"];

    const listeners = []; // 记录 { target, type, listener, options }
    const observers = []; // 记录 IntersectionObserver 实例

    const origAdd = EventTarget.prototype.addEventListener;
    const origObserver = window.IntersectionObserver;

    async function search() {
        isSearch = true;
        result = [];
        searchInfo = "";
        const url = `https://api.qijieya.cn/meting/?type=search&id=${encodeURIComponent(name)}&limit=${limit}&server=netease`;
        try {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP 错误! 状态码: ${response.status}`);
            }
            result = await response.json();
        } catch (error) {
            searchInfo = error.message || JSON.stringify(error);
        }
        isSearch = false;
    }
    async function searchPlaylist() {
        isSearch = true;
        result = [];
        searchInfo = "";
        try {
            const jsonString = await invoke("cloud_search", {
                keywords: name,
                t: "1000",
                limit: "" + limit,
            });
            result = JSON.parse(jsonString);
        } catch (error) {
            searchInfo =
                typeof error === "string"
                    ? error
                    : error.message || JSON.stringify(error);
        }
        isSearch = false;
    }
    async function getDetail(a) {
        const url = `https://api.qijieya.cn/meting/?type=playlist&id=${a.id}`;
        try {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP 错误! 状态码: ${response.status}`);
            }
            a["详情"] = await response.json();
        } catch (error) {
            a["详情"] = error.message || JSON.stringify(error);
        }
    }
    function sanitizeFileName(name) {
        if (typeof name !== "string") return "";

        // 第一步：将 / 和 \ 全部替换为 &（and 符号）
        let result = name.replace(/[\\\/]/g, "&");

        // 第二步：移除其他非法字符（如 : * ? " < > |）
        // 注意：& 已被保留，不会在这里被移除
        result = result.replace(/[:*?"<>|]/g, "");

        // 第三步：去除首尾空格（Windows 不允许文件名以空格结尾）
        return result.trim();
    }
    function getId(url) {
        const parsed = new URL(url);
        return parsed.searchParams.get("id");
    }

    function addQueue(a) {
        if (queue.length === 0) {
            queue.push(a);
            doWork();
        } else {
            a.status = "waiting";
            queue.push(a);
        }
    }

    function downloadAll(result) {
        for (let i = 0; i < result.length; i++) {
            if (
                result[i].status != "Done" &&
                result[i].status != "waiting" &&
                result[i].status != "downloading" &&
                FlagDB2.get(getId(result[i].url)) !== true
            ) {
                addQueue(result[i]);
            }
        }
    }

    async function doWork() {
        isBusy = true;
        while (queue.length > 0) {
            try {
                await download(queue[0]);
            } catch (error) {
                queue[0].status = error.message || JSON.stringify(error);
            }
            queue.shift();
        }
        isBusy = false;
    }

    async function download(u) {
        if (FlagDB2.get(getId(u.url)) === true) {
            u.status = "下过了";
            return;
        }
        u.status = "downloading";
        console.log(u.url + `&br=${br}`);
        const status = await invoke("download_file_async_without_redirect", {
            url: u.url + `&br=${br}`,
            name: sanitizeFileName(u.name + "-" + u.artist),
        });
        if (status === "Done") {
            FlagDB2.set(getId(u.url), true);
        }
        u.status = status;
    }
    async function getTopPlaylist() {
        isSearch = true;
        result = [];
        searchInfo = "";
        try {
            const jsonString = await invoke("top_playlist", {
                limit: "" + limit,
            });
            result = { result: JSON.parse(jsonString) };
        } catch (error) {
            searchInfo =
                typeof error === "string"
                    ? error
                    : error.message || JSON.stringify(error);
        }
        isSearch = false;
    }
    async function getHotPlaylist() {
        isSearch = true;
        result = [];
        searchInfo = "";
        try {
            const jsonString = await invoke("playlist_hot");
            const data = JSON.parse(jsonString);
            result = { result: { playlists: data.tags } };
            console.log(result);
        } catch (error) {
            searchInfo =
                typeof error === "string"
                    ? error
                    : error.message || JSON.stringify(error);
        }
        isSearch = false;
    }

    let clean = $state();
    $effect(() => {
        // 依赖数据，当 playlists 有内容时（重新）初始化
        if (
            result?.result?.playlists?.length > 0 &&
            !clean &&
            searchType === "歌单"
        ) {
            tick().then(() => {
                clean = createCleanupWrapper();
            });
        }
        if (result.length === 0) {
            tick().then(() => {
                if (clean) {
                    clean();
                    clean = "";
                }
            });
        }
    });

    function createCleanupWrapper() {
        // 劫持 addEventListener
        EventTarget.prototype.addEventListener = function (
            type,
            listener,
            options,
        ) {
            listeners.push({ target: this, type, listener, options });
            return origAdd.call(this, type, listener, options);
        };

        // 劫持 IntersectionObserver 构造函数
        window.IntersectionObserver = class extends origObserver {
            constructor(callback, options) {
                super(callback, options);
                observers.push(this);
            }
        };

        // 执行初始化
        const documentBody = document.querySelector("body");
        const navRoots = documentBody.querySelectorAll(".p-in-page-navigation");
        navRoots.forEach((navRoot) => {
            buildInPageNavigation(navRoot);
            initNavigationInteraction(navRoot);
        });

        // 恢复原方法
        EventTarget.prototype.addEventListener = origAdd;
        window.IntersectionObserver = origObserver;

        // 返回销毁函数
        return () => {
            // 移除所有事件监听
            listeners.forEach(({ target, type, listener, options }) => {
                target.removeEventListener(type, listener, options);
            });
            // 断开所有观察器
            observers.forEach((obs) => obs.disconnect());
        };
    }

    /**
     * Init:
     *  - Generates in-page navigation if scope is set to "full-page".
     *  - Initializes navigation interactions.
     */

    /**
     * Build the navigation list from page headings.
     * @param {HTMLElement} navRoot - The .p-in-page-navigation element
     */
    function buildInPageNavigation(navRoot) {
        if (!navRoot) {
            return;
        }

        // If not full-page, assume manual navigation structure
        const scope = navRoot.dataset.inPageNavigationScope;
        if (scope !== "full-page") {
            return;
        }

        const selectors = generateSelectors(navRoot);
        const headings = getHeadings(navRoot, selectors);
        if (!headings.length) {
            return;
        }

        const navList = navRoot.querySelector(".js-in-page-nav-list");
        const itemTemplate = document.querySelector(
            ".js-in-page-nav-template-item",
        );
        const sublistTemplate = document.querySelector(
            ".js-in-page-nav-template-sublist",
        );
        let currentPrimaryItem = null;
        let currentNestedList = null;
        let isFirst = true;

        headings.forEach((heading) => {
            const id = generateHeadingId(heading);
            const text = heading.textContent.trim().replace(/\s+/g, " "); // Remove whitespace
            const tooltipId = `${id}-tooltip`;
            const isPrimaryList = heading.matches(selectors.primarySelector);
            const itemClone = itemTemplate.content.cloneNode(true);
            const li = itemClone.querySelector("li");
            const link = itemClone.querySelector("a");
            const tooltipWrapper = itemClone.querySelector(".p-tooltip--right");
            const tooltipMessage = itemClone.querySelector(
                ".p-tooltip__message",
            );

            link.href = `#${id}`;
            link.textContent = text;
            tooltipWrapper.setAttribute("aria-describedby", tooltipId);
            tooltipMessage.id = tooltipId;
            tooltipMessage.textContent = text;

            if (isFirst) {
                link.classList.add("is-active");
                isFirst = false;
            }

            if (isPrimaryList) {
                // Append to main list
                navList.appendChild(li);
                currentPrimaryItem = li;
                currentNestedList = null;
            } else if (selectors.secondarySelector && currentPrimaryItem) {
                // Append to sublist under current primary list
                if (!currentNestedList) {
                    const nestedListClone =
                        sublistTemplate.content.cloneNode(true);
                    currentNestedList = nestedListClone.querySelector("ul");
                    currentPrimaryItem.appendChild(currentNestedList);
                }
                if (currentNestedList) {
                    currentNestedList.appendChild(li);
                }
            } else {
                navList.appendChild(li);
            }
        });
    }

    /**
     * Initializes navigation interactions:
     *  - Dropdown toggle for mobile view
     *  - Intersection observer to highlight active section in navigation
     * @param {HTMLElement} navRoot - The .p-in-page-navigation element
     */
    function initNavigationInteraction(navRoot) {
        const toggle = navRoot.querySelector(
            ".p-in-page-navigation__dropdown-toggle",
        );
        const navList = navRoot.querySelector(".js-in-page-nav-list");

        if (toggle && navList) {
            toggle.addEventListener("click", function () {
                if (toggle.getAttribute("aria-expanded") === "true") {
                    navRoot.classList.remove("is-expanded");
                    toggle.setAttribute("aria-expanded", "false");
                    navList.setAttribute("aria-expanded", "false");
                    toggle
                        .querySelector(".p-icon--chevron-down")
                        .classList.remove("u-hide");
                    toggle
                        .querySelector(".p-icon--chevron-up")
                        .classList.add("u-hide");
                    // Ensure active item is visible in horizontal layout
                    scrollActiveNavItemIntoView(link);
                } else {
                    navRoot.classList.add("is-expanded");
                    toggle.setAttribute("aria-expanded", "true");
                    navList.setAttribute("aria-expanded", "true");
                    toggle
                        .querySelector(".p-icon--chevron-down")
                        .classList.add("u-hide");
                    toggle
                        .querySelector(".p-icon--chevron-up")
                        .classList.remove("u-hide");
                }
            });
        }

        const selectors = generateSelectors(navRoot);
        const headings = getHeadings(navRoot, selectors);
        const navigationLinks = getNavigationLinks(navRoot);

        /**
         * Updates the active navigation link based on the given heading ID.
         * Also scrolls the active link into view if it's in horizontal layout.
         * @param {string} headingId - The ID of the currently active heading.
         */
        function updateActiveLink(headingId) {
            const targetLink = navRoot.querySelector(`a[href='#${headingId}']`);
            // Ignore links that are hidden in horizontal layout
            const parentList = targetLink
                ? targetLink.closest(".p-in-page-navigation__list")
                : null;

            if (
                !targetLink ||
                !parentList ||
                window.getComputedStyle(parentList, null).display === "none"
            ) {
                return;
            }

            navigationLinks.forEach((link) => {
                if (link.getAttribute("href") === `#${headingId}`) {
                    link.classList.add("is-active");
                    scrollActiveNavItemIntoView(link);
                } else {
                    link.classList.remove("is-active");
                }
            });
        }

        const BREAKPOINTLARGE = 1036;
        let observer;
        let lastViewportState = null;

        function manageObserver() {
            const isLargeView = getCurrentViewportWidth() >= BREAKPOINTLARGE;

            // Prevent recreating the observer if state hasn't changed
            if (lastViewportState === isLargeView) {
                return;
            }
            lastViewportState = isLargeView;

            // Cleanup existing observer
            if (observer) observer.disconnect();

            observer = new IntersectionObserver(
                function (entries) {
                    if (
                        typeof navItemClicked !== "undefined" &&
                        navItemClicked
                    ) {
                        return;
                    }
                    entries.forEach((entry) => {
                        if (entry.isIntersecting) {
                            updateActiveLink(entry.target.id);
                        }
                    });
                },
                {
                    rootMargin: isLargeView
                        ? "0px 0px -80% 0px"
                        : "-10% 0px -75% 0px",
                    threshold: 0,
                },
            );

            headings.forEach((heading) => observer.observe(heading));
        }

        // Initialize observer
        manageObserver();

        // Update observer rootMargins on viewport resize
        window.addEventListener("resize", debounce(manageObserver, 250));

        // Handle navigation link clicks
        let navItemClicked = false;
        navigationLinks.forEach(function (link) {
            link.addEventListener("click", function (e) {
                e.preventDefault();
                navItemClicked = true;

                // Handle active state
                navigationLinks.forEach(function (navLink) {
                    navLink.classList.remove("is-active");
                });
                link.classList.add("is-active");

                // Handle smooth scroll
                const targetId = link.getAttribute("href");
                const targetHeading = document.querySelector(targetId);

                (function () {
                    navRoot.classList.remove("is-expanded");
                    toggle.setAttribute("aria-expanded", "false");
                    navList.setAttribute("aria-expanded", "false");
                    toggle
                        .querySelector(".p-icon--chevron-down")
                        .classList.remove("u-hide");
                    toggle
                        .querySelector(".p-icon--chevron-up")
                        .classList.add("u-hide");
                })();
                setTimeout(() =>{
                    if (targetHeading) {
                    targetHeading.setAttribute("tabindex", "-1");
                    targetHeading.focus({
                        preventScroll: true,
                    });
                    targetHeading.scrollIntoView({
                        behavior: "smooth",
                    });
                    //history.pushState(null, null, targetId);
                }
                },50);
                setTimeout(() => {
                    // Ensure active item is visible in horizontal layout
                    scrollActiveNavItemIntoView(link);
                }, 150);
                
                // We use a timeout to prevent the IntersectionObserver from immediately
                // overriding the active state. As the IntersectionObserver points at the
                // center of the screen
                setTimeout(() => {
                    navItemClicked = false;
                }, 1000);
            });
        });

        // Show tooltip for links that span more than 2
        navigationLinks.forEach(function (link) {
            if (spansMoreThanTwoLines(link)) {
                const linkContainer = link.parentNode;
                const tooltip = linkContainer.querySelector(
                    ".p-tooltip__message",
                );
                tooltip.classList.remove("u-hide");
                attachPositionTooltipListener(linkContainer);
            }
        });
    }

    // Helper functions

    /**
     * Returns headings to be included in navigation.
     * @param {HTMLElement} navRoot
     * @returns {NodeList} List of heading elements matching the defined selectors
     */
    function getHeadings(navRoot, selectors) {
        const headings = Array.from(document.querySelectorAll(selectors.query));
        const excludes = getHeadingExcludes(navRoot, headings);
        return headings.filter((heading) => !excludes.includes(heading));
    }

    /**
     * Parse exclusion rules and return a list of excluded heading elements.
     * Supports two formats:
     *   - CSS selector (e.g. "#some-id", ".some-class")
     *   - Text match (e.g. "text:Newsletter signup") - case-insensitive
     * @param {HTMLElement} navRoot
     * @param {NodeList} headings - all headings being considered
     * @returns {HTMLElement[]} List of heading elements to exclude
     */
    function getHeadingExcludes(navRoot, headings) {
        const excludeAttr = navRoot.dataset.inPageNavigationExcludes;
        if (!excludeAttr) {
            return [];
        }

        const excludeRules = excludeAttr
            .trim()
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean);
        const excludeList = [];

        excludeRules.forEach((rule) => {
            if (rule.toLowerCase().startsWith("text:")) {
                // Text-based exclusion
                const textToMatch = rule.split("text:")[1].trim().toLowerCase();
                headings.forEach((heading) => {
                    if (
                        heading.textContent.trim().toLowerCase() === textToMatch
                    ) {
                        excludeList.push(heading);
                    }
                });
            } else {
                // CSS selector exclusion
                try {
                    const matched = document.querySelector(rule);
                    if (matched) {
                        excludeList.push(matched);
                    }
                } catch (e) {
                    console.warn(
                        `In-page navigation: Invalid exclude selector "${rule}"`,
                    );
                }
            }
        });
        return excludeList;
    }

    /**
     * Generate CSS selectors for query based heading retrieval.
     * @param {HTMLElement} navRoot
     * @returns {Object} An object containing primarySelector, secondarySelector, and a query string
     */
    function generateSelectors(navRoot) {
        const primarySelector = navRoot.dataset.inPageNavigationPrimary;
        const secondarySelector =
            navRoot.dataset.inPageNavigationSecondary ?? null;
        const query = secondarySelector
            ? `${primarySelector}, ${secondarySelector}`
            : primarySelector;
        return {
            primarySelector,
            secondarySelector,
            query,
        };
    }

    /**
     * Checks for heading ID and generates on from text content if missing
     * @param {HTMLElement} heading
     * @returns {string} The heading ID
     */
    function generateHeadingId(heading) {
        if (heading.id && !document.getElementById(heading.id)) {
            return heading.id;
        }

        let baseId = slugify(heading.textContent);
        let id = baseId;

        // Handle duplicate IDs
        let counter = 1;
        while (document.getElementById(id)) {
            appendix = counter == 1 ? "" : `-${counter}`;
            id = baseId + appendix;
            counter++;
        }

        heading.id = id;
        return id;
    }

    /**
     * Returns all navigation links within the given navigation root.
     * @param {HTMLElement} navRoot
     * @returns {NodeList} List of navigation link elements
     */
    function getNavigationLinks(navRoot) {
        return navRoot.querySelectorAll(".p-in-page-navigation__link");
    }

    /**
     * Converts a string to a href friendly slug.
     * @param {string} text - The text content of the heading
     * @return {string} A slugified version of the text
     */
    function slugify(text) {
        return text
            .toString()
            .toLowerCase()
            .trim()
            .replace(/\s+/g, "-")
            .replace(/[^\w-]+/g, "")
            .replace(/--+/g, "-")
            .replace(/^-+/, "")
            .replace(/-+$/, "");
    }

    /**
     * Checks if the content of an element would span more than 2 lines if not truncated
     * To do this we have to recreate the original height without clamping
     * and then check the number of lines.
     * @param {HTMLElement} element
     * @returns {boolean} True if the content would be more than 2 lines, else false.
     */
    function spansMoreThanTwoLines(element) {
        const originalDisplay = element.style.display;
        const originalLineClamp = element.style.webkitLineClamp;

        element.style.display = "block";
        element.style.webkitLineClamp = "unset";

        const style = window.getComputedStyle(element);
        const lineHeight = parseFloat(style.lineHeight);
        const height = element.getBoundingClientRect().height;

        // Restore original styles
        element.style.display = originalDisplay;
        element.style.webkitLineClamp = originalLineClamp;

        return height > lineHeight * 3;
    }

    /**
     * Scrolls the active navigation item into view in horizontal layout.
     * @param {HTMLElement} link - The active navigation link
     */
    function scrollActiveNavItemIntoViewOld(link) {
        if (!link) {
            link = document.querySelector(
                ".p-in-page-navigation__link.is-active",
            );
        }
        const listItem = link.closest(".p-in-page-navigation__item");
        if (listItem) {
            listItem.scrollIntoView({
                behavior: "smooth",
                block: "nearest",
                inline: "start",
            });
        }
    }

    /**
     * Returns the current viewport width.
     * @returns {number} The width of the viewport in pixels
     */
    function getCurrentViewportWidth() {
        return Math.max(
            document.documentElement.clientWidth || 0,
            window.innerWidth || 0,
        );
    }

    /**
     * Position tooltips in scrollable navigation using fixed positioning.
     * This is is a workaround as the tooltips are contained in an overflow:auto container
     * that allows the navigation to be scrollable. This means the tooltip would usually get clipped.
     * So we manually position the tooltip with JS. If JS is disabled, so are tooltips.
     * @param {HTMLElement} tooltipContainer - The .p-tooltip element
     */
    function attachPositionTooltipListener(tooltipContainer) {
        const tooltipMessage = tooltipContainer.querySelector(
            ".p-tooltip__message",
        );
        if (!tooltipMessage) return;

        // One hover update the tooltip position property to be used in CSS
        tooltipContainer.addEventListener("mouseenter", function () {
            const rect = tooltipContainer.getBoundingClientRect();
            tooltipMessage.style.setProperty(
                "--tooltip-left",
                `${rect.right + 8}px`,
            );
            tooltipMessage.style.setProperty(
                "--tooltip-top",
                `${rect.top + 24}px`,
            );
        });
    }

    /**
     * Debounce helper
     * @param {Function} func - The function to debounce
     * @param {number} wait - The debounce delay in milliseconds
     * @return {Function} A debounced version of the input function
     */
    function debounce(func, wait) {
        let timeout;
        return function (...args) {
            const context = this;
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(context, args), wait);
        };
    }

    let scrollTimer = null;
    let pendingLink = null;

    function onScrollStop() {
        // 滚动停止 150ms 后执行
        if (pendingLink) {
            scrollActiveNavItemIntoViewOld(pendingLink);
            pendingLink = null;
        }
        // 任务完成，移除 scroll 监听，彻底清空
        window.removeEventListener("scroll", onScroll);
        scrollTimer = null;
    }

    function onScroll() {
        // 每次滚动都重置“停止检测”定时器
        clearTimeout(scrollTimer);
        scrollTimer = setTimeout(onScrollStop, 150);
    }

    function scrollActiveNavItemIntoView(link) {
        // 保存最新的 link
        pendingLink = link;

        // 如果 scroll 监听还没加上，就加上
        if (!scrollTimer) {
            window.addEventListener("scroll", onScroll, { passive: true });
            // 立刻启动一次“停止检测”定时器，防止用户调用后完全不滚动
            scrollTimer = setTimeout(onScrollStop, 150);
        }
    }
</script>

<style>
.h {
    scroll-margin-top: 80px;
}
</style>

<div style="margin-bottom: 1rem;">
    <button
        class="p-button"
        on:click={() => {
            page.num = 0;
        }}
        disabled={isBusy || isSearch}>← 返回</button
    >
</div>
<div class="p-card--highlighted" style="padding: 2rem;">
    <form class="p-search-box" on:submit|preventDefault={() => {}}>
        <label class="u-off-screen" for="search">Search</label>
        <input
            type="search"
            id="search"
            class="p-search-box__input"
            placeholder="点击输入…"
            bind:value={name}
        />
        <button type="reset" class="p-search-box__reset">
            <i class="p-icon--close" on:click={()=>{document.getElementById("search")?.focus()}}>Close</i>
        </button>
        {#if searchType === "单曲"}
            <button
                type="submit"
                class="p-search-box__button"
                on:click={search}
                disabled={isBusy || isSearch}
            >
                <i class="p-icon--search">Search</i>
            </button>
        {:else if searchType === "歌单"}
            <button
                type="submit"
                class="p-search-box__button"
                on:click={searchPlaylist}
                disabled={isBusy || isSearch}
            >
                <i class="p-icon--search">Search</i>
            </button>
        {/if}
    </form>
    <div>
        <p style="margin-bottom:0px;">
            搜索种类：
            {#each availablesSearchType as current}
                {#if searchType === current}
                    <button
                        class="p-button--positive"
                        on:click={() => {
                            searchType = current;
                        }}
                        disabled={isBusy || isSearch}>{current}</button
                    >
                {:else}
                    <button
                        class="p-button"
                        on:click={() => {
                            searchType = current;
                            result = [];
                        }}
                        disabled={isBusy || isSearch}>{current}</button
                    >
                {/if}
            {/each}
        </p>
    </div>
    <div class="p-slider__wrapper" style="margin-bottom: 0.5rem;">
        <div style="white-space: nowrap;">搜索个数：</div>
        <input
            type="range"
            min="1"
            max="100"
            bind:value={limit}
            step="1"
            id="slider3"
            aria-label="Example slider, range 0 to 100"
        />
        <input
            class="p-slider__input"
            type="text"
            maxlength="3"
            id="slider3-input"
            tabindex="0"
        />
    </div>
    <div>
        <p style="margin-bottom:0px;">
            下载码率（kbps）：
            {#each availableBr as current}
                {#if br === current}
                    <button
                        class="p-button--positive"
                        on:click={() => {
                            br = current;
                            localStorage.setItem("br", current);
                        }}>{current}</button
                    >
                {:else}
                    <button
                        class="p-button"
                        on:click={() => {
                            br = current;
                            localStorage.setItem("br", current);
                        }}>{current}</button
                    >
                {/if}
            {/each}
        </p>
    </div>
    <div>
        <div
            style="cursor: pointer; user-select: none; display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0;"
            on:click={() => {
                showInofOfStatus = !showInofOfStatus;
            }}
        >
            <strong>关于下载记录</strong>
            <span>{showInofOfStatus ? "▾" : "▸"}</span>
        </div>
        {#if showInofOfStatus}
            <div
                in:fade={{ duration: 400 }}
                style="padding-left: 1rem; color: #666;"
            >
                <p>
                    仅记录哪个下载了，以标记为“下过了”，避免重复下载。不同板块记录不互通。重置后立即生效，此操作不可逆！
                </p>
                <p>
                    <button
                        class="p-button--negative"
                        on:click={() => {
                            FlagDB2.clear();
                            reset = !reset;
                        }}>重置全部</button
                    >
                    <button
                        class="p-button--negative"
                        on:click={() => {
                            if (searchType === "单曲") {
                                for (let i = 0; i < result.length; i++) {
                                    FlagDB2.delete(getId(result[i].url));
                                }
                            } else {
                                result?.result?.playlists?.forEach(playlist => {
                                    playlist?.["详情"]?.forEach(item => {
                                        FlagDB2.delete(getId(item.url));
                                    });
                                });
                            }
                            reset = !reset;
                        }}
                    >
                        重置本页
                    </button>
                </p>
            </div>
        {/if}
    </div>
    <script>
        var isWebkit =
            /Chrome/i.test(navigator.userAgent) ||
            /Safari/i.test(navigator.userAgent);

        var PROGRESS_COLOUR = "#06c";
        var EMPTY_COLOUR = "#D9D9D9";

        function renderSlider(slider) {
            if (isWebkit) {
                var value =
                    (slider.value - slider.min) / (slider.max - slider.min);
                slider.style.backgroundImage =
                    "-webkit-gradient(linear, left top, right top, color-stop(" +
                    value +
                    ", " +
                    PROGRESS_COLOUR +
                    "), color-stop(" +
                    value +
                    ", " +
                    EMPTY_COLOUR +
                    "))";
            }
        }

        function equaliseValues(receive, give) {
            receive.value = give.value;
            give.value = receive.value;
        }

        /**
  Attaches change listener to sliders to update their background color.
  @param {HTMLElement} slider Slider element to render background on.
*/
        function initSlider(slider) {
            var input = document.getElementById(slider.id + "-input");
            renderSlider(slider);

            if (input) {
                // Synchronise values of input and slider
                equaliseValues(input, slider);
                input.addEventListener("input", function () {
                    if (!input.value) {
                        input.value = 0;
                    }
                    equaliseValues(slider, input);
                    renderSlider(slider);
                });
            }

            slider.addEventListener("input", function () {
                if (input) {
                    equaliseValues(input, slider);
                }
                renderSlider(slider);
            });
        }

        // Setup all sliders on the page.
        var sliders = document.querySelectorAll("input[type=range]");

        for (var i = 0, l = sliders.length; i < l; i++) {
            initSlider(sliders[i]);
        }
    </script>
    {#if searchInfo}
        <div class="p-notification--negative">
            <div class="p-notification__content">
                <h5 class="p-notification__title">搜索失败</h5>
                <p class="p-notification__message">{searchInfo}</p>
            </div>
        </div>
    {/if}

    {#if result.length === 0 && searchType === "歌单" && isSearch === false}
        <div class="p-notification--information">
            <div class="p-notification__content">
                <h5 class="p-notification__title">搜索歌单</h5>
                <p class="p-notification__message">
                    搜索歌单的API由本地提供，你可以搜索歌单名和创建者的名字<br/>
                    你也可以查看：<br /><br />
                    <button class="p-button" on:click={getHotPlaylist} disabled={isBusy || isSearch}>
                        官方热门歌单
                    </button>
                    <button class="p-button" on:click={getTopPlaylist} disabled={isBusy || isSearch}>
                        网友精选
                    </button>
                </p>
            </div>
        </div>
    {/if}
</div>

{#if result.length !== 0}
    {#if searchType === "单曲"}
        <table style="margin-top:2rem;">
            <thead>
                <tr>
                    <th class="p-heading--5">歌曲名</th>
                    <th class="p-heading--5">歌手</th>
                    <th>
                        <button style="margin-bottom:0px;" class="p-button--positive" on:click={() => { downloadAll(result); }}>
                            全部下载
                        </button>
                    </th>
                </tr>
            </thead>
            <tbody>
                {#key reset}
                    {#each result ?? [] as a}
                        <tr>
                            <th>{a.name}</th>
                            <td>{a.artist}</td>
                            <td>
                                {#if a.status == "Done"}
                                    <div class="p-status-label--positive">
                                        成功
                                    </div>
                                {:else if FlagDB2.get(getId(a.url)) === true || a.status === "下过了"}
                                    <div class="p-status-label--positive">
                                        下过了
                                    </div>
                                {:else if !a.status}
                                    <button style="margin-bottom:0px;" class="p-button" on:click={() => { addQueue(a); }}>
                                        下载
                                    </button >
                                {:else if a.status == "downloading"}
                                    <div class="p-status-label--information">
                                        下载中
                                    </div>
                                {:else if a.status == "waiting"}
                                    <div class="p-status-label">等待中</div>
                                {:else}
                                    <button style="margin-bottom:0px;" class="p-button--negative" on:click={() => { addQueue(a); }}>
                                        重试
                                    </button>
                                {/if}
                            </td>
                        </tr>
                        {#if FlagDB2.get(getId(a.url)) !== true && !!a.status && a.status !== "downloading" && a.status !== "waiting" && a.status !== "Done"}
                            <tr style="border-top:0">
                                <td colspan="3">
                                    <div class="p-notification--negative">
                                        <div class="p-notification__content">
                                            <h5 class="p-notification__title">
                                                出错了
                                            </h5>
                                            <p class="p-notification__message">
                                                {a.status}
                                            </p>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        {/if}
                    {/each}
                {/key}
            </tbody>
        </table>
    {/if}

    {#if searchType === "歌单"}
        <div class="grid-row">
            <div class="grid-col-2">
                <div class="p-in-page-navigation" data-in-page-navigation-scope="manual" data-in-page-navigation-primary=".h" data-in-page-navigation-excludes=",.p-in-page-navigation__heading" >
                    <nav class="p-in-page-navigation__container" aria-label="In-page navigation" style="padding-top: 1rem;">
                        <ul class="p-in-page-navigation__list js-in-page-nav-list" id="in-page-navigation-list" aria-expanded="false">
                            {#each result?.result?.playlists ?? [] as playlist}
                                <li class="p-in-page-navigation__item p-tooltip--right" aria-describedby="h{playlist.id}-tooltip" >
                                    <a class="p-in-page-navigation__link" href="#h{playlist.id}" >
                                        {playlist.name}
                                    </a>
                                    <span class="p-tooltip__message u-hide" role="tooltip" id="h{playlist.id}-tooltip" >
                                        {playlist.name}
                                    </span>
                                </li>
                            {/each}
                        </ul>

                        <button class="p-in-page-navigation__dropdown-toggle" aria-expanded="false" aria-controls="in-page-navigation-list">
                            <i class="p-icon--chevron-down p-in-page-navigation__dropdown-toggle-icon"></i>
                            <i class="p-icon--chevron-up p-in-page-navigation__dropdown-toggle-icon u-hide"></i>
                        </button>
                    </nav>
                </div>
            </div>

            <!-- 右侧内容区（4 列），根据 playlists 动态生成标题和内容 -->
            <div class="grid-col-6">
                <table style="margin-top: 1rem;">
                    <thead>
                        <tr>
                            <th class="p-heading--5">歌单名</th>
                            <th class="p-heading--5">创建者</th>
                            <th></th>
                        </tr>
                    </thead>
                    {#each result?.result?.playlists ?? [] as current}
                        <tbody class="h" id="h{current.id}">
                            <tr>
                                <td>
                                    {current.name}
                                </td>
                                <td>
                                    {current.creator === undefined ? "" : current.creator.nickname}
                                </td>
                                <td>
                                    {#if current["详情"] === undefined}
                                        <button style="margin-bottom:0px;" on:click={() => { getDetail(current);}} class="p-button--base">
                                            展开
                                        </button>
                                    {:else if !Array.isArray(current["详情"])}
                                        <button
                                            style="margin-bottom:0px;"
                                            on:click={() => {
                                                getDetail(current);
                                            }}
                                            class="p-button--negative"
                                            >重试</button
                                        >
                                    {/if}
                                </td>
                            </tr>
                            {#if !Array.isArray(current["详情"]) && current["详情"] !== undefined}
                                <tr style="border-top:0">
                                    <td colspan="3">
                                        <div class="p-notification--negative">
                                            <div
                                                class="p-notification__content"
                                            >
                                                <h5
                                                    class="p-notification__title"
                                                >
                                                    展开出错
                                                </h5>
                                                <p
                                                    class="p-notification__message"
                                                >
                                                    {current["详情"]}
                                                </p>
                                            </div>
                                        </div>
                                    </td>
                                </tr>
                            {:else if Array.isArray(current["详情"])}
                                <tr>
                                    <td class="p-heading--5">歌曲名</td>
                                    <td class="p-heading--5">歌手</td>
                                    <td
                                        ><button
                                            style="margin-bottom:0px;"
                                            class="p-button--positive"
                                            on:click={() => {
                                                downloadAll(current["详情"]);
                                            }}
                                        >
                                            全部下载</button
                                        ></td
                                    >
                                </tr>
                                {#key reset}
                                    {#each current["详情"] as a}
                                        <tr>
                                            <th>{a.name}</th>
                                            <td>{a.artist}</td>
                                            <td>
                                                {#if a.status == "Done"}
                                                    <div
                                                        class="p-status-label--positive"
                                                    >
                                                        成功
                                                    </div>
                                                {:else if FlagDB2.get(getId(a.url)) === true || a.status === "下过了"}
                                                    <div
                                                        class="p-status-label--positive"
                                                    >
                                                        下过了
                                                    </div>
                                                {:else if !a.status}
                                                    <button
                                                        style="margin-bottom:0px;"
                                                        class="p-button"
                                                        on:click={() => {
                                                            addQueue(a);
                                                        }}>下载</button
                                                    >
                                                {:else if a.status == "downloading"}
                                                    <div
                                                        class="p-status-label--information"
                                                    >
                                                        下载中
                                                    </div>
                                                {:else if a.status == "waiting"}
                                                    <div class="p-status-label">
                                                        等待中
                                                    </div>
                                                {:else}
                                                    <button
                                                        style="margin-bottom:0px;"
                                                        class="p-button--negative"
                                                        on:click={() => {
                                                            addQueue(a);
                                                        }}>重试</button
                                                    >
                                                {/if}
                                            </td>
                                        </tr>
                                        {#if FlagDB2.get(getId(a.url)) !== true && !!a.status && a.status !== "downloading" && a.status !== "waiting" && a.status !== "Done"}
                                            <tr style="border-top:0">
                                                <td colspan="3">
                                                    <div class="p-notification--negative">
                                                        <div class="p-notification__content">
                                                            <h5 class="p-notification__title">
                                                                出错了
                                                            </h5>
                                                            <p class="p-notification__message">
                                                                {a.status}
                                                            </p>
                                                        </div>
                                                    </div>
                                                </td>
                                            </tr>
                                        {/if}
                                    {/each}
                                {/key}
                            {/if}
                        </tbody>
                    {/each}
                </table>
            </div>
        </div>
    {/if}
{/if}
