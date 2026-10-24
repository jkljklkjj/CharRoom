/**
 * ChatLite 客服组件（H5）—— 零依赖，直接 <script> 引入。
 *
 * 用法：
 *   <script src="https://your-host/widget.js"
 *           data-channel-key="ch_xxxxxxxx"
 *           data-api-base="https://your-host/api"
 *           data-title="在线客服"></script>
 *
 * 安全：只用公开的 channelKey + 访客令牌（仅能访问自己那条会话），
 * 不携带任何用户 JWT，页面被注入脚本也拿不到用户权限。
 */
(() => {
	var script = document.currentScript;
	if (!script) {
		console.warn(
			'[CopilotWidget] 无法定位脚本标签，请使用 <script src="..."> 方式引入',
		);
		return;
	}
	var channelKey = script.getAttribute("data-channel-key");
	if (!channelKey) {
		console.warn("[CopilotWidget] 缺少 data-channel-key");
		return;
	}
	var apiBase = (
		script.getAttribute("data-api-base") || location.origin + "/api"
	).replace(/\/+$/, "");
	var title = script.getAttribute("data-title") || "在线客服";
	var storageKey = "copilot_widget_" + channelKey;

	var state = {
		conversationId: null,
		token: null,
		visitorId: null,
		lastId: 0,
		open: false,
		polling: null,
		sending: false,
	};

	function loadState() {
		try {
			var saved = JSON.parse(localStorage.getItem(storageKey) || "{}");
			state.conversationId = saved.conversationId || null;
			state.token = saved.token || null;
			state.visitorId = saved.visitorId || null;
		} catch (e) {
			/* ignore */
		}
	}

	function saveState() {
		try {
			localStorage.setItem(
				storageKey,
				JSON.stringify({
					conversationId: state.conversationId,
					token: state.token,
					visitorId: state.visitorId,
				}),
			);
		} catch (e) {
			/* ignore */
		}
	}

	function request(path, options) {
		var opts = options || {};
		var headers = { "Content-Type": "application/json" };
		if (state.token) {
			headers["X-Visitor-Token"] = state.token;
		}
		return fetch(
			apiBase + "/public/copilot/" + encodeURIComponent(channelKey) + path,
			{
				method: opts.method || "GET",
				headers: headers,
				body: opts.body ? JSON.stringify(opts.body) : undefined,
			},
		)
			.then((res) => res.json().catch(() => null))
			.then((body) => {
				if (!body || body.code !== 0) {
					throw new Error((body && body.message) || "请求失败");
				}
				return body.data;
			});
	}

	// ── DOM ──

	var root, panel, listEl, inputEl, badgeEl;

	function buildUi() {
		root = document.createElement("div");
		root.setAttribute("data-copilot-widget", "");
		root.innerHTML =
			"" +
			"<style>" +
			'[data-copilot-widget]{position:fixed;right:24px;bottom:24px;z-index:2147483000;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif}' +
			"[data-copilot-widget] .cw-btn{width:56px;height:56px;border-radius:50%;border:0;background:#2563eb;color:#fff;font-size:24px;cursor:pointer;box-shadow:0 6px 20px rgba(37,99,235,.4)}" +
			"[data-copilot-widget] .cw-badge{position:absolute;top:-4px;right:-4px;background:#ef4444;color:#fff;border-radius:10px;padding:0 6px;font-size:11px;line-height:16px;display:none}" +
			"[data-copilot-widget] .cw-panel{position:absolute;right:0;bottom:72px;width:340px;height:480px;background:#fff;border-radius:12px;box-shadow:0 12px 40px rgba(0,0,0,.18);display:none;flex-direction:column;overflow:hidden}" +
			"[data-copilot-widget] .cw-head{background:#1f2937;color:#fff;padding:12px 14px;font-weight:600;font-size:14px;display:flex;justify-content:space-between;align-items:center}" +
			"[data-copilot-widget] .cw-close{cursor:pointer;opacity:.8}" +
			"[data-copilot-widget] .cw-list{flex:1;overflow-y:auto;padding:12px;background:#f5f6f8}" +
			"[data-copilot-widget] .cw-msg{max-width:80%;padding:8px 12px;border-radius:10px;margin-bottom:8px;font-size:13px;line-height:1.5;white-space:pre-wrap;word-break:break-word}" +
			"[data-copilot-widget] .cw-customer{margin-left:auto;background:#2563eb;color:#fff}" +
			"[data-copilot-widget] .cw-agent{background:#fff;color:#111827;border:1px solid #e5e7eb}" +
			"[data-copilot-widget] .cw-foot{border-top:1px solid #e5e7eb;padding:8px;display:flex;gap:8px;background:#fff}" +
			"[data-copilot-widget] .cw-input{flex:1;border:1px solid #d1d5db;border-radius:8px;padding:8px;font-size:13px;resize:none;height:38px;box-sizing:border-box}" +
			"[data-copilot-widget] .cw-send{border:0;background:#2563eb;color:#fff;border-radius:8px;padding:0 14px;cursor:pointer;font-size:13px}" +
			"[data-copilot-widget] .cw-send:disabled{opacity:.5;cursor:not-allowed}" +
			"</style>" +
			'<button class="cw-btn" aria-label="在线客服">💬<span class="cw-badge"></span></button>' +
			'<div class="cw-panel">' +
			'  <div class="cw-head"><span class="cw-title"></span><span class="cw-close">✕</span></div>' +
			'  <div class="cw-list"></div>' +
			'  <div class="cw-foot">' +
			'    <textarea class="cw-input" placeholder="请输入您的问题…"></textarea>' +
			'    <button class="cw-send">发送</button>' +
			"  </div>" +
			"</div>";
		document.body.appendChild(root);

		panel = root.querySelector(".cw-panel");
		listEl = root.querySelector(".cw-list");
		inputEl = root.querySelector(".cw-input");
		badgeEl = root.querySelector(".cw-badge");
		root.querySelector(".cw-title").textContent = title;
		root.querySelector(".cw-btn").addEventListener("click", toggle);
		root.querySelector(".cw-close").addEventListener("click", toggle);
		root.querySelector(".cw-send").addEventListener("click", onSend);
		inputEl.addEventListener("keydown", (e) => {
			if (e.key === "Enter" && !e.shiftKey) {
				e.preventDefault();
				onSend();
			}
		});
	}

	function appendMessage(msg) {
		if (!msg || typeof msg.id !== "number") {
			return;
		}
		if (msg.id <= state.lastId) {
			return;
		}
		state.lastId = msg.id;
		var el = document.createElement("div");
		el.className =
			"cw-msg " + (msg.role === "customer" ? "cw-customer" : "cw-agent");
		el.textContent = msg.content;
		listEl.appendChild(el);
		listEl.scrollTop = listEl.scrollHeight;
		if (!state.open && msg.role !== "customer") {
			badgeEl.style.display = "block";
		}
	}

	function toggle() {
		state.open = !state.open;
		panel.style.display = state.open ? "flex" : "none";
		if (state.open) {
			badgeEl.style.display = "none";
			inputEl.focus();
			ensureSession()
				.then(startPolling)
				.catch((e) => {
					appendSystem("连接失败：" + e.message);
				});
		} else {
			stopPolling();
		}
	}

	function appendSystem(text) {
		var el = document.createElement("div");
		el.className = "cw-msg cw-agent";
		el.textContent = text;
		listEl.appendChild(el);
		listEl.scrollTop = listEl.scrollHeight;
	}

	function ensureSession() {
		if (state.token) {
			return Promise.resolve();
		}
		return request("/session", {
			method: "POST",
			body: { visitorId: state.visitorId },
		}).then((data) => {
			state.conversationId = data.conversationId;
			state.visitorId = data.visitorId;
			state.token = data.visitorToken;
			state.lastId = 0;
			saveState();
		});
	}

	function onSend() {
		var content = (inputEl.value || "").trim();
		if (!content || state.sending) {
			return;
		}
		state.sending = true;
		root.querySelector(".cw-send").disabled = true;
		ensureSession()
			.then(() =>
				request("/messages", { method: "POST", body: { content: content } }),
			)
			.then(() => {
				inputEl.value = "";
				return refresh();
			})
			.catch((e) => {
				appendSystem("发送失败：" + e.message);
			})
			.then(() => {
				state.sending = false;
				root.querySelector(".cw-send").disabled = false;
			});
	}

	function refresh() {
		if (!state.token) {
			return Promise.resolve();
		}
		return request("/messages?afterId=" + state.lastId, { method: "GET" }).then(
			(list) => {
				(list || []).forEach(appendMessage);
			},
		);
	}

	function startPolling() {
		stopPolling();
		refresh();
		state.polling = setInterval(refresh, 4000);
	}

	function stopPolling() {
		if (state.polling) {
			clearInterval(state.polling);
			state.polling = null;
		}
	}

	loadState();
	buildUi();
	// 预热会话，便于未打开面板时也能收到坐席消息（静默失败不影响页面）
	ensureSession()
		.then(startPolling)
		.catch(() => {
			/* 等待用户打开面板再重试 */
		});
})();
