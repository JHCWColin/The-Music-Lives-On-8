<script>
  import './styles.scss';
  import { page } from './page.svelte.js';
  let showMIT = $state(false);
  let showApache = $state(false);
  import Apache from './apache.svelte';
  import Mit from './mit.svelte';
  import Netease from './netease.svelte';
</script>

<main class="p-strip">
  <div class="row">
    <div class="grid-col-large-8 grid-col-start-large-3">
      {#if page.num == 0}
        <div>
          <!-- 标题行 -->
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h1 class="p-heading--1">欢迎使用 The Downloader Lives On 8</h1>
            <button class="p-button--link" onclick={() => { page.num = 1; }}>关于</button>
          </div>
            <h3 class="p-heading--3">用法(通用)：</h3>
            <p>搜索歌名或歌手，点击结果，你的下载将自动开始。</p>
            <p>音频文件（mp3、aac）将自动存入音乐库文件夹（系统 Music 下的 The Music Lives On 8）</p>

          <!-- 卡片保留 padding -->
          <div class="p-card--highlighted" style="padding: 2rem;">
            <h2 class="p-heading--2">通过 祈杰のMeting-API 下载</h2>
            <button class="p-button--positive" onclick={()=>{page.num = 2} }>开始</button>
          </div>
       
          <div class="p-card--highlighted" style="padding: 2rem;">
            <h2 class="p-heading--2">从gequhai下载</h2>
            <a class="p-button--positive" href="https://www.gequhai.com">开始</a>
          </div>
           </div>


      {:else if page.num == 1}
        <div>
          <!-- 返回按钮 -->
          <div style="margin-bottom: 1rem;">
            <button class="p-button" onclick={() => { page.num = 0; }}>← 返回</button>
          </div>

          <!-- 卡片保留 padding -->
          <div class="p-card--highlighted" style="padding: 2rem;">
            <p>The Downloader Lives On 8 是一个音乐下载器，旨在用最少的操作下载音乐，并直接存入音乐库文件夹。</p>
            <p>本软件使用的API均由第三方免费提供。不保障可用性。请不要滥用。</p>

            <hr class="p-divider" style="margin: 1.5rem 0;" />
            <p>本软件是开源软件。使用MIT或Apache 2.0开源协议。你可以<a class="p-button--link" href="https://github.com/JHCWColin/The-Music-Lives-On-8" target="_blank">在此处</a>查看源代码。</p>

            <!-- MIT -->
            <div style="margin-bottom: 0.5rem;">
              <div
                style="cursor: pointer; user-select: none; display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0;"
                onclick={() => { showMIT = !showMIT; }}
              >
                <strong>MIT License</strong>
                <span>{showMIT ? '▾' : '▸'}</span>
              </div>
              {#if showMIT}
                <div style="padding-left: 1rem; color: #666;">
                  <p><Mit /></p>
                </div>
              {/if}
            </div>

            <!-- Apache -->
            <div>
              <div
                style="cursor: pointer; user-select: none; display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0;"
                onclick={() => { showApache = !showApache; }}
              >
                <strong>Apache License 2.0</strong>
                <span>{showApache ? '▾' : '▸'}</span>
              </div>
              {#if showApache}
                <div style="padding-left: 1rem; color: #666;">
                  <p><Apache /></p>
                </div>
              {/if}
            </div>
          </div>
        </div>
      {:else if page.num ==2}
      <div>
        <Netease />
      </div>
      {/if}
    </div>
  </div>
</main>