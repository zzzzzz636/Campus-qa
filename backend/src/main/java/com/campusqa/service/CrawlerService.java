package com.campusqa.service;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.CrawlerPageResult;
import com.campusqa.dto.CrawlerRunResult;
import com.campusqa.repository.KnowledgeRepository;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CrawlerService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final int MAX_DEPTH = 4;
    private static final int REQUEST_TIMEOUT_MS = 15000;
    private static final int REQUEST_DELAY_MS = 1500;   // 请求间隔，避免反爬
    private static final String DEFAULT_CATEGORY = "校园资料";

    // 浏览器级 User-Agent
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    // 不过滤教务类链接
    private static final Pattern BLOCKED_PATH_PATTERN = Pattern.compile(
            "(/login|/auth|/sso|/cas|/oauth)", Pattern.CASE_INSENSITIVE);

    // 按关键词猜测分类（LinkedHashMap 保证匹配顺序，高频具体词优先）
    private static final Map<String, String> CATEGORY_KEYWORDS = new java.util.LinkedHashMap<>();
    static {
        CATEGORY_KEYWORDS.put("图书馆|借阅|图书|阅览|自习|座位|lib\\.|馆际|文献|数据库|查新|馆藏|馆内", "图书馆");
        CATEGORY_KEYWORDS.put("食堂|餐饮|就餐|饭菜|窗口|夜宵|早餐|午餐|晚餐|canteen|饭堂", "食堂");
        CATEGORY_KEYWORDS.put("选课|教务|成绩|学籍|培养方案|jwc|课程|学分|绩点|补考|缓考", "教务");
        CATEGORY_KEYWORDS.put("宿舍|报修|维修|水电|住宿|公寓|寝室", "宿舍报修");
        CATEGORY_KEYWORDS.put("校园卡|一卡通|ecard|充值|挂失|补办", "校园卡");
        CATEGORY_KEYWORDS.put("打印|文印|复印|印刷|自助文印|云打印", "打印");
        CATEGORY_KEYWORDS.put("网络|校园网|wifi|vpn|网费|上网|账号", "校园网络");
        CATEGORY_KEYWORDS.put("校历|放假|寒假|暑假|考试周|教学周|开学|校车|班车", "校历");
        CATEGORY_KEYWORDS.put("医院|医保|就诊|体检|门诊|校医|就医|报销", "校医院");
        CATEGORY_KEYWORDS.put("社团|招新|社联|俱乐部|协会|学生会", "社团");
        CATEGORY_KEYWORDS.put("奖学|助学|贷款|勤工|补助|奖学金|助学金", "奖助学金");
        CATEGORY_KEYWORDS.put("保卫|安全|门禁|停车|交通|出入|保卫处", "安全保卫");
    }

    // 有意义内容的最小长度
    private static final int MIN_CONTENT_LENGTH = 60;

    // 记录最后一次 fetch 错误，便于调试
    private String lastFetchError = null;

    private final KnowledgeRepository knowledgeRepository;

    public CrawlerService(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    @Transactional
    public ApiResponse<CrawlerRunResult> run(Integer limit, Integer maxDepth) {
        List<String> seedUrls;
        try {
            seedUrls = readSeedUrls();
        } catch (IOException ex) {
            CrawlerRunResult result = new CrawlerRunResult(0, 1,
                    List.of(new CrawlerPageResult(null, null, null, false,
                            "读取种子文件失败：" + ex.getMessage())));
            return ApiResponse.failure("官网资料采集失败", result);
        }

        if (seedUrls.isEmpty()) {
            CrawlerRunResult result = new CrawlerRunResult(0, 1,
                    List.of(new CrawlerPageResult(null, null, null, false,
                            "tools/seed_urls.txt 中没有可采集 URL")));
            return ApiResponse.failure("官网资料采集失败", result);
        }

        int crawlLimit = normalizeInt(limit, DEFAULT_LIMIT, 1, MAX_LIMIT);
        int depth = normalizeInt(maxDepth, DEFAULT_MAX_DEPTH, 1, MAX_DEPTH);
        Set<String> visited = new HashSet<>();
        List<CrawlerPageResult> items = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (String seedUrl : seedUrls) {
            if (items.size() >= crawlLimit) break;
            CrawlResult cr = crawlRecursive(seedUrl, depth, crawlLimit - items.size(), visited, new HashSet<>());
            items.addAll(cr.items);
            successCount += cr.success;
            failCount += cr.fail;
        }

        CrawlerRunResult result = new CrawlerRunResult(successCount, failCount, items);
        return ApiResponse.success("官网资料采集完成（成功 " + successCount + "，失败 " + failCount + "）", result);
    }

    /**
     * 递归爬取：当前页 → 提取子链接 → 继续爬
     */
    private CrawlResult crawlRecursive(String url, int remainingDepth, int remainingLimit,
                                        Set<String> visited, Set<String> currentDomainUrls) {
        CrawlResult result = new CrawlResult();
        if (remainingDepth <= 0 || remainingLimit <= 0) return result;
        if (visited.contains(url)) return result;
        if (!isAllowedPublicUrl(url)) return result;

        visited.add(url);
        String domain = extractDomain(url);
        if (domain == null) {
            result.fail++;
            result.items.add(new CrawlerPageResult(url, null, null, false, "无法解析域名"));
            return result;
        }

        // 检查是否已入库
        if (knowledgeRepository.existsBySourceUrl(url)) {
            result.items.add(new CrawlerPageResult(url, null, null, false, "该 URL 已存在于知识资料库"));
            // 已存在不增加 fail 计数，但继续尝试发现子链接
            // 这里仍然尝试获取子链接以发现新内容
            Document doc = fetchDocument(url);
            if (doc != null) {
                List<String> childUrls = discoverLinks(doc, domain, visited);
                for (String childUrl : childUrls) {
                    if (result.totalItems() >= remainingLimit) break;
                    CrawlResult childResult = crawlRecursive(childUrl, remainingDepth - 1,
                            remainingLimit - result.totalItems(), visited, currentDomainUrls);
                    result.merge(childResult);
                }
            }
            return result;
        }

        // 延迟，避免触发反爬
        sleep(REQUEST_DELAY_MS);

        // 抓取页面
        Document doc = fetchDocument(url);
        if (doc == null) {
            String errMsg = "页面访问失败";
            if (lastFetchError != null) {
                errMsg = "访问失败：" + lastFetchError;
            }
            result.fail++;
            result.items.add(new CrawlerPageResult(url, null, null, false, errMsg));
            return result;
        }

        // 提取标题和正文
        String title = extractTitle(doc, url);
        String content = extractContent(doc);

        // 过滤列表页/导航页（分页文字、面包屑、链接密度过高）
        if (isListOrNavPage(doc, content)) {
            result.fail++;
            result.items.add(new CrawlerPageResult(url, title, null, false,
                    "疑似列表页或导航页，已跳过（避免将目录页作为正文入库）"));
            // 仍然尝试发现子链接
            List<String> childUrls = discoverLinks(doc, domain, visited);
            for (String childUrl : childUrls) {
                if (result.totalItems() >= remainingLimit) break;
                CrawlResult childResult = crawlRecursive(childUrl, remainingDepth - 1,
                        remainingLimit - result.totalItems(), visited, currentDomainUrls);
                result.merge(childResult);
            }
            return result;
        }

        if (!StringUtils.hasText(content) || content.length() < MIN_CONTENT_LENGTH) {
            result.fail++;
            result.items.add(new CrawlerPageResult(url, title, null, false,
                    "页面正文不足 " + MIN_CONTENT_LENGTH + " 字符（实际 " +
                    (content == null ? 0 : content.length()) + " 字符）"));
            // 仍然尝试发现子链接
            List<String> childUrls = discoverLinks(doc, domain, visited);
            for (String childUrl : childUrls) {
                if (result.totalItems() >= remainingLimit) break;
                CrawlResult childResult = crawlRecursive(childUrl, remainingDepth - 1,
                        remainingLimit - result.totalItems(), visited, currentDomainUrls);
                result.merge(childResult);
            }
            return result;
        }

        // 智能分类
        String category = guessCategory(title, content, url);

        // 来源类型
        String sourceType = determineSourceType(url);

        // 入库
        Long id = knowledgeRepository.save(title, content, category, url, sourceType);
        if (id == null) {
            result.fail++;
            result.items.add(new CrawlerPageResult(url, title, null, false, "知识资料入库失败"));
            return result;
        }

        result.success++;
        result.items.add(new CrawlerPageResult(url, title, id, true, "采集并入库成功"));

        // 发现并爬取子链接
        List<String> childUrls = discoverLinks(doc, domain, visited);
        for (String childUrl : childUrls) {
            if (result.totalItems() >= remainingLimit) break;
            CrawlResult childResult = crawlRecursive(childUrl, remainingDepth - 1,
                    remainingLimit - result.totalItems(), visited, currentDomainUrls);
            result.merge(childResult);
        }

        return result;
    }

    /** 全局 SSL 上下文，信任所有证书（校园网站常用自签/国产证书） */
    private static final SSLContext TRUST_ALL_SSL_CONTEXT = createTrustAllSslContext();

    private static SSLContext createTrustAllSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509ExtendedTrustManager() {
                        public void checkClientTrusted(X509Certificate[] c, String a) {}
                        public void checkServerTrusted(X509Certificate[] c, String a) {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] c, String a, Socket s) {}
                        public void checkServerTrusted(X509Certificate[] c, String a, Socket s) {}
                        public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) {}
                        public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) {}
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, null);
            return ctx;
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * 用 Jsoup 获取页面（正常SSL → 宽松SSL → HTTP降级）
     */
    private Document fetchDocument(String url) {
        // 第一次：正常 SSL
        try {
            return buildConnection(url, null).get();
        } catch (IOException e) {
            lastFetchError = e.toString();
        }
        // 第二次：跳过 SSL 证书 + 忽略 Content-Type
        try {
            return buildConnection(url, TRUST_ALL_SSL_CONTEXT)
                    .ignoreContentType(true)
                    .get();
        } catch (IOException e2) {
            lastFetchError = e2.toString();
        }
        // 第三次：HTTPS 降级为 HTTP（有些校园站 TLS 版本太旧被 Java 高版本禁用）
        if (url.startsWith("https://")) {
            String httpUrl = "http://" + url.substring("https://".length());
            try {
                return buildConnection(httpUrl, null)
                        .ignoreContentType(true)
                        .get();
            } catch (IOException e3) {
                lastFetchError = e3.toString();
            }
        }
        return null;
    }

    private Connection buildConnection(String url, SSLContext sslContext) {
        Connection conn = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(REQUEST_TIMEOUT_MS)
                .followRedirects(true)
                .maxBodySize(2 * 1024 * 1024)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1");
        if (sslContext != null) {
            conn.sslSocketFactory(sslContext.getSocketFactory());
        }
        return conn;
    }

    /**
     * 从页面中发现同域名下的子链接
     */
    private List<String> discoverLinks(Document doc, String domain, Set<String> visited) {
        List<String> links = new ArrayList<>();
        Elements anchors = doc.select("a[href]");
        Set<String> seen = new HashSet<>();

        for (Element anchor : anchors) {
            String href = anchor.attr("abs:href"); // Jsoup 自动解析为绝对 URL
            if (!StringUtils.hasText(href)) continue;

            // 只保留同域名链接
            String linkDomain = extractDomain(href);
            if (!domain.equals(linkDomain)) continue;

            // 去重、去已访问
            String normalized = normalizeUrl(href);
            if (seen.contains(normalized) || visited.contains(normalized)) continue;
            seen.add(normalized);

            // 过滤
            if (!isAllowedPublicUrl(normalized)) continue;
            if (knowledgeRepository.existsBySourceUrl(normalized)) continue;

            // 优先爬取看起来像信息页的链接
            if (isLikelyInfoPage(normalized)) {
                links.add(0, normalized); // 插入队首
            } else {
                links.add(normalized);    // 追加队尾
            }

            if (links.size() >= 50) break; // 单页最多发现 50 个子链接
        }
        return links;
    }

    /**
     * 判断是否可能是信息页面（非首页、非纯导航页）
     */
    private boolean isLikelyInfoPage(String url) {
        String lower = url.toLowerCase();
        // 包含信息类路径关键词
        return lower.contains("/info/") || lower.contains("/xxgk/") || lower.contains("/tzgg/")
                || lower.contains("/detail") || lower.contains("/content") || lower.contains("/article/")
                || lower.contains("/news/") || lower.contains("/notice/") || lower.contains("/announcement/")
                || lower.matches(".*\\.(htm|html|shtml|jsp|aspx|php)\\b.*")
                || lower.contains("?")  // 带参数的一般是内容页
                || !lower.matches("https?://[^/]+/?$"); // 不是纯域名首页
    }

    /**
     * 提取标题
     */
    private String extractTitle(Document doc, String url) {
        String title = doc.title();
        if (StringUtils.hasText(title)) {
            return cleanText(title);
        }
        // 尝试 h1
        Elements h1s = doc.select("h1");
        if (!h1s.isEmpty()) {
            return cleanText(h1s.first().text());
        }
        // 回退到 URL
        return url;
    }

    // 页面元数据关键词正则（浏览次数、发布时间、来源等模板内容）
    private static final java.util.regex.Pattern METADATA_STRIP_PATTERN = java.util.regex.Pattern.compile(
            "\\s*(浏览(次数)?|点击(次数|量)?|访问(量|次数)|来源|作者|供稿|编辑|审核|发布者" +
            "|责任编辑|文字|摄影|图片|发文单位|发布部门|信息员|录入|审核人" +
            "|发布日期|发布时间|更新日期|创建日期|发文时间)" +
            "[：:：\\s]*[^\\s。！？，,;；]*[\\s。！？]*",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * 提取正文（智能选择主要内容区域，并清洗页面元数据）
     */
    private String extractContent(Document doc) {
        // 移除干扰元素 + 元数据标签
        doc.select("script, style, noscript, nav, footer, header, iframe, svg, canvas, " +
                "form, input, button, select, option, textarea, " +
                ".sidebar, .menu, .nav, .footer, .header, " +
                // 高校 CMS 常见元数据类
                ".arti_views, .arti_publisher, .arti_update, .arti_origin, " +
                ".WP_VisitCount, .visit_count, .click_count, .view_count, " +
                ".article-info, .article_other, .info_source, .info_author, " +
                ".time, .pub_time, .publish_time, .post_time").remove();

        // 按优先级尝试主要选择器
        String[] contentSelectors = {
                "article", ".article", ".content", ".main-content", ".post-content", ".entry-content",
                "#content", "#article", "#artibody", ".art_con", ".text-con", ".detail-content",
                ".news-content", ".info-content", ".page-content", ".wp_content", ".TRS_Editor",
                ".Custom_UnionStyle", ".con_text", "#UCAP-CONTENT",
                "main", ".main", "[role=main]"
        };

        for (String selector : contentSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                String text = extractStructuredText(elements.first());
                if (StringUtils.hasText(text) && text.length() >= MIN_CONTENT_LENGTH) {
                    return stripMetadata(text);
                }
            }
        }

        // 回退到 body 文本
        Elements body = doc.select("body");
        if (!body.isEmpty()) {
            String text = extractStructuredText(body.first());
            // 截取合理长度（前 5000 字符）
            if (text.length() > 5000) {
                text = text.substring(0, 5000);
            }
            return stripMetadata(text);
        }
        return "";
    }

    /**
     * 结构化提取文本：保留表格、列表、段落的换行结构
     * 不再把所有内容压成一行空格串
     */
    private String extractStructuredText(Element root) {
        StringBuilder sb = new StringBuilder();
        extractNodeText(root, sb);
        // 清理：连续空行合并，行内多余空白合并
        String text = sb.toString()
                .replace('　', ' ')
                .replace(' ', ' ')  // &nbsp;
                .replaceAll("[ \t]+", " ")    // 行内多空格变单空格
                .replaceAll(" ?\n ?", "\n")   // 行首行尾空格去掉
                .replaceAll("\n{3,}", "\n\n") // 最多保留一个空行
                .trim();
        return text;
    }

    private void extractNodeText(Element node, StringBuilder sb) {
        for (org.jsoup.nodes.Node child : node.childNodes()) {
            if (child instanceof Element el) {
                String tag = el.tagName().toLowerCase();
                // 块级元素前后加换行
                boolean isBlock = switch (tag) {
                    case "p", "div", "section", "article", "header", "footer",
                         "h1", "h2", "h3", "h4", "h5", "h6",
                         "li", "tr", "hr", "blockquote", "pre", "figcaption" -> true;
                    default -> false;
                };
                boolean isTableCell = "td".equals(tag) || "th".equals(tag);

                if (isBlock && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                // 表格单元格之间用竖线分隔
                if (isTableCell && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append(" | ");
                }
                // br 换行
                if ("br".equals(tag)) {
                    sb.append('\n');
                    continue;
                }
                // img 用 alt 文本
                if ("img".equals(tag)) {
                    String alt = el.attr("alt");
                    if (StringUtils.hasText(alt)) {
                        sb.append('[').append(alt.trim()).append(']');
                    }
                    continue;
                }

                extractNodeText(el, sb);

                // 块级元素和表格行结束后换行
                if (isBlock) {
                    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                }
                if ("tr".equals(tag)) {
                    sb.append('\n');
                }
            } else if (child instanceof org.jsoup.nodes.TextNode tn) {
                String t = tn.getWholeText();
                if (t != null && !t.isBlank()) {
                    sb.append(t);
                }
            }
        }
    }

    /**
     * 清洗正文中的页面模板元数据（浏览次数、发布时间、来源等）
     */
    private String stripMetadata(String text) {
        if (!StringUtils.hasText(text)) return "";
        // 按行处理元数据（避免跨行替换把换行符弄丢）
        StringBuilder result = new StringBuilder();
        for (String line : text.split("\n")) {
            String cleaned = METADATA_STRIP_PATTERN.matcher(line).replaceAll("");
            cleaned = cleaned.trim();
            if (!cleaned.isEmpty()) {
                if (!result.isEmpty()) result.append('\n');
                result.append(cleaned);
            }
        }
        return result.toString();
    }

    /**
     * 智能分类：标题优先匹配 → 内容+URL 综合匹配 → 默认分类
     */
    private String guessCategory(String title, String content, String url) {
        String lowerTitle = (title != null ? title : "").toLowerCase();
        String lowerContent = (content != null ? content : "").toLowerCase();
        String lowerUrl = (url != null ? url : "").toLowerCase();

        // 第一轮：只在标题中匹配（标题关键词更可靠）
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(lowerTitle).find()) {
                return entry.getValue();
            }
        }

        // 第二轮：标题+URL 联合匹配（URL 中的域名信息也较可靠）
        String titleAndUrl = lowerTitle + " " + lowerUrl;
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(titleAndUrl).find()) {
                return entry.getValue();
            }
        }

        // 第三轮：全文综合匹配
        String combined = lowerTitle + " " + lowerContent + " " + lowerUrl;
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(combined).find()) {
                return entry.getValue();
            }
        }

        return DEFAULT_CATEGORY;
    }

    /**
     * 判断来源类型
     */
    private String determineSourceType(String url) {
        if (url.contains("scut.edu.cn")) {
            return "华工官网";
        }
        return "官网爬虫";
    }

    // ===================== 工具方法 =====================

    private List<String> readSeedUrls() throws IOException {
        Path seedPath = resolveSeedPath();
        if (!Files.exists(seedPath)) {
            throw new IOException("未找到 " + seedPath);
        }

        List<String> urls = new ArrayList<>();
        for (String line : Files.readAllLines(seedPath, StandardCharsets.UTF_8)) {
            String value = line.trim();
            if (!value.isEmpty() && !value.startsWith("#")) {
                urls.add(value);
            }
        }
        return urls;
    }

    private Path resolveSeedPath() {
        Path current = Path.of("").toAbsolutePath();
        Path direct = current.resolve("tools").resolve("seed_urls.txt");
        if (Files.exists(direct)) {
            return direct;
        }
        return current.getParent().resolve("tools").resolve("seed_urls.txt");
    }

    /**
     * 检测是否为列表页/导航页（不应作为正文入库）
     * 华工 CMS 几乎所有页面 URL 都含 /list.htm，不能仅凭 URL 判断，
     * 必须通过内容特征（分页、日期密度、链接密度）综合判断。
     */
    private boolean isListOrNavPage(Document doc, String content) {
        if (content == null) return true;

        // 1. 分页标记检测（强信号：一定是列表页）
        java.util.regex.Pattern paginationPattern = java.util.regex.Pattern.compile(
                "每页\\s*\\d+\\s*记录|总共\\s*\\d+\\s*记录|第一页|上一页|下一页|尾页|跳转到|页码\\s*\\d+/\\d+");
        if (paginationPattern.matcher(content).find()) {
            return true;
        }

        // 2. 面包屑 + 大量日期 → 文章列表（如"本馆资讯"）
        if (content.contains("当前位置") || content.contains("您的位置")) {
            java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
            long dateCount = datePattern.matcher(content).results().count();
            if (dateCount >= 5) { // 至少5个日期才判定为列表
                return true;
            }
        }

        // 3. 链接密度过高（正文短但链接极多 → 导航/目录页）
        int linkCount = doc.select("a[href]").size();
        int textLength = content.length();
        if (textLength > 0 && linkCount > 0) {
            double linksPerChar = (double) linkCount / textLength;
            if (linksPerChar > 0.03) { // 每 ~33 字符超过 1 个链接
                return true;
            }
        }

        return false;
    }

    private boolean isAllowedPublicUrl(String url) {
        if (!StringUtils.hasText(url)) return false;
        String lower = url.toLowerCase();
        // 必须 http/https
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false;
        // 排除登录/认证页面
        if (BLOCKED_PATH_PATTERN.matcher(lower).find()) return false;
        // 排除静态资源
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|bmp|svg|ico|webp|pdf|doc|docx|xls|xlsx|zip|rar|mp4|mp3|avi|css|js|woff|ttf|eot)(\\?.*)?$")) {
            return false;
        }
        return true;
    }

    private String extractDomain(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (host == null) return null;
            // 统一为 www 前缀的主域名
            return host.replaceFirst("^www\\d*\\.", "");
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeUrl(String url) {
        // 去掉尾部 fragment
        int fragmentIdx = url.indexOf('#');
        if (fragmentIdx >= 0) {
            url = url.substring(0, fragmentIdx);
        }
        // 去掉尾部斜杠
        while (url.endsWith("/") && url.length() > 8) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String cleanText(String text) {
        if (!StringUtils.hasText(text)) return "";
        return text.replace('　', ' ')
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int normalizeInt(Integer value, int defaultValue, int min, int max) {
        if (value == null) return defaultValue;
        return Math.max(min, Math.min(value, max));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 采集单个指定公开 URL（管理员手动输入）
     */
    @Transactional
    public ApiResponse<CrawlerRunResult> runOne(String url, Integer maxDepth) {
        if (!StringUtils.hasText(url)) {
            CrawlerRunResult result = new CrawlerRunResult(0, 1,
                    List.of(new CrawlerPageResult(null, null, null, false, "URL 不能为空")));
            return ApiResponse.failure("请提供要采集的公开 URL", result);
        }

        if (!isAllowedPublicUrl(url.trim())) {
            CrawlerRunResult result = new CrawlerRunResult(0, 1,
                    List.of(new CrawlerPageResult(url, null, null, false,
                            "URL 不合法：仅支持公开 http/https 链接，不能是登录页、静态资源或内网地址")));
            return ApiResponse.failure("URL 不合法", result);
        }

        int depth = normalizeInt(maxDepth, DEFAULT_MAX_DEPTH, 1, MAX_DEPTH);
        CrawlResult cr = crawlRecursive(url.trim(), depth, MAX_LIMIT, new HashSet<>(), new HashSet<>());

        CrawlerRunResult result = new CrawlerRunResult(cr.success, cr.fail, cr.items);
        String msg = cr.success > 0
                ? "指定 URL 采集完成（成功 " + cr.success + "，失败 " + cr.fail + "）"
                : "指定 URL 采集失败";
        if (cr.fail > 0 && cr.success == 0) {
            return ApiResponse.failure(msg, result);
        }
        return ApiResponse.success(msg, result);
    }

    // ===================== 内部类 =====================

    private static class CrawlResult {
        int success = 0;
        int fail = 0;
        List<CrawlerPageResult> items = new ArrayList<>();

        int totalItems() {
            return success + fail;
        }

        void merge(CrawlResult other) {
            this.success += other.success;
            this.fail += other.fail;
            this.items.addAll(other.items);
        }
    }
}
