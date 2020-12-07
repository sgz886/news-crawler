package com.github.sgz886;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Crawler {
    public static void main(String[] args) {
        String crawler_URL = "https://sina.cn";
        // 待处理的链接池
        List<String> linkPool = Stream.of(crawler_URL).collect(Collectors.toCollection(ArrayList::new));
        // 已处理的链接池
        Set<String> processedLinks = new HashSet<>();
        int times = 0;
        while (times < 200) {
            times++;
            if (linkPool.isEmpty()) {
                break;
            }
            String link = linkPool.remove(0);
            if (processedLinks.contains(link)) {
                continue;
            }
            if (!link.contains("sina.cn")) {
                // 这是我们不感兴趣的,不处理
                continue;
            } else {
                Document doc = httpGetandParseHtml(link);
                // 1.将所有发现的sina.cn链接存到链接池
                doc.select("a").stream().map(tag -> tag.attr("href"))
                        .forEach(href -> addUrlsToPool(href, linkPool, processedLinks));
                // 2.将article文章视为新闻页面, 存入数据库,否则do nothing
                storeIntoDatabaseForNewPage(doc);
                // 4.收尾
                processedLinks.add(link);
            }
        }
        System.out.println("已处理 = " + processedLinks.size());
        System.out.println("待处理 = " + linkPool.size());
    }

    private static void addUrlsToPool(String href, List<String> linkPool, Set<String> processedLinks) {
        if (isInterestingLink(href)) {
            if (href.startsWith("//")) {
                href = "https:" + href;
                System.out.println("        插入https前缀");
            }
            if (!processedLinks.contains(href)) {
                linkPool.add(href);
                System.out.println("    1.添加:" + href + "进入linkPool");
            }
        }
    }

    private static void storeIntoDatabaseForNewPage(Document doc) {
        // 2.将article文章视为新闻页面, 存入数据库,否则do nothing
        Elements articleTags = doc.select("article");
        for (Element articleTag : articleTags) {
            String text = articleTag.text();
            // TODO: 2020/12/7 放入数据库
            System.out.println("    2. TODO  标题:" + text.substring(0, Math.min(text.length(), 30)));
        }
    }

    private static Document httpGetandParseHtml(String link) {
        // 这是我们感兴趣的内容(sina.cn)
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(link);
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, "
                                                + "like Gecko) Chrome/86.0.4240.198 Safari/537.36");
        System.out.println("当前页面 " + link);
        try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
            // System.out.println(response1.getStatusLine());
            HttpEntity entity1 = response1.getEntity();
            String html = EntityUtils.toString(entity1);
            return Jsoup.parse(html);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean isInterestingLink(String s) {
        return (isNewsPage(s) || isIndexPage(s)) && isNotLoginPage(s);
    }

    private static boolean isIndexPage(String s) {
        return "https://sina.cn".equals(s);
    }

    private static boolean isNewsPage(String s) {
        return s.contains("news.sina.cn");
    }

    private static boolean isNotLoginPage(String s) {
        return !s.contains("passport.sina.cn");
    }
}
