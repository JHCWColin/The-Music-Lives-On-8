<script>
  let { children } = $props();
</script>

<!-- 顶部标题栏（墨水瓶高对比，无动画） -->
<header class="dl-header">
  <div class="dl-header__title">The Downloader Lives On 8</div>
  <div class="dl-header__byline">By abpdf/JHCWColin</div>
</header>

{@render children()}
