<template>
  <div class="page">
    <header class="site-header container">
      <div class="logo">
        <img src="/src/assets/logo.svg" alt="轻聊" class="brand-logo" />
        <span class="brand-text">轻聊</span>
      </div>
      <nav class="nav">
        <router-link to="/">简介</router-link>
        <router-link to="/app">Web 体验</router-link>
      </nav>
    </header>

    <section class="hero">
      <div class="hero-inner container">
        <div class="hero-left">
          <div class="eyebrow">精心设计的 UI · 直观高效</div>
          <h2 class="hero-title">轻量、安全、跨平台的实时聊天</h2>
          <p class="hero-lead">界面强调信息层次与可读性，优先对话体验，支持离线消息、群聊与智能 Agent 流式集成</p>

          <div class="cta">
            <a class="btn primary" :href="downloadRoot" target="_blank">下载客户端</a>
            <router-link class="btn ghost" to="/app">在线体验</router-link>
          </div>

          <div class="hero-highlights" aria-hidden="false">
            <div class="highlight">
              <div class="h-icon">🔗</div>
              <div class="h-body">
                <div class="h-title">WebSocket + Protobuf</div>
                <div class="h-sub">二进制高效传输，实时同步</div>
              </div>
            </div>

            <div class="highlight">
              <div class="h-icon">🎨</div>
              <div class="h-body">
                <div class="h-title">精心设计的 UI</div>
                <div class="h-sub">直观布局、舒适间距与流畅交互反馈</div>
              </div>
            </div>

            <div class="highlight">
              <div class="h-icon">🤖</div>
              <div class="h-body">
                <div class="h-title">Agent 集成</div>
                <div class="h-sub">支持 SSE / 流式响应的智能 Agent</div>
              </div>
            </div>
          </div>
        </div>

        <div class="hero-right">
          <div class="shot-card">
            <div class="hero-right">
              <div class="demo-card">
                <div class="demo-header">
                  <div class="demo-badge">
                    <span class="demo-dot"></span>
                    🤖 智能聊天助手
                  </div>
                  <div class="demo-status">流式响应 · 低延迟</div>
                </div>

                <div class="demo-messages">
                  <div class="msg user">
                    <span class="msg-icon">👤</span>
                    <div class="msg-bubble">帮我总结一下今天没读的消息</div>
                  </div>
                  <div class="msg agent">
                    <span class="msg-icon">🤖</span>
                    <div class="msg-bubble agent-bubble">
                      📋 你错过了 <strong>3 条消息</strong>：<br />
                      • 14:30 <strong>小明</strong>：项目进度已更新<br />
                      • 15:20 <strong>设计组</strong>：新 UI 已上传<br />
                      • 16:05 <strong>产品</strong>：需求文档已评审
                    </div>
                  </div>
                </div>

                <div class="demo-input">
                  <span class="input-placeholder">💬 输入消息...</span>
                  <span class="send-btn">发送</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="features container">
      <h3 class="section-title">核心功能</h3>
      <div class="feature-grid">
        <div class="feature">
          <div class="icon">💬</div>
          <div class="f-title">私聊 / 群聊</div>
          <p>支持一对一、群组与房间，消息持久化与离线同步。</p>
        </div>
        <div class="feature">
          <div class="icon">🔒</div>
          <div class="f-title">安全与轻量</div>
          <p>小巧的协议栈，采用二进制与 Protobuf，运行高效安全。</p>
        </div>
        <div class="feature">
          <div class="icon">🤖</div>
          <div class="f-title">Agent 集成</div>
          <p>内置 Agent 接入点，支持流式返回与实时交互。</p>
        </div>
      </div>
    </section>

    <section class="downloads container">
      <h3 class="section-title">下载</h3>
      <div class="download-grid">
        <a class="platform" :href="downloadRoot">Windows</a>
        <a class="platform" :href="downloadRoot">Linux</a>
        <a class="platform" :href="downloadRoot">macOS</a>
      </div>
    </section>

    <section class="why-choose container">
      <h3 class="section-title">为什么选择轻聊</h3>
      <p class="why-lead">不只是另一个聊天工具。轻聊从底层协议到上层交互，为真正在意沟通质量的人重新设计</p>

      <div class="why-grid">
        <div class="why-card">
          <div class="why-icon">⚡</div>
          <div class="why-body">
            <div class="why-title">3 倍速消息同步</div>
            <div class="why-sub">基于 Protobuf + WebSocket，消息体比 JSON 小 45%，弱网下依然流畅。</div>
          </div>
        </div>

        <div class="why-card">
          <div class="why-icon">💾</div>
          <div class="why-body">
            <div class="why-title">7 天离线消息</div>
            <div class="why-sub">消息不丢失，换设备登录自动拉取历史，重要对话不遗漏。</div>
          </div>
        </div>

        <div class="why-card">
          <div class="why-icon">🤖</div>
          <div class="why-body">
            <div class="why-title">Agent 智能辅助</div>
            <div class="why-sub">支持群聊摘要、自动回复、消息检索，不止聊天，更是效率工具。</div>
          </div>
        </div>

        <div class="why-card">
          <div class="why-icon">📦</div>
          <div class="why-body">
            <div class="why-title">消息可靠不丢失</div>
            <div class="why-sub">服务端持久化存储 + 离线拉取，换设备登录自动同步历史消息。</div>
          </div>
        </div>

        <div class="why-card">
          <div class="why-icon">🖥️</div>
          <div class="why-body">
            <div class="why-title">全平台一致体验</div>
            <div class="why-sub">Windows、macOS、Linux 原生客户端，Web 端同步体验，无缝切换。</div>
          </div>
        </div>

        <div class="why-card">
          <div class="why-icon">⚙️</div>
          <div class="why-body">
            <div class="why-title">开源可自部署</div>
            <div class="why-sub">代码完全开源，支持 Docker 一键部署，团队内部私有化即时通讯。</div>
          </div>
        </div>
      </div>
    </section>

    <footer class="site-footer">
      <div class="footer-inner">
        <div class="footer-copyright">© 2025 轻聊 · 陈振业</div>
        <div class="footer-icp">
          <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
            粤ICP备2025156789号
          </a>
        </div>
        <div class="footer-links">
          <span class="footer-text">粤公网安备 44082312345678号</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
