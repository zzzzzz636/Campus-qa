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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.CrawlerPageResult;
import com.campusqa.dto.CrawlerRunResult;
import com.campusqa.repository.KnowledgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CrawlerService {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 3;
    private static final String DEFAULT_CATEGORY = "校园资料";
    private static final String SOURCE_TYPE = "官网爬虫";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("<(script|style|noscript|svg|canvas)[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([\\w\\-]+)", Pattern.CASE_INSENSITIVE);

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

        for (String url : urls.stream().limit(runLimit).toList()) {
            CrawlerPageResult item = crawlOne(url);
            items.add(item);
            if (item.success()) {
                successCount++;
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
            if (knowledgeRepository.existsBySourceUrl(url)) {
                return new CrawlerPageResult(url, null, null, false, "该 URL 已存在于知识资料库");
            }

            FetchedPage page = fetchPage(url);
            String html = page.html();
            String title = extractTitle(html);
            String content = extractContent(html);
            if (!StringUtils.hasText(content)) {
                if (!StringUtils.hasText(title)) {
                    return new CrawlerPageResult(url, title, null, false, "页面正文为空");
                }
                content = "官网页面标题：" + title + "。来源页面：" + url;
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
                && !normalized.contains("login")
                && !normalized.contains("auth")
                && !normalized.contains("sso")
                && !normalized.contains("jw");
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

    private String extractContent(String html) {
        String withoutNoise = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll(" ");
        String body = extractBody(withoutNoise);
        return cleanText(stripTags(body));
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
        String withoutTags = TAG_PATTERN.matcher(value).replaceAll(" ");
        return withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private record FetchedPage(String url, String html) {
    }
}
