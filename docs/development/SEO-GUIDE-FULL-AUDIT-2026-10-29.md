# SEO Guide 全量逐文件对照（2026-10-29）

本次覆盖 [seo-guide-skill](https://github.com/lesishu/seo-guide-skill/tree/ab8af275605f249b8a3eb620ba0cd1b74eb01436) 的 **176 份 Markdown/TXT 文件**，固定提交 `ab8af275605f249b8a3eb620ba0cd1b74eb01436`。不是仅检查 AI/Agent 发现协议。

## 方法与边界

按每个文件的主题、章节和规范性要求逐项判断适用性，再核对源码、构建和浏览器证据；对直接影响实现的关键章节阅读全文。长课程字幕按主题与要求提取，未逐句翻译或逐句标注。文件数包括目录、总结及重复正文，不代表176条独立规则，更不表示所有SEO工作已完成。JSON保留逐文件SHA-256、大小、行数、结论与证据编号，可复核此次资料版本。

- `references/`：10份核心指南。
- `references-google/`：86篇正文及1份目录。
- `references-moz/`：10章原文、1份总结及1份目录。
- `references-ahrefs/`：56份字幕、7份总结及1份目录。README提到58课时，实际文件数以Git树为准。
- 根README、SKILL及脚本README：3份。

“已核对”仅表示本地适用检查有证据；“部分满足”明确保留缺口；“待数据/待运营/后续项”均未完成。“不适用”给出当前业务原因，不表示资料没有价值。

本轮按文档条目统计：已核对31、本轮修复3、部分满足39、待数据27、待运营19、不适用44、后续项6、参考7。重复文档会映射同一证据，不按条目数计算独立修复数量。

## 这次落实的修改

1. Vue路由外的 `/embed-demo.html`、`/docs/api/index.html` 补初始 `noindex,follow` 和描述。它们仍可正常访问并被抓取读取noindex；不把接入测试工具、简略目录页当正式搜索落地页。API页纠正REST基地址为 `/api`，不再把注册邮箱验证码页称为站点验证页。验证文件本身未修改。
2. 聚合网关移除旧SEO map及HTML响应替换；旧规则仍含“端到端加密”和无证据的竞品文案，可能和构建metadata冲突。页面标题/描述统一由构建和运行时SEO模块管理。没有改变认证、实时握手或API限流。
3. FAQ新增注册/邮件验证码说明、WebTransport/QUIC网络排障和连接限流冷却说明，三种语言同时更新。正文在构建期HTML中可读，不依赖点击后发请求。
4. 建立这份逐文件矩阵以及可机器读取的清单；没有购买工具、群发外联、伪造评分/搜索量/日期或批量生成薄页面。

## 仍需继续的实质工作（按优先级）

| 优先级 | 工作 | 当前缺口与完成条件 |
| --- | --- | --- |
| P1 | 真实搜索表现基线 | 从GSC及实际服务的搜索引擎站长后台导出最近28天和前28天：页面、查询、国家、设备、点击、曝光、CTR、位置、索引状态。检查手动操作/安全问题、URL检查与Google选定canonical。当前没有连接，未声称提交或验证成功。 |
| P1 | 线上部署检查 | 部署后复查robots/JSON MIME、独立静态页noindex、显式index重定向、未知404和TLS；检查CDN/WAF是否给爬虫挑战或错误。此次只修改并推送源码，不能代替线上配置证据。 |
| P1 | 多语言独立索引 | 当前三语共用URL，只能证明语言切换可用。若要英日文自然搜索流量，应增加稳定语言URL、对应SSR、每语自canonical及互返hreflang/x-default、语言切换链接和sitemap，再做全路径回归。当前未完成，不向同一URL伪造三种hreflang。 |
| P1 | 真实移动性能 | 获取LCP/INP/CLS的p75字段数据；新站若CrUX样本不足，记录无数据。实验分数不等于真实体验。仍保留首页无认证、聊天Worker/虚拟列表/本地恢复，不为抓取扩大消息窗口。 |
| P2 | 关键词与内容计划 | 下表只是意图假设，不是已完成的搜索量/竞品研究。用真实查询确定下一篇：下载/安装、注册邮件、浏览器连接、七天历史、自部署。达到可操作深度才单独建指南页，避免每个词一个薄页。 |
| P2 | 可信度与分享素材 | 保持源码/项目说明可查；核实备案信息、作者与维护联系方式。现有分享图是品牌图，若要Discover/图片搜索内容应提供真实相关配图；不能凭品牌图宣称已满足全部图片建议。 |
| P2 | 自然外链 | 有价值的真实使用/部署文档供引用，先分析已有链接再做相关推广。没有联系人或外发授权，不自动采集邮箱、群发或买链。 |
| P3 | 软件富结果 | 先获得可公开验证的价格/评价等必要资料；不从Google示例复制评分。当前WebSite与BreadcrumbList不等于SoftwareApplication富结果资格。 |

### 页面与搜索意图假设

| 页面 | 服务的真实任务 | 当前内容与下一步 |
| --- | --- | --- |
| `/` | 轻聊是什么、网页版入口、客户端怎么下载 | 品牌和具体通讯用途、真实安装包、功能/FAQ/源码入口；以真实查询判断标题和引导是否匹配。 |
| `/features` | 私聊、群聊、AI、支持平台与限制 | 功能与边界，保持传输加密和端到端加密区别；不放虚构竞品优势。 |
| `/faq` | 怎么注册、邮箱没收到、网页不能连接、历史恢复、自部署 | 本轮补操作/排障；用反馈决定哪些问题需要独立完整指南。 |
| `/app`等私有入口 | 登录后使用 | noindex，认证与账号数据隔离；不用私有消息充搜索内容。 |
| 演示/简略API目录 | 开发者接入检查 | noindex；未来有完整、准确的公开API参考后再单独评估是否索引。 |

### 不机械采用的旧建议

关键词密度2–5%、所谓LSI、固定标题字数、强制配图/链接数量不作为Google合格规则；无需因清单而安装GA、WordPress插件或AMP。旧课程的FID按当前INP评估；旧FAQ富结果资料不作当前承诺。`rel=next/prev`、`noarchive`等历史指导需查当前支持情况。部分以cloaking/paid-links/hacked等命名的文件实际是完整SpamPolicies，按正文识别重复，不重复制造要求。

## 当前官方资料复核

已对照当前Google文档：[noindex须允许抓取](https://developers.google.com/search/docs/crawling-indexing/block-indexing)、[自适应语言建议使用独立URL](https://developers.google.com/search/docs/specialty/international/locale-adaptive-pages)、[入门指南与常见误区](https://developers.google.com/search/docs/fundamentals/seo-starter-guide)、[软件富结果所需价格和评价](https://developers.google.com/search/docs/appearance/structured-data/software-app)。[Google更新日志](https://developers.google.com/search/updates)记录FAQ富结果自2026-05-07停止展示，2026-06-15移除文档，因此参考仓库旧FAQ指南不能直接作为当前要求。

## 证据索引

| 编号 | 源码/验证入口 | 能证明什么及限制 |
| --- | --- | --- |
| A | [路由范围](../../web/site.routes.json)、[构建SEO](../../web/scripts/site-seo.cjs)、[SSR](../../web/scripts/render-ssr.mjs)、[运行时SEO](../../web/src/services/pageSeo.ts)、[SEO单测](../../web/tests/seo.test.cjs) | 初始与导航metadata、3个公开索引页、canonical、robots、sitemap、面包屑。不能证明Google已索引。 |
| B | [首页](../../web/src/views/Home.vue)、[FAQ](../../web/src/views/FAQ.vue)、[中文内容](../../web/src/locales/zh-CN.json)、[英文](../../web/src/locales/en.json)、[日文](../../web/src/locales/ja.json) | 真实能力、操作说明、隐私边界和内容意图；不替代搜索需求数据。 |
| C | [源码出处](../../web/src/components/PublicSourceLinks.vue)、[公开包屑](../../web/src/components/PublicBreadcrumbs.vue)、[SEO浏览器](../../web/tests/seo-browser.cjs) | 可抓取内链与出处。不能证明外链权威或流量。 |
| D | [接入演示](../../web/public/embed-demo.html)、[API目录](../../web/public/docs/api/index.html)、[回归测试](../../web/tests/seo-guide.test.cjs) | 独立静态head的noindex与文档准确性。 |
| E | `chat-room/docker/nginx/nginx.conf.template`（相邻后端独立仓库）、[前端Nginx](../../web/nginx.conf)、[回归](../../web/tests/seo-guide.test.cjs) | 状态码/重定向和不再替换SEO的配置契约。真实Nginx未在本机运行。 |
| F | [首屏浏览器](../../web/tests/app-ssr-browser.cjs)、[性能测量工具](../../web/scripts/measure-page-performance.cjs)、[前轮测量](WEB-SEO-PERFORMANCE-2026-10-29.md) | 实验环境响应式和性能验证；不是CrUX字段数据。 |
| G | 本报告“仍需继续”及站长后台/运营资料 | 明确未获得的数据与运营证据，不视作通过。 |
| H | [语言初始化](../../web/src/i18n.ts)、[路由](../../web/src/router/routes.ts) | 只有同URL切换，独立语言SEO尚未完成。 |
| I | [机器清单](seo-guide-full-audit.json)及固定版本外部原文 | 文档覆盖、版本和分类。 |
| N | 当前产品为通讯软件；[路由](../../web/site.routes.json)与公开内容 | 不存在对应电商/门店/招聘/食谱/视频业务，故不添加虚假schema。 |

## 实际验证

- **通过**：`charroom-client-check/scripts/doctor.py --client CharRoom --target web` 前置条件（未声明lint，未冒充执行lint）。
- **通过**：`npm run build`，包含严格类型检查、客户端打包、路由head生成、4个入口SSR。
- **通过**：`node --experimental-vm-modules --test --test-concurrency=1 tests/*.test.cjs`，145项通过、0失败。新增2项回归在修复前失败、修复后通过。
- **通过**：Edge运行 `node tests/seo-browser.cjs <browser>`，含禁JS正文/10条FAQ、独立静态页初始noindex、canonical、JSON、404、语言切换和私有页schema清理。此HTTP服务是构建产物夹具，不是实际Nginx。
- **通过**：Edge运行 `node tests/app-ssr-browser.cjs <browser>`，首页无认证/聊天运行时，移动/桌面首帧和/app认证入口稳定。
- **通过**：176条唯一ID、176个非pending结论、报告相对路径，以及参考文件SHA-256一致性检查；两个仓库 `git diff --check`。
- **静态核对**：3个索引页首始HTML标题与正文；首页两处装饰品牌图为32×32且空alt，配套品牌文字可读；功能/FAQ没有内容图片。当前不为凑数量增加图片请求。
- **未运行**：本机实际Nginx语法/HTTP、部署后CDN和线上Lighthouse、SearchConsole/CrUX、真实账号联调；此次没有改聊天协议和运行时，未重复全量消息联调。前轮Lighthouse100是前轮的本地SEO分类结果，本轮不借用它声称线上分数。
- **跨端评估**：Web静态索引、公开说明和网关metadata；桌面/CLI/Android没有对应搜索HTML链路，账户/协议/消息语义未变，无需同步原生代码，未运行原生构建。


## 176份文档逐项矩阵

每个链接指向此次固定提交的原文；SHA-256在JSON中，不复制整份第三方资料。

| ID | 文件 | 结论 | 证据 | 对照与差距 |
| --- | --- | --- | --- | --- |
| D001 | [README.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/README.md) | 参考 | I | 仓库入口已清点176文件；目录索引和重复正文分别保留，不按176个独立SEO规则计算。 |
| D002 | [references/content-strategy.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/content-strategy.md) | 部分满足 | B,G | 首页/功能/FAQ按用户意图分工；本轮补注册和连接排障。下一轮内容以真实搜索查询和反馈选题，不批量造文章。 |
| D003 | [references/keyword-research.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/keyword-research.md) | 待数据 | G | 有页面意图映射；搜索量、竞争度、已有排名没有数据，不能声称做完关键词研究。 |
| D004 | [references/link-building.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/link-building.md) | 待运营 | G | 源码可查且有自然出处链接；未获得外链报告，未群发邮件、购买或交换链接。 |
| D005 | [references/local-seo.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/local-seo.md) | 不适用 | N | 在线聊天产品没有已确认的实体门店，不创建GBP、地址、营业时间或本地引用。 |
| D006 | [references/on-page-seo.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/on-page-seo.md) | 部分满足 | A,B,C | 标题、H1、正文、真实链接已核对；不采用固定关键词密度、LSI或每页强制图片规则。 |
| D007 | [references/seo-audit.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/seo-audit.md) | 部分满足 | A,D,E,F,G | 源码/构建与本地浏览器审查可做；收录、CTR、自然流量和线上CWV须站长数据。 |
| D008 | [references/seo-principles.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/seo-principles.md) | 已核对 | A,B | 以用户任务、真实能力、可抓取正文为准；不把满分或AI清单等同于排名。 |
| D009 | [references/seo-tools.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/seo-tools.md) | 待数据 | G | 本地构建和浏览器可验证；GSC/Bing/百度站长/Ahrefs未连接，不自动安装GA或付费工具。 |
| D010 | [references/technical-seo.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/technical-seo.md) | 部分满足 | A,D,E,F | SSR、状态码、canonical、robots、缓存入口已核对；线上网关和字段数据仍需部署验证。 |
| D011 | [references/tkd-standard.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references/tkd-standard.md) | 部分满足 | A,B | 三语标题与描述有内容依据；Google不把meta keywords和固定字符数作为通行合格线。 |
| D012 | [references-ahrefs/AHREFS-CONTENT-MARKETING-SUMMARY.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/AHREFS-CONTENT-MARKETING-SUMMARY.md) | 部分满足 | B,G | 采用产品相关问题而非泛化营销；内容发布效果和分发渠道待真实数据。 |
| D013 | [references-ahrefs/AHREFS-DIGITAL-MARKETING-SUMMARY.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/AHREFS-DIGITAL-MARKETING-SUMMARY.md) | 待运营 | G | 营销渠道选择与转化目标需运营决策，职业技能章节不对应产品代码。 |
| D014 | [references-ahrefs/AHREFS-KEYWORD-RESEARCH-SUMMARY.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/AHREFS-KEYWORD-RESEARCH-SUMMARY.md) | 待数据 | G | 需求、商业相关性、流量潜力与可竞争性分开；没有工具数据时不填虚构搜索量。 |
| D015 | [references-ahrefs/AHREFS-LINK-BUILDING-SUMMARY.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/AHREFS-LINK-BUILDING-SUMMARY.md) | 待运营 | C,G | 提供可引用的真实项目资料；潜在链接对象筛选、联系与效果不在源码中假装完成。 |
| D016 | [references-ahrefs/AHREFS-LOCAL-SEO-SUMMARY.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/AHREFS-LOCAL-SEO-SUMMARY.md) | 不适用 | N | 本地商家课程不匹配无实体门店产品；移动和技术通用建议另见技术条目。 |
| D017 | [references-ahrefs/AHREFS-TECHNICAL-SEO-SUMMARY.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/AHREFS-TECHNICAL-SEO-SUMMARY.md) | 部分满足 | A,D,E,F | 技术课程对应可抓取、HTTPS、结构、性能；WordPress插件与本项目无关。 |
| D018 | [references-ahrefs/AHREFS-VIDEO-MARKETING-SUMMARY.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/AHREFS-VIDEO-MARKETING-SUMMARY.md) | 不适用 | N | 没有公开视频内容、YouTube频道或视频广告活动，不创建VideoObject。 |
| D019 | [references-ahrefs/content_marketing/0R_3iarc8IA.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/0R_3iarc8IA.txt) | 部分满足 | B,G | 内容创建与分发围绕轻聊任务；本轮补真实操作说明，分发效果待运营。 |
| D020 | [references-ahrefs/content_marketing/8BdZ0dUu7VQ.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/8BdZ0dUu7VQ.txt) | 部分满足 | B,G | FAQ按问题清晰作答；SERP三要素需实际搜索结果数据复核。 |
| D021 | [references-ahrefs/content_marketing/9fglNtHmc8I.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/9fglNtHmc8I.txt) | 待数据 | G | 业务目标应看访问到使用/下载，而非只看Lighthouse数字；没有转化基线。 |
| D022 | [references-ahrefs/content_marketing/9HWna4hK47c.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/9HWna4hK47c.txt) | 待数据 | B,G | 更新过时能力文案；持续刷新节奏按查询与使用问题判断，不伪造更新时间。 |
| D023 | [references-ahrefs/content_marketing/n5KKbPS6N-g.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/n5KKbPS6N-g.txt) | 部分满足 | B | 正文准确性与可操作性已核对；注册、邮件未达和连接限制已补说明。 |
| D024 | [references-ahrefs/content_marketing/nIbJI1kCxrU.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/nIbJI1kCxrU.txt) | 部分满足 | C,G | 三页形成互链；目前无需为了内容集群强造大量子页面，按需求再扩展。 |
| D025 | [references-ahrefs/content_marketing/PoVYweKH4ck.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/PoVYweKH4ck.txt) | 待运营 | G | 内容推广清单需要渠道与受众；本轮没有自动外发消息或垃圾链接。 |
| D026 | [references-ahrefs/content_marketing/tWNvAGwiVqo.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/tWNvAGwiVqo.txt) | 待数据 | G | 删除、合并与刷新旧页需要流量和外链依据；不凭内容篇幅盲删页面。 |
| D027 | [references-ahrefs/content_marketing/xOB-Pa2aERs.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/content_marketing/xOB-Pa2aERs.txt) | 部分满足 | B | 用注册、下载、历史恢复、浏览器兼容解决产品问题，不写无关热点文章。 |
| D028 | [references-ahrefs/digital_marketing/7sRyflzOc1Y.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/digital_marketing/7sRyflzOc1Y.txt) | 不适用 | N | 数字营销硬技能/软技能职业课程，不对应站点实现任务。 |
| D029 | [references-ahrefs/digital_marketing/bG-AL2Hd6Cc.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/digital_marketing/bG-AL2Hd6Cc.txt) | 不适用 | N | 营销职业入门路径不对应产品页面或SEO技术缺陷。 |
| D030 | [references-ahrefs/digital_marketing/eedeyfy_B-Q.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/digital_marketing/eedeyfy_B-Q.txt) | 待数据 | G | 优先以真实业务转化验证渠道；没有数据时不因访问量或分数做收益结论。 |
| D031 | [references-ahrefs/digital_marketing/N8MvgpFf_-k.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/digital_marketing/N8MvgpFf_-k.txt) | 待运营 | G | 跨渠道内容应适配平台意图；当前未制定/执行多渠道运营。 |
| D032 | [references-ahrefs/digital_marketing/wZZnxXyES80.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/digital_marketing/wZZnxXyES80.txt) | 待运营 | G | 七类营销渠道需要目标和成本判断；不自动开PPC、邮件或社媒活动。 |
| D033 | [references-ahrefs/INDEX.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/INDEX.md) | 参考 | I | Ahrefs目录实际64文件：7总结、1索引、56字幕；与README所称58课时区分。 |
| D034 | [references-ahrefs/keyword_research/01-what-are-keywords.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/01-what-are-keywords.txt) | 待数据 | G | 关键词必须兼顾需求、流量潜力、业务价值和排名可行性；当前无量化数据。 |
| D035 | [references-ahrefs/keyword_research/02-analyze-searcher-intent.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/02-analyze-searcher-intent.txt) | 待数据 | G | 搜索意图按内容类型/格式/角度核对；尚无目标查询的SERP采样。 |
| D036 | [references-ahrefs/keyword_research/03-find-keywords-for-website.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/03-find-keywords-for-website.txt) | 待数据 | G | 种子词可来自产品能力；扩词和筛选指标未获得工具数据。 |
| D037 | [references-ahrefs/keyword_research/04-understanding-ranking-difficulty.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/04-understanding-ranking-difficulty.txt) | 待数据 | G | 竞争度需评估实际结果与主题权威，不能凭竞品名称推断。 |
| D038 | [references-ahrefs/keyword_research/05-keyword-research-tutorial.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/05-keyword-research-tutorial.txt) | 部分满足 | B,G | 同一任务相关词映射同页；收入/转化潜力需要实际业务数据。 |
| D039 | [references-ahrefs/keyword_research/06-keyword-research-new-website.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/06-keyword-research-new-website.txt) | 部分满足 | B,G | 先覆盖品牌加具体用途，不承诺泛化聊天大词排名；目标难度待研究。 |
| D040 | [references-ahrefs/keyword_research/07-low-competition-keywords.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/07-low-competition-keywords.txt) | 待数据 | G | 关键词难度不能仅看反向链接数；需要人工核对SERP与内容匹配。 |
| D041 | [references-ahrefs/keyword_research/08-long-tail-keywords.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/08-long-tail-keywords.txt) | 部分满足 | B | 长尾同意图合并在FAQ；未为每个词序变体新增薄页。 |
| D042 | [references-ahrefs/keyword_research/09-keyword-difficulty-scores.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/09-keyword-difficulty-scores.txt) | 待数据 | G | 单独KD分数不能代表可排名性；没有工具数据，不生成难度结论。 |
| D043 | [references-ahrefs/keyword_research/10-advanced-keyword-tips.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/10-advanced-keyword-tips.txt) | 待数据 | G | 竞品内容缺口需已排名页面和查询数据，不把未证实竞品表作为研究。 |
| D044 | [references-ahrefs/keyword_research/11-fractured-search-intent.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/keyword_research/11-fractured-search-intent.txt) | 部分满足 | A,B | “轻聊”品牌可能有歧义，页面标题/引导说明网页聊天、私聊、群聊的具体上下文。 |
| D045 | [references-ahrefs/link_building/linkbuild-01-advanced-link-building-fundamentals.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/linkbuild-01-advanced-link-building-fundamentals.txt) | 待运营 | C,G | 大型字幕含基础与高级链接策略；已提供真实源码出处，未执行外链获取。 |
| D046 | [references-ahrefs/link_building/linkbuild-02-outreach.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/linkbuild-02-outreach.txt) | 待运营 | G | 外联要相关、可信且有价值；没有网站权威/联系方式数据，不批量发送。 |
| D047 | [references-ahrefs/link_building/linkbuild-03-team-building-systems.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/linkbuild-03-team-building-systems.txt) | 待运营 | G | 文件名虽写team-building，正文是新链接策略介绍；按正文判断，不当作人员配置任务。 |
| D048 | [references-ahrefs/link_building/m2l1_seed_prospects.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m2l1_seed_prospects.txt) | 待运营 | G | 种子链接候选需有真实推荐理由；本轮未创建联系名单。 |
| D049 | [references-ahrefs/link_building/m2l2_lookalike_prospects.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m2l2_lookalike_prospects.txt) | 待运营 | G | 相似候选扩展需要工具和人工筛选；未采集联系人。 |
| D050 | [references-ahrefs/link_building/m2l3_segment_prospects.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m2l3_segment_prospects.txt) | 待运营 | G | 外联细分与目标相关性属运营执行；没有把模板嵌入网站。 |
| D051 | [references-ahrefs/link_building/m3l1_vetting_prospects.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m3l1_vetting_prospects.txt) | 待运营 | G | 检查页面语境与相关性，不能只看DR；缺外链候选数据。 |
| D052 | [references-ahrefs/link_building/m3l2_who_to_pitch.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m3l2_who_to_pitch.txt) | 待运营 | G | 联系作者/编辑需明确授权；本轮没有对外发送消息。 |
| D053 | [references-ahrefs/link_building/m3l3_blitz_list.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m3l3_blitz_list.txt) | 待运营 | G | 批量联系人工作流涉及数据、API和预算；未擅自收集邮箱或开通服务。 |
| D054 | [references-ahrefs/link_building/m4l2_hybrid_outreach.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m4l2_hybrid_outreach.txt) | 待运营 | G | 混合外联需真实价值理由；没有实际外联活动。 |
| D055 | [references-ahrefs/link_building/m4l3_email_templates.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m4l3_email_templates.txt) | 待运营 | G | 可读且针对对象的邮件模板并非站内SEO修复；未发信。 |
| D056 | [references-ahrefs/link_building/m5l2_link_building_system.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m5l2_link_building_system.txt) | 不适用 | N | 链接团队分工与招聘属于人员运营，不为此改产品代码或安排人员。 |
| D057 | [references-ahrefs/link_building/m5l3_team_workflow.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/link_building/m5l3_team_workflow.txt) | 待运营 | G | 候选筛选、去重、外联工作流需运营负责人；本轮未建立自动发送链路。 |
| D058 | [references-ahrefs/local_seo/01_how_to_do_local_seo.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/local_seo/01_how_to_do_local_seo.txt) | 不适用 | N | 本地商家NAP/GoogleBusinessProfile没有实体门店依据。 |
| D059 | [references-ahrefs/local_seo/02_local_keyword_research.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/local_seo/02_local_keyword_research.txt) | 不适用 | N | 城市服务词不匹配当前数字产品，不造城市落地页。 |
| D060 | [references-ahrefs/local_seo/03_link_building_for_local_seo.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/local_seo/03_link_building_for_local_seo.txt) | 不适用 | N | 地方商会/本地链接课程没有适用商家场景。 |
| D061 | [references-ahrefs/local_seo/04_find_citations_for_local_seo.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/local_seo/04_find_citations_for_local_seo.txt) | 不适用 | N | 本地目录引用和地址一致性没有已确认门店资料。 |
| D062 | [references-ahrefs/technical_seo/techseo-01-what-is-technical-seo.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/technical_seo/techseo-01-what-is-technical-seo.txt) | 部分满足 | A,D | 公开SSR不需登录，私有页noindex；演示与简略文档页本轮补初始noindex。 |
| D063 | [references-ahrefs/technical_seo/techseo-02-technical-seo-best-practices.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/technical_seo/techseo-02-technical-seo-best-practices.txt) | 部分满足 | C,E,F | 层级、缓存和加载体验已有实现；维护效果需线上抓取和字段数据。 |
| D064 | [references-ahrefs/technical_seo/techseo-03-http-vs-https.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/technical_seo/techseo-03-http-vs-https.txt) | 部分满足 | E,G | 模板包含TLS和HTTP到HTTPS重定向；证书有效性/线上实际配置须部署端确认。 |
| D065 | [references-ahrefs/technical_seo/techseo-04-technical-seo-audit.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/technical_seo/techseo-04-technical-seo-audit.txt) | 部分满足 | A,E | canonical统一www；没有强制跨主机迁移/app，避免丢失原域本地会话，迁移需单独方案。 |
| D066 | [references-ahrefs/technical_seo/techseo-05-speed-up-wordpress.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/technical_seo/techseo-05-speed-up-wordpress.txt) | 不适用 | N | 项目不是WordPress，不装插件；通用缓存、压缩和资源建议另见技术证据。 |
| D067 | [references-ahrefs/technical_seo/techseo-06-internal-links.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/technical_seo/techseo-06-internal-links.txt) | 已核对 | C | 公开页面通过真实href互链，锚文本描述目标，未机械填满固定链接数。 |
| D068 | [references-ahrefs/technical_seo/techseo-07-seo-audit-fix-issues.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/technical_seo/techseo-07-seo-audit-fix-issues.txt) | 本轮修复 | D | 扩大到Vue路由以外的静态HTML，发现演示页和简略API页未声明noindex并修复。 |
| D069 | [references-ahrefs/technical_seo/techseo-08-core-web-vitals.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/technical_seo/techseo-08-core-web-vitals.txt) | 待数据 | F,G | 旧课程FID已过时；关注LCP/INP/CLS的p75字段数据，本地实验不能代替。 |
| D070 | [references-ahrefs/video_marketing/BObU_VCwnvY.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/video_marketing/BObU_VCwnvY.txt) | 不适用 | N | 未提供可索引的视频观看页，不添加视频富结果标记。 |
| D071 | [references-ahrefs/video_marketing/kAuM2nQb_P8.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/video_marketing/kAuM2nQb_P8.txt) | 不适用 | N | 没有视频选题/视频搜索需求研究场景。 |
| D072 | [references-ahrefs/video_marketing/Nh0mDbfEdVA.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/video_marketing/Nh0mDbfEdVA.txt) | 不适用 | N | 没有YouTube频道，不做频道标题或视频排名改造。 |
| D073 | [references-ahrefs/video_marketing/NR_V2nGSp98.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/video_marketing/NR_V2nGSp98.txt) | 不适用 | N | 没有视频订阅增长运营目标，不加订阅组件。 |
| D074 | [references-ahrefs/video_marketing/QCgDIhvFkCM.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/video_marketing/QCgDIhvFkCM.txt) | 不适用 | N | 品牌/教育/娱乐视频框架需真实视频计划；本轮不制作占位视频。 |
| D075 | [references-ahrefs/video_marketing/YShyWxW2iB4.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-ahrefs/video_marketing/YShyWxW2iB4.txt) | 不适用 | N | YouTube广告需要预算与活动，不属于当前站内SEO修复。 |
| D076 | [references-google/301-redirects.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/301-redirects.md) | 已核对 | E | 显式index.html和已知末尾斜线使用301；查询保留，内部目录解析不被错误重定向。 |
| D077 | [references-google/ai-features.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ai-features.md) | 已核对 | A,B | Google AI搜索沿用常规SEO；ARD是额外发现协议，不作为Google收录/排名的必要条件。 |
| D078 | [references-google/appearance-ad-network-and-translation.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/appearance-ad-network-and-translation.md) | 不适用 | N | 无广告网络翻译集成；不会为此安装广告脚本。 |
| D079 | [references-google/avoid-interstitials.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/avoid-interstitials.md) | 已核对 | B,F | 公开页无强制登录或遮挡正文的自动弹窗；用户主动打开打赏不等于侵入广告。 |
| D080 | [references-google/block-indexing.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/block-indexing.md) | 本轮修复 | D | noindex放入静态head；没有通过robots阻止这两页被抓取，否则爬虫读不到noindex。 |
| D081 | [references-google/build-sitemap.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/build-sitemap.md) | 已核对 | A | sitemap仅3个公开canonical，不含账号/管理页，不伪造lastmod。 |
| D082 | [references-google/canonicalization.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/canonicalization.md) | 部分满足 | A,G | canonical信号已给出；Google实际选择的canonical只能由URL检查报告确认。 |
| D083 | [references-google/cloaking.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/cloaking.md) | 已核对 | B,E | 该文件实际为完整SpamPolicies正文；SSR对普通用户与爬虫相同，不做UA特殊展示。 |
| D084 | [references-google/consolidate-duplicate-urls.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/consolidate-duplicate-urls.md) | 已核对 | A,C,E | 初始HTML、运行时、sitemap和内链canonical一致；不把noindex当重复页归并方法。 |
| D085 | [references-google/core-updates.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/core-updates.md) | 待数据 | G | 核心更新判断需流量、查询和日期对照；Lighthouse降分不等于算法惩罚。 |
| D086 | [references-google/core-web-vitals.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/core-web-vitals.md) | 待数据 | F,G | 本地浏览器是实验数据；真实CWV需CrUX/GSC或合规真实用户数据。 |
| D087 | [references-google/creating-helpful-content.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/creating-helpful-content.md) | 部分满足 | B,G | 清楚说明产品能力与边界，补操作型FAQ；需要继续依据真实问题维护内容。 |
| D088 | [references-google/debugging-traffic-drops.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/debugging-traffic-drops.md) | 待数据 | G | 流量下降需检查收录/安全/手动处置/需求季节性；当前没有该数据，不能归因。 |
| D089 | [references-google/do-i-need-seo.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/do-i-need-seo.md) | 不适用 | N | 聘请SEO指南不要求立刻采购代理服务；目前直接修复已知站内问题。 |
| D090 | [references-google/ecommerce-appear.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-appear.md) | 不适用 | N | 没有商品交易/购物Feed，不配置MerchantCenter或Shopping展示。 |
| D091 | [references-google/ecommerce-getting-started.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-getting-started.md) | 不适用 | N | 非电商网站；导航等通用部分已在C核对，不套商品目录。 |
| D092 | [references-google/ecommerce-launch.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-launch.md) | 不适用 | N | 无电商开店上线任务；SearchConsole发现步骤另列G。 |
| D093 | [references-google/ecommerce-pagination.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-pagination.md) | 不适用 | N | 公开页无商品分页；私有聊天历史不能为SEO暴露，也不取消聊天虚拟列表。 |
| D094 | [references-google/ecommerce-product-data.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-product-data.md) | 不适用 | N | 无商品价格/库存数据，不生成产品Feed。 |
| D095 | [references-google/ecommerce-reviews.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-reviews.md) | 部分满足 | B | compare保留noindex；没有原创验证材料前，不把竞品断言当可信评测索引。 |
| D096 | [references-google/ecommerce-site-structure.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-site-structure.md) | 已核对 | C | 首页→功能/FAQ互链，源代码入口可见，公开页无孤立页。 |
| D097 | [references-google/ecommerce-structured-data.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-structured-data.md) | 不适用 | N | 无购物商品与商家政策，不补与实际无关的commerce schema。 |
| D098 | [references-google/ecommerce-url-structure.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/ecommerce-url-structure.md) | 不适用 | N | 无电商筛选/变体URL；通用简洁URL见D157。 |
| D099 | [references-google/essentials-spam-policies.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/essentials-spam-policies.md) | 已核对 | B | 实际完整SpamPolicies；不做关键词堆砌、门页、隐藏内容、链接买卖或批量薄页。 |
| D100 | [references-google/essentials-technical.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/essentials-technical.md) | 部分满足 | A,D,E,G | 公开文本可直接取得、正常页200、未知页404；Google访问线上仍需URLInspection证据。 |
| D101 | [references-google/essentials.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/essentials.md) | 部分满足 | A,B,C,G | 技术资格、真实内容和链接已审查；搜索引擎不保证索引或排名。 |
| D102 | [references-google/faceted-navigation.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/faceted-navigation.md) | 不适用 | N | 无公开多维筛选导航；账号数据与聊天筛选不成为抓取入口。 |
| D103 | [references-google/featured-snippets.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/featured-snippets.md) | 已核对 | B | FAQ可清楚作答，但精选摘要由搜索引擎选择，不添加不存在的精选摘要schema。 |
| D104 | [references-google/fix-search-javascript.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/fix-search-javascript.md) | 已核对 | A,D,E | 直接HTML含公开正文，使用History路由与指纹资源；未知URL不回退首页软404。 |
| D105 | [references-google/google-discover.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/google-discover.md) | 不适用 | N | 目前产品入口不是新闻/兴趣内容站；现有品牌分享图不冒充Discover编辑图片，后续内容页另评估。 |
| D106 | [references-google/google-images.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/google-images.md) | 部分满足 | F | 图像有真实用途；分享图是品牌图，未宣称符合所有Discover图像建议，未来文章需真实配图。 |
| D107 | [references-google/googlebot.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/googlebot.md) | 待数据 | E,G | 不依UA放宽认证/握手；Googlebot异常访问需真实日志与DNS/IP验证，不能凭UA认定。 |
| D108 | [references-google/hacked-site.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/hacked-site.md) | 待数据 | G | 正文为SpamPolicies；本轮未执行服务器入侵取证，安全报告/部署文件完整性需运营数据。 |
| D109 | [references-google/how-search-works.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/how-search-works.md) | 已核对 | A,G | 区分发现、抓取、索引与排序；构建成功或SEO100不能证明已收录。 |
| D110 | [references-google/http-status-codes.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/http-status-codes.md) | 部分满足 | D,E,G | 已知页面200/规范重定向301/未知404；实际线上限流、5xx情况需日志验证。 |
| D111 | [references-google/image-sitemap.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/image-sitemap.md) | 不适用 | N | 没有独立图像内容集或难发现图片，当前少量品牌资源无需额外image sitemap。 |
| D112 | [references-google/INDEX.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/INDEX.md) | 参考 | I | Google索引87文件含86篇正文；目录逐项纳入清单，没有把索引当第87条技术要求。 |
| D113 | [references-google/international-seo.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/international-seo.md) | 后续项 | H | 三语切换已有；英日文没有独立URL，不能宣称多语言SEO已完成。 |
| D114 | [references-google/javascript-seo-basics.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/javascript-seo-basics.md) | 已核对 | A,D,E | 初始与运行时metadata保持一致；公开内容SSR，私有入口初始noindex。 |
| D115 | [references-google/lazy-loading.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/lazy-loading.md) | 已核对 | A,F | 首屏和FAQ正文不依赖滚动/点击请求；聊天历史懒加载是私有功能，不暴露给爬虫。 |
| D116 | [references-google/links-crawlable.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/links-crawlable.md) | 已核对 | C | 使用a href和可读锚文本，首页可抵达功能/FAQ，来源可查。 |
| D117 | [references-google/locale-adaptive.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/locale-adaptive.md) | 后续项 | H | 自适应语言不能保证各语种被发现；后续需独立语言URL与互返链接，而非同URL乱加hreflang。 |
| D118 | [references-google/localized-versions.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/localized-versions.md) | 后续项 | H | 未实现独立语言版本URL；未来需自引用、互返、完整URL及x-default，当前不伪造通过。 |
| D119 | [references-google/mobile-first-indexing.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/mobile-first-indexing.md) | 部分满足 | A,F,G | 响应式共用HTML，移动不删正文/metadata；已做本地首屏，线上移动收录仍需站长验证。 |
| D120 | [references-google/mobile-guide.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/mobile-guide.md) | 部分满足 | A,F,G | 与D119正文重复；同一响应式实现满足内容一致，真实设备与字段体验仍需持续检查。 |
| D121 | [references-google/multi-regional-sites.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/multi-regional-sites.md) | 后续项 | H | 尚无地域特供内容，不按IP强跳语言；独立语言路径作为后续明确任务。 |
| D122 | [references-google/overview-google-crawlers.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/overview-google-crawlers.md) | 待数据 | E,G | 资源指纹、缓存策略已查；爬虫负载、ETag实际响应和WAF行为须线上头部/日志。 |
| D123 | [references-google/page-experience.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/page-experience.md) | 部分满足 | F,G | 不只追单个分数，保留公开可读和/app聊天体验；真实体验需字段数据。 |
| D124 | [references-google/paid-links.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/paid-links.md) | 已核对 | B,C | 付费链接文件是完整SpamPolicies；目前未买链，公开源码链接不是付费推荐。 |
| D125 | [references-google/qualify-outbound-links.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/qualify-outbound-links.md) | 已核对 | C | 普通出处链接不用一律nofollow；无公开UGC/赞助链接，不伪造sponsored，聊天内容不索引。 |
| D126 | [references-google/reviews-system.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/reviews-system.md) | 部分满足 | B | 没有有证据的独立评测，compare不索引；不编造研究、测评结果或用户评分。 |
| D127 | [references-google/robots-create.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/robots-create.md) | 已核对 | A | 根robots为UTF8标准User-agent/Allow/Disallow/Sitemap，未添加Lighthouse不识别的Agentmap。 |
| D128 | [references-google/robots-intro.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/robots-intro.md) | 已核对 | A,D | robots不承担隐私或去索引职责；私有内容靠认证，静态工具页靠noindex。 |
| D129 | [references-google/robots-meta-tag.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/robots-meta-tag.md) | 本轮修复 | D | 独立HTML初始robots noindex,follow；允许爬取以读取声明，没有误用nofollow隐藏正文。 |
| D130 | [references-google/robots-submit.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/robots-submit.md) | 待数据 | G | 仓库robots已更新；Google缓存何时刷新需线上抓取或SearchConsole请求，未冒充提交成功。 |
| D131 | [references-google/robots-txt.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/robots-txt.md) | 已核对 | A | 规则位于站点根、标准语法，私有页面未robots屏蔽到读不到noindex。 |
| D132 | [references-google/safesearch.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/safesearch.md) | 不适用 | N | 公开产品页无成人内容，不添加成人评级；私有UGC不面向索引，平台治理另属业务安全范围。 |
| D133 | [references-google/search-console-start.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/search-console-start.md) | 待数据 | G | 未连接站长账号；所有权、sitemap提交、索引覆盖、安全/手动操作需站长确认。 |
| D134 | [references-google/search-ranking-systems.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/search-ranking-systems.md) | 已核对 | B,G | 理解去重、语义和可靠性系统；不声称可通过固定关键词公式操控排名。 |
| D135 | [references-google/seo-starter-guide.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/seo-starter-guide.md) | 部分满足 | A,B,C,G | 公开内容、标题、摘要、链接已落实；实际搜索效果须等待抓取并从站长数据评估。 |
| D136 | [references-google/site-move.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/site-move.md) | 后续项 | E,H | 当前没有迁站；未来统一apex/www或语言路径迁移要URL映射、会话存储迁移及监控。 |
| D137 | [references-google/site-pause.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/site-pause.md) | 不适用 | N | 未安排停站；发生短期维护再按503/Retry-After评估，不能因聊天故障把全站改noindex。 |
| D138 | [references-google/sitemap.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/sitemap.md) | 已核对 | A,C | 小站仍提供sitemap但不依赖它代替内链；只列真正希望索引的页面。 |
| D139 | [references-google/sneaky-redirects.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/sneaky-redirects.md) | 已核对 | E | 正文为完整SpamPolicies；无UA/设备诱导跳转，index规范化保持原有路径与参数。 |
| D140 | [references-google/snippet.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/snippet.md) | 已核对 | A,B | 每页摘要准确且独立；Google可重写摘要，没有固定字数或强制展示承诺。 |
| D141 | [references-google/spam-policies-full.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/spam-policies-full.md) | 已核对 | B | 完整垃圾政策与其他文件重复；未以批量内容、无关词或虚假功能操纵排名。 |
| D142 | [references-google/spam-report.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/spam-report.md) | 不适用 | N | 未发现需举报的外部垃圾站点，不擅自提交举报或以此替代自身修复。 |
| D143 | [references-google/spampolicy-full.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/spampolicy-full.md) | 已核对 | B | 与D141近重复全文；不额外创造一套检查规则，沿用同一垃圾政策证据。 |
| D144 | [references-google/structured-data-article.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-article.md) | 不适用 | N | 当前公开页为产品说明/FAQ，不伪装新闻Article，不编造作者履历、发布日期。 |
| D145 | [references-google/structured-data-event.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-event.md) | 不适用 | N | 无真实可报名活动及时间地点，不加Event。 |
| D146 | [references-google/structured-data-faqpage.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-faqpage.md) | 已核对 | B | 旧FAQ富结果资料已过时；普通产品FAQ提供可见回答，不承诺GoogleFAQ富结果。 |
| D147 | [references-google/structured-data-intro.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-intro.md) | 部分满足 | A,G | 使用JSON-LD WebSite/BreadcrumbList且与可见页面对应；线上RichResults/索引需工具数据。 |
| D148 | [references-google/structured-data-job-posting.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-job-posting.md) | 不适用 | N | 无招聘岗位及申请入口，不加JobPosting或调用招聘IndexingAPI。 |
| D149 | [references-google/structured-data-local-business.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-local-business.md) | 不适用 | N | 无实体营业地点，不加LocalBusiness、营业时间或虚构坐标。 |
| D150 | [references-google/structured-data-policies.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-policies.md) | 已核对 | A,B | JSON-LD只描述真实可见内容；不伪造评分/价格/身份来填结构化字段。 |
| D151 | [references-google/structured-data-product.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-product.md) | 不适用 | N | 无电商产品详情/购买交易，不加Product/库存/配送/退货政策。 |
| D152 | [references-google/structured-data-recipe.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-recipe.md) | 不适用 | N | 无食谱内容，不加Recipe/烹饪步骤/营养数据。 |
| D153 | [references-google/structured-data-software-app.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-software-app.md) | 后续项 | G | 软件富结果需真实价格/评价等资格资料；现有资料不足，不复制示例伪造AggregateRating。 |
| D154 | [references-google/structured-data-video.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/structured-data-video.md) | 不适用 | N | 无视频观看页、时码/直播，不加VideoObject/Clip/BroadcastEvent。 |
| D155 | [references-google/title-link.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/title-link.md) | 已核对 | A,B | 各页title精简、准确、语言匹配且有唯一主标题；搜索结果标题可能由Google改写。 |
| D156 | [references-google/trends-start.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/trends-start.md) | 待数据 | G | 选题需产品相关并结合趋势/查询数据；未获取Trends数据，不追无关热点。 |
| D157 | [references-google/url-structure.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/url-structure.md) | 已核对 | A,C,E | History路径、少参数、公开canonical明确；#downloads仅同页锚点，不用fragment切换独立内容。 |
| D158 | [references-google/verifying-googlebot.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/verifying-googlebot.md) | 待数据 | G | Googlebot真伪应反查DNS再正查或用官方IP范围；无线上日志，未验证具体请求。 |
| D159 | [references-google/video-seo.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/video-seo.md) | 不适用 | N | 没有视频观看内容与媒体文件，不为SEO新增视频播放器请求。 |
| D160 | [references-google/video-sitemap.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/video-sitemap.md) | 不适用 | N | 无视频库存，不创建video sitemap或mRSS。 |
| D161 | [references-google/web-stories-policy.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/web-stories-policy.md) | 不适用 | N | 未制作AMP WebStories，常规聊天UI不是Story。 |
| D162 | [references-google/web-stories-seo.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-google/web-stories-seo.md) | 不适用 | N | 无WebStories叙事/海报/视频，不为此改首页为故事轮播。 |
| D163 | [references-moz/growing-popularity-and-links.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/growing-popularity-and-links.txt) | 待运营 | C,G | 可信链接应自然且相关；项目出处已给，外链质量与新增推荐仍需运营与数据。 |
| D164 | [references-moz/how-search-engines-operate.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/how-search-engines-operate.txt) | 部分满足 | A,D,E,G | 公共内容可抓取、登录内容隔离；GSC旧URL参数工具等历史建议不照搬。 |
| D165 | [references-moz/INDEX.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/INDEX.md) | 参考 | I | Moz索引含10章原文和总结，12份均列出，Google当前文档优先。 |
| D166 | [references-moz/introduction.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/introduction.txt) | 参考 | I | SEO学习框架可用于优先级，不需为课程推广/认证购买服务。 |
| D167 | [references-moz/keyword-research.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/keyword-research.txt) | 待数据 | B,G | 页面级意图已映射；用户需求、地域、长尾流量与排名尚无量化证据。 |
| D168 | [references-moz/measuring-and-tracking-success.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/measuring-and-tracking-success.txt) | 待数据 | G | 指标应包括自然访问和有意义的使用/下载；无数据时不把停留时长/DA当排名证明。 |
| D169 | [references-moz/MOZ-LEARNING-SUMMARY.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/MOZ-LEARNING-SUMMARY.md) | 部分满足 | A,B,G | 综合清单已映射；旧UA/GA工具、固定字符数及统计断言不机械照搬。 |
| D170 | [references-moz/on-page-seo.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/on-page-seo.txt) | 部分满足 | A,B,C | 同意图合页、清晰标题与内链，避开薄页/堆词；新增FAQ解决实际任务。 |
| D171 | [references-moz/quick-start-guide.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/quick-start-guide.txt) | 部分满足 | A,D,F,G | 技术和内容检查可完成；搜索后台、关键词和外链基线仍待数据。 |
| D172 | [references-moz/seo-glossary.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/seo-glossary.txt) | 参考 | I | 术语表用于统一表达；rel next/prev、noarchive等历史用法不当当前Google优化要求。 |
| D173 | [references-moz/technical-seo.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/technical-seo.txt) | 部分满足 | A,E,F,G | SSR、指纹资源与响应式已实现；不为SEO安装AMP框架或改私有聊天加载逻辑。 |
| D174 | [references-moz/why-search-engine-marketing-is-necessary.txt](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/references-moz/why-search-engine-marketing-is-necessary.txt) | 部分满足 | B,G | 目标是相关用户找到并使用产品；白帽原则已核对，业务成效需后续实际数据。 |
| D175 | [scripts/README.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/scripts/README.md) | 不适用 | N | PDF工具只在用户明确要求PDF时使用；本次交付可维护Markdown和JSON。 |
| D176 | [SKILL.md](https://github.com/lesishu/seo-guide-skill/blob/ab8af275605f249b8a3eb620ba0cd1b74eb01436/SKILL.md) | 参考 | I | 按全站体检入口覆盖全部目录；未要求安装skill或执行外联，Google官方资料优先。 |