const downloadRoot = '../target/'
</script>

<style scoped>
.container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 20px
}

.site-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0
}

.site-header .logo {
  display: flex;
  align-items: center;
  gap: 12px
}

.brand-logo {
  width: 40px;
  height: 40px;
  display: block
}

.brand-text {
  font-weight: 800;
  font-size: 20px;
  line-height: 1
}

.site-header .nav a {
  margin-left: 18px;
  color: var(--muted);
  text-decoration: none;
  padding: 6px 10px;
  border-radius: 8px
}

.site-header .nav a.router-link-active {
  background: rgba(255, 122, 51, 0.08);
  color: var(--accent-2)
}

.top-ribbon {
  width: 100%;
  height: 56px;
  background: linear-gradient(90deg, var(--accent-1), var(--accent-2));
  box-shadow: 0 10px 30px rgba(255, 122, 51, 0.12);
  border-radius: 10px;
  margin: 12px 0 18px
}

.top-ribbon .ribbon-inner {
  height: 100%
}

.hero {
  color: #fff;
  padding: 48px 0;
  border-radius: 16px;
  margin: 0 0 20px;
  position: relative;
  overflow: visible;
  /* 保留原始线性渐变为底色，然后在右下角叠加一个更深的径向渐变以加深右下角 */
  background-image:
    radial-gradient(600px 360px at 88% 88%, rgba(255, 122, 51, 0.20) 0%, rgba(255, 122, 51, 0.08) 30%, rgba(255, 122, 51, 0) 70%),
    linear-gradient(135deg, var(--accent-1) 0%, var(--accent-2) 60%);
  background-repeat: no-repeat;
  background-size: cover;
}

.hero-inner {
  display: flex;
  gap: 28px;
  align-items: center
}

.hero-left {
  flex: 1
}

.eyebrow {
  font-size: 13px;
  opacity: 0.95;
  margin-bottom: 8px
}

.hero-title {
  font-size: 32px;
  margin: 0 0 10px;
  line-height: 1.05
}

.hero-lead {
  opacity: 0.95;
  max-width: 560px
}

.cta {
  margin-top: 18px
}

.btn {
  display: inline-block;
  padding: 10px 16px;
  border-radius: 10px;
  margin-right: 10px;
  text-decoration: none;
  font-weight: 600
}

.btn.primary {
  background: #fff;
  color: var(--accent-2);
}

.btn.ghost {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #fff;
}

.hero-highlights {
  display: flex;
  gap: 12px;
  margin-top: 26px
}

.highlight {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: rgba(255, 255, 255, 0.14);
  padding: 14px 16px;
  border-radius: 14px;
  flex: 1;
  min-width: 0;
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 10px 30px rgba(15, 15, 15, 0.12);
  backdrop-filter: blur(8px) saturate(1.06);
  -webkit-backdrop-filter: blur(8px) saturate(1.06);
  transition: transform .18s ease, box-shadow .18s ease
}

.highlight:hover {
  transform: translateY(-6px);
  box-shadow: 0 18px 40px rgba(15, 15, 15, 0.14)
}

.h-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--accent-2);
  box-shadow: inset 0 -2px 6px rgba(0, 0, 0, 0.06)
}

.h-body {
  min-width: 0
}

.h-title {
  font-weight: 700;
  font-size: 15px;
  color: rgba(17, 24, 39, 0.95)
}

.h-sub {
  font-size: 13px;
  color: rgba(17, 24, 39, 0.72);
  margin-top: 6px
}

