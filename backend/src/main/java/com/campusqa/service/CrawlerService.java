package com.campusqa.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.CrawlerPageResult;
import com.campusqa.dto.CrawlerRunResult;
import com.campusqa.model.KnowledgeDoc;
import com.campusqa.repository.KnowledgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CrawlerService {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 10;
    private static final int MIN_CHINESE_CONTENT_LENGTH = 80;
    private static final int MAX_DETAIL_LINKS_PER_SEED = 30;
    private static final String DEFAULT_CATEGORY = "校园资料";
    private static final String SOURCE_TYPE = "官网爬虫";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NOISE_BLOCK_PATTERN = Pattern.compile("<(script|style|nav|header|footer|iframe|noscript|svg|canvas)[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ARTICLE_MAIN_PATTERN = Pattern.compile("<(article|main)\\b[^>]*>(.*?)</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONTENT_CONTAINER_PATTERN = Pattern.compile("<([a-zA-Z0-9]+)\\b(?=[^>]*(?:class|id)\\s*=\\s*['\"][^'\"]*(?:content|article|news|detail|text)[^'\"]*['\"])[^>]*>(.*?)</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DATA_FOCUS_URL_PATTERN = Pattern.compile("\\s*data-focus-url\\s*=\\s*(['\"]).*?\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE_RESIDUE_PATTERN = Pattern.compile("\\b(?:class|id|href|src|style|title|target|rel|alt|data-[\\w-]+)\\s*=\\s*(['\"]).*?\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_RESIDUE_PATTERN = Pattern.compile("\\b(?:function|var|let|const|window|document)\\b[^。！？；\\n]{0,160}", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([\\w\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_PATTERN = Pattern.compile("<a\\b[^>]*href\\s*=\\s*(['\"])(.*?)\\1[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FILE_LINK_PATTERN = Pattern.compile(".*\\.(?:jpg|jpeg|png|gif|webp|svg|ico|pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|7z)(?:[?#].*)?$", Pattern.CASE_INSENSITIVE);

    private final KnowledgeRepository knowledgeRepository;
    private final HttpClient httpClient;

    public CrawlerService(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional
    public ApiResponse<CrawlerRunResult> run(Integer limit) {
        List<String> urls;
        try {
            urls = readSeedUrls();
        } catch (IOException ex) {
            CrawlerRunResult result = new CrawlerRunResult(
                    0,
                    1,
                    List.of(new CrawlerPageResult(null, null, null, false, "读取 URL 种子文件失败：" + ex.getMessage()))
            );
            return ApiResponse.failure("官网资料采集失败", result);
        }

        if (urls.isEmpty()) {
            CrawlerRunResult result = new CrawlerRunResult(
                    0,
                    1,
                    List.of(new CrawlerPageResult(null, null, null, false, "tools/seed_urls.txt 中没有可采集 URL"))
            );
            return ApiResponse.failure("官网资料采集失败", result);
        }

        int runLimit = normalizeLimit(limit);
        List<CrawlerPageResult> items = new ArrayList<>();
        int successCount = 0;
        List<List<String>> targetGroups = urls.stream()
                .map(this::discoverTargetUrls)
                .toList();

        for (int index = 0; index < MAX_DETAIL_LINKS_PER_SEED && successCount < runLimit; index++) {
            boolean hasMoreTargets = false;
            for (List<String> targetUrls : targetGroups) {
                if (index >= targetUrls.size()) {
                    continue;
                }
                hasMoreTargets = true;
                String targetUrl = targetUrls.get(index);
                CrawlerPageResult item = crawlOne(targetUrl);
                items.add(item);
                if (item.success()) {
                    successCount++;
                }
                if (successCount >= runLimit) {
                    break;
                }
            }
            if (!hasMoreTargets) {
                break;
            }
        }

        int failCount = items.size() - successCount;
        CrawlerRunResult result = new CrawlerRunResult(successCount, failCount, items);
        return ApiResponse.success("官网资料采集完成", result);
    }

    private CrawlerPageResult crawlOne(String url) {
        if (!isAllowedPublicUrl(url)) {
            return new CrawlerPageResult(url, null, null, false, "仅允许采集公开 http/https 页面");
        }

        try {
            KnowledgeDoc existingDoc = knowledgeRepository.findBySourceUrl(url).orElse(null);
            if (existingDoc != null) {
                return new CrawlerPageResult(url, existingDoc.title(), existingDoc.id(), false, "该 URL 已存在于知识资料库");
            }

            FetchedPage page = fetchPage(url);
            String html = page.html();
            String title = extractTitle(html);
            String content = extractContent(html, title);
            if (countChineseCharacters(content) < MIN_CHINESE_CONTENT_LENGTH) {
                return new CrawlerPageResult(url, title, null, false, "正文内容过少或疑似列表页");
            }

            KnowledgeDoc similarDoc = findHighlySimilarDoc(title, content);
            if (similarDoc != null) {
                return new CrawlerPageResult(url, title, similarDoc.id(), false, "标题和正文与已有知识资料高度相似，已跳过");
            }

            Long id = knowledgeRepository.save(
                    StringUtils.hasText(title) ? title : url,
                    content,
                    DEFAULT_CATEGORY,
                    url,
                    SOURCE_TYPE
            );
            if (id == null) {
                return new CrawlerPageResult(url, title, null, false, "知识资料入库失败");
            }
            return new CrawlerPageResult(url, title, id, true, "采集并入库成功");
        } catch (IllegalArgumentException ex) {
            return new CrawlerPageResult(url, null, null, false, "URL 格式错误");
        } catch (IOException ex) {
            return new CrawlerPageResult(url, null, null, false, "访问失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CrawlerPageResult(url, null, null, false, "采集被中断");
        }
    }

    private List<String> discoverTargetUrls(String seedUrl) {
        if (!isAllowedPublicUrl(seedUrl)) {
            return List.of(seedUrl);
        }

        try {
            FetchedPage seedPage = fetchPage(seedUrl);
            List<String> detailUrls = extractDetailLinks(seedUrl, seedPage.html());
            if (!detailUrls.isEmpty()) {
                return detailUrls;
            }
            return List.of(seedUrl);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of(seedUrl);
        }
    }

    private List<String> extractDetailLinks(String seedUrl, String html) {
        Set<String> seen = new LinkedHashSet<>();
        List<LinkCandidate> candidates = new ArrayList<>();
        URI baseUri = URI.create(seedUrl);
        Matcher matcher = LINK_PATTERN.matcher(html);

        while (matcher.find()) {
            String rawHref = matcher.group(2);
            if (!StringUtils.hasText(rawHref)) {
                continue;
            }

            String absoluteUrl = normalizeDiscoveredUrl(baseUri, rawHref);
            if (!StringUtils.hasText(absoluteUrl) || !seen.add(absoluteUrl)) {
                continue;
            }
            if (!isAllowedDetailUrl(seedUrl, absoluteUrl)) {
                continue;
            }

            String linkText = cleanText(stripTags(matcher.group(3)));
            int score = scoreDetailLink(absoluteUrl, linkText);
            if (score <= 0) {
                continue;
            }
            candidates.add(new LinkCandidate(absoluteUrl, score));
        }

        candidates.sort((left, right) -> Integer.compare(right.score(), left.score()));
        return candidates.stream()
                .limit(MAX_DETAIL_LINKS_PER_SEED)
                .map(LinkCandidate::url)
                .toList();
    }

    private String normalizeDiscoveredUrl(URI baseUri, String rawHref) {
        String href = rawHref.trim();
        String lower = href.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:")
                || lower.startsWith("#")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:")) {
            return "";
        }
        try {
            URI resolved = baseUri.resolve(href.split("#", 2)[0]).normalize();
            String scheme = resolved.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return "";
            }
            return resolved.toString();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private boolean isAllowedDetailUrl(String seedUrl, String targetUrl) {
        if (!isAllowedPublicUrl(targetUrl) || isFileUrl(targetUrl)) {
            return false;
        }
        if (!isSameSchoolDomain(seedUrl, targetUrl)) {
            return false;
        }
        String normalized = targetUrl.toLowerCase(Locale.ROOT);
        if (normalized.contains("search")
                || normalized.contains("list")
                || normalized.contains("index")
                || normalized.endsWith("/")
                || normalized.contains("category")
                || normalized.contains("column")
                || normalized.contains("node")) {
            return normalized.contains("page.htm") || normalized.contains("/2026/") || normalized.contains("/2025/");
        }
        return true;
    }

    private int scoreDetailLink(String url, String linkText) {
        String normalizedUrl = url.toLowerCase(Locale.ROOT);
        int score = 0;
        if (normalizedUrl.contains("page.htm")) {
            score += 100;
        }
        if (normalizedUrl.contains("/2026/")) {
            score += 80;
        }
        if (normalizedUrl.contains("/2025/")) {
            score += 70;
        }
        if (normalizedUrl.matches(".*/\\d{4}/\\d{4}/.*")) {
            score += 40;
        }
        if (StringUtils.hasText(linkText) && countChineseCharacters(linkText) >= 6) {
            score += 20;
        }
        if (isLikelyListPage(url)) {
            score -= 80;
        }
        return score;
    }

    private boolean isSameSchoolDomain(String seedUrl, String targetUrl) {
        try {
            String seedHost = URI.create(seedUrl).getHost();
            String targetHost = URI.create(targetUrl).getHost();
            if (!StringUtils.hasText(seedHost) || !StringUtils.hasText(targetHost)) {
                return false;
            }
            seedHost = seedHost.toLowerCase(Locale.ROOT);
            targetHost = targetHost.toLowerCase(Locale.ROOT);
            if (seedHost.equals(targetHost)) {
                return true;
            }
            if (seedHost.endsWith("scut.edu.cn") && targetHost.endsWith("scut.edu.cn")) {
                return true;
            }
            String seedWithoutWww = seedHost.startsWith("www.") ? seedHost.substring(4) : seedHost;
            String targetWithoutWww = targetHost.startsWith("www.") ? targetHost.substring(4) : targetHost;
            return seedWithoutWww.equals(targetWithoutWww);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isFileUrl(String url) {
        return FILE_LINK_PATTERN.matcher(url.toLowerCase(Locale.ROOT)).matches();
    }

    private boolean isLikelyListPage(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        return normalized.endsWith("/")
                || normalized.contains("list")
                || normalized.contains("index")
                || normalized.contains("search")
                || normalized.contains("category")
                || normalized.contains("column");
    }

    private KnowledgeDoc findHighlySimilarDoc(String title, String content) {
        String normalizedTitle = normalizeComparableText(title);
        String normalizedContent = normalizeComparableText(content);
        if (!StringUtils.hasText(normalizedTitle) || normalizedContent.length() < 120) {
            return null;
        }

        String contentSample = normalizedContent.substring(0, Math.min(normalizedContent.length(), 300));
        for (KnowledgeDoc doc : knowledgeRepository.findAll(null, null)) {
            String existingTitle = normalizeComparableText(doc.title());
            String existingContent = normalizeComparableText(doc.content());
            if (existingContent.length() < 120) {
                continue;
            }
            String existingSample = existingContent.substring(0, Math.min(existingContent.length(), 300));
            if (normalizedTitle.equals(existingTitle)
                    && (existingContent.contains(contentSample) || normalizedContent.contains(existingSample))) {
                return doc;
            }
        }
        return null;
    }

    private String normalizeComparableText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private FetchedPage fetchPage(String url) throws IOException, InterruptedException {
        try {
            return fetchPageDirect(url);
        } catch (IOException ex) {
            if (url.startsWith("https://")) {
                String fallbackUrl = "http://" + url.substring("https://".length());
                return fetchPageDirect(fallbackUrl);
            }
            throw ex;
        }
    }

    private FetchedPage fetchPageDirect(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .header("User-Agent", "campus-qa-course-crawler/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP 状态码：" + response.statusCode());
        }
        return new FetchedPage(url, decodeBody(response));
    }

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

    private boolean isAllowedPublicUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String normalized = url.toLowerCase(Locale.ROOT);
        return (normalized.startsWith("https://") || normalized.startsWith("http://"))
                && !containsForbiddenUrlKeyword(normalized)
                && !isFileUrl(normalized);
    }

    private boolean containsForbiddenUrlKeyword(String normalizedUrl) {
        return normalizedUrl.contains("login")
                || normalizedUrl.contains("auth")
                || normalizedUrl.contains("sso")
                || normalizedUrl.contains("jw")
                || normalizedUrl.contains("cas")
                || normalizedUrl.contains("captcha")
                || normalizedUrl.contains("verify")
                || normalizedUrl.contains("password")
                || normalizedUrl.contains("personal")
                || normalizedUrl.contains("profile")
                || normalizedUrl.contains("passport")
                || normalizedUrl.contains("教务")
                || normalizedUrl.contains("登录")
                || normalizedUrl.contains("认证")
                || normalizedUrl.contains("验证码")
                || normalizedUrl.contains("个人信息");
    }

    private String decodeBody(HttpResponse<byte[]> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        Charset charset = detectCharset(contentType);
        return new String(response.body(), charset);
    }

    private Charset detectCharset(String contentType) {
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (RuntimeException ignored) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (!matcher.find()) {
            return "";
        }
        return cleanText(stripTags(matcher.group(1)));
    }

    private String extractContent(String html, String title) {
        String withoutNoise = removeNoiseBlocks(html);
        List<String> candidates = new ArrayList<>();
        collectMatches(ARTICLE_MAIN_PATTERN, withoutNoise, candidates);
        collectMatches(CONTENT_CONTAINER_PATTERN, withoutNoise, candidates);
        candidates.add(extractBody(withoutNoise));

        return candidates.stream()
                .map(this::cleanHtmlText)
                .map(content -> trimToArticleContent(content, title))
                .filter(StringUtils::hasText)
                .max((left, right) -> Integer.compare(scoreContent(left), scoreContent(right)))
                .orElse("");
    }

    private String extractBody(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("<body");
        if (start < 0) {
            return html;
        }
        int bodyStart = lower.indexOf(">", start);
        int bodyEnd = lower.lastIndexOf("</body>");
        if (bodyStart < 0 || bodyEnd <= bodyStart) {
            return html;
        }
        return html.substring(bodyStart + 1, bodyEnd);
    }

    private String stripTags(String value) {
        String normalized = DATA_FOCUS_URL_PATTERN.matcher(value).replaceAll(" ");
        normalized = ATTRIBUTE_RESIDUE_PATTERN.matcher(normalized).replaceAll(" ");
        String withoutTags = TAG_PATTERN.matcher(normalized).replaceAll(" ");
        return withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#160;", " ");
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
    }

    private String removeNoiseBlocks(String html) {
        return NOISE_BLOCK_PATTERN.matcher(html).replaceAll(" ");
    }

    private void collectMatches(Pattern pattern, String html, List<String> candidates) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            candidates.add(matcher.group(2));
        }
    }

    private String cleanHtmlText(String html) {
        String text = stripTags(html);
        text = DATA_FOCUS_URL_PATTERN.matcher(text).replaceAll(" ");
        text = ATTRIBUTE_RESIDUE_PATTERN.matcher(text).replaceAll(" ");
        text = SCRIPT_RESIDUE_PATTERN.matcher(text).replaceAll(" ");
        text = removeCommonNavigationText(text);
        return cleanText(text);
    }

    private String removeCommonNavigationText(String value) {
        String text = value;
        text = text.replaceAll("(校报\\s*)?(微信\\s*)?(微博\\s*)?(华工主页\\s*)?导航", " ");
        text = text.replaceAll("(首页\\s+){2,}", " ");
        text = text.replaceAll("(查看更多\\s*){2,}", " ");
        text = text.replaceAll("(上一页\\s*|下一页\\s*|返回顶部\\s*)+", " ");
        text = text.replaceAll("分享到\\s*A\\+\\s*A-\\s*夜晚模式", " ");
        text = text.replaceAll("相关文章\\s*返回\\s*原图\\s*/?", " ");
        return text;
    }

    private String trimToArticleContent(String content, String title) {
        String text = content;
        if (StringUtils.hasText(title)) {
            int titleIndex = text.indexOf(title);
            if (titleIndex >= 0) {
                text = text.substring(titleIndex);
            }
        }

        int locationIndex = text.indexOf("当前位置：");
        if (locationIndex >= 0 && locationIndex < 300) {
            text = text.substring(locationIndex);
        }

        text = text.replaceAll("^当前位置：\\s*[^\\n。！？]{0,120}", " ");
        text = text.replaceAll("时间：\\s*\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s*供稿单位：[^\\s]{1,30}\\s*浏览量：\\s*\\d+", " ");
        text = text.replaceAll("分享到\\s*A\\+\\s*A-\\s*夜晚模式", " ");
        text = text.replaceAll("相关文章\\s*返回\\s*原图\\s*/?", " ");
        return cleanText(text);
    }

    private int scoreContent(String content) {
        int chineseCount = countChineseCharacters(content);
        int score = Math.min(content.length(), 3000) + chineseCount * 3;
        if (content.contains("版权所有") || content.contains("ICP备")) {
            score -= 300;
        }
        if (content.contains("data-focus-url") || content.contains("function")) {
            score -= 500;
        }
        return score;
    }

    private int countChineseCharacters(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                count++;
            }
        }
        return count;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private record FetchedPage(String url, String html) {
    }

    private record LinkCandidate(String url, int score) {
    }
}