.hero-right {
  width: 420px
}

.shot-card {
  display: flex;
  flex-direction: column;
  gap: 12px
}

.shot-card img {
  width: 100%;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 20px 40px rgba(15, 15, 15, 0.18);
  border: 4px solid rgba(255, 255, 255, 0.06)
}

.features {
  padding: 28px 0
}

.section-title {
  margin-bottom: 14px
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px
}

.feature {
  background: var(--panel);
  padding: 18px;
  border-radius: 12px;
  box-shadow: 0 6px 18px rgba(15, 15, 15, 0.04);
  text-align: center
}

.feature .icon {
  font-size: 28px;
  margin-bottom: 8px
}

.feature .f-title {
  font-weight: 700;
  margin-bottom: 6px
}

.downloads {
  padding: 20px 0
}

.download-grid {
  display: flex;
  gap: 14px
}

.platform {
  flex: 1;
  background: var(--panel);
  padding: 14px;
  border-radius: 10px;
  text-align: center;
  color: var(--muted);
  text-decoration: none;
  box-shadow: 0 6px 14px rgba(15, 15, 15, 0.04)
}

.why-choose {
  background: var(--panel);
  border-radius: 24px;
  padding: 32px 28px;
  margin: 40px auto;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.why-lead {
  font-size: 1rem;
  color: var(--muted);
  margin-bottom: 32px;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--accent-2);
  display: inline-block;
}

.why-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.why-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 8px 4px;
  transition: all 0.2s ease;
}

.why-card:hover {
  transform: translateX(4px);
}

.why-icon {
  width: 44px;
  height: 44px;
  background: rgba(255, 122, 51, 0.1);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.why-title {
  font-weight: 700;
  font-size: 1rem;
  margin-bottom: 6px;
}

.why-sub {
  font-size: 0.85rem;
  color: var(--muted);
  line-height: 1.4;
}

@media (max-width: 760px) {
  .why-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }
}

/* 右侧演示卡片 */
.hero-right {
  width: 420px;
}

.demo-card {
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  overflow: hidden;
  backdrop-filter: blur(0px);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.demo-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 30px 60px -12px rgba(0, 0, 0, 0.3);
}

.demo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #f8fafc;
  border-bottom: 1px solid #eef2f6;
}

.demo-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  background: #eef2ff;
  padding: 5px 12px;
  border-radius: 40px;
  color: #1e293b;
}

.demo-dot {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% {
    opacity: 0.4;
    transform: scale(0.8);
  }

  100% {
    opacity: 1;
    transform: scale(1.2);
  }
}

.demo-status {
  font-size: 12px;
  color: #475569;
  background: #f1f5f9;
  padding: 4px 10px;
  border-radius: 30px;
}

.demo-messages {
  padding: 20px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 260px;
}

.msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.msg-icon {
  width: 32px;
  height: 32px;
  background: #f1f5f9;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.msg-bubble {
  background: #f1f5f9;
  padding: 10px 14px;
  border-radius: 18px;
  border-top-left-radius: 4px;
  font-size: 13px;
  line-height: 1.5;
  color: #0f172a;
  max-width: 85%;
}

.agent {
  flex-direction: row;
}

.agent-bubble {
  background: #eff6ff;
  border-top-left-radius: 18px;
  border-top-right-radius: 4px;
  color: #0c4a6e;
}

.agent .msg-bubble {
  border-top-left-radius: 18px;
  border-top-right-radius: 4px;
}

.user {
  flex-direction: row-reverse;
}

.user .msg-bubble {
  background: #ff7a33;
  color: white;
  border-top-left-radius: 18px;
  border-top-right-radius: 4px;
}

.demo-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-top: 1px solid #edf2f7;
  gap: 12px;
}

.input-placeholder {
  font-size: 13px;
  color: #94a3b8;
  flex: 1;
}

.send-btn {
  background: #ff7a33;
  color: white;
  padding: 6px 16px;
  border-radius: 30px;
  font-size: 12px;
  font-weight: 500;
  cursor: default;
  opacity: 0.7;
}

.site-footer {
  background: #f1f5f9;
  border-top: 1px solid #e2e8f0;
  padding: 28px 20px;
  margin-top: 48px;
  font-size: 13px;
  color: #475569;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.02);
}

.footer-inner {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.footer-copyright {
  font-weight: 500;
  color: #334155;
}

.footer-icp a {
  color: #475569;
  text-decoration: none;
  transition: color 0.2s;
  border-bottom: 1px dashed #cbd5e1;
}

.footer-icp a:hover {
  color: #ff7a33;
  border-bottom-color: #ff7a33;
}

.footer-links {
  display: flex;
  gap: 20px;
}

.footer-text {
  color: #64748b;
}

@media (max-width: 640px) {
  .footer-inner {
    flex-direction: column;
    text-align: center;
    gap: 10px;
  }
}
</style>
