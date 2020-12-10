package com.github.sgz886;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.sql.SQLException;
import java.util.stream.Collectors;

public class Crawler {
    private static int processedNumberSession = 0;

    private CrawlerDao dao = new JdbcCrawlerDao("jdbc:h2:file:" + System.getProperty("user.dir") + "/news", "root",
            "root");

    @SuppressFBWarnings("DMI_CONSTANT_DB_PASSWORD")
    public Crawler() {
    }

    public void run() throws SQLException {

        String link;
        // 从待处理数据库中加载下一个链接,如果存在则删除
        while ((link = dao.getNextLinkThenDelete()) != null && processedNumberSession <= 300) {
            if (dao.isInDB("select LINK from LINKS_ALREADY_PROCESSED where LINK= ?", link)) {
                continue;
            }

            Document doc = httpGetandParseHtml(link);
            if (doc == null) {
                continue;
            }
            parseUrlsFromPageAndStoreToDo(doc);
            processNews(doc, link);

            dao.insertOrDeleteFromDB("insert into LINKS_ALREADY_PROCESSED  (LINK) VALUES ( ? )", link);
        }

        System.out.println("待处理 = 暂没做"); // TODO: 2020/12/9
        System.out.println("本次已处理 = " + processedNumberSession);
    }

    public static void main(String[] args) throws SQLException {
        new Crawler().run();
    }

    // 1.将所有发现的 新链接 存到待处理数据库
    private void parseUrlsFromPageAndStoreToDo(Document doc) throws SQLException {
        for (Element tag : doc.select("a")) {
            String href = tag.attr("href").trim();
            if (isInterestingLink(href)) {
                if (dao.isInDB("select LINK from LINKS_ALREADY_PROCESSED where LINK= ?", href) ||
                            dao.isInDB("select LINK from LINKS_TO_BE_PROCESSED where LINK= ?", href)) {
                    continue;
                }
                if (href.startsWith("//")) {
                    href = "https:" + href;
                }
                dao.insertOrDeleteFromDB("insert into LINKS_TO_BE_PROCESSED (LINK) values ( ? )", href);
                System.out.println("    1.添加:" + href + "进入待处理");
            }
        }
    }


    private static Document httpGetandParseHtml(String link) {
        // 这是我们感兴趣的内容(sina.cn)
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet;
        try {
            httpGet = new HttpGet(link);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
        return isNewsPage(s) && isNotLoginPage(s) && isNoCallback(s) && isNoPhoto(s) && isNoVideo(s) && isNoKan(s);
        // return (isNewsPage(s) || isIndexPage(s)) && isNotLoginPage(s);
    }

    private static boolean isNoKan(String s) {
        return !s.contains("k.sina.cn");
    }

    private static boolean isNoVideo(String s) {
        return !s.contains("video.sina.cn");
    }

    private static boolean isNoPhoto(String s) {
        return !s.contains("photo.sina.cn");
    }

    private static boolean isNoCallback(String s) {
        return !s.contains("callback") && !s.contains("jump");
    }

    private static boolean isIndexPage(String s) {
        return "https://sina.cn".equals(s);
    }

    private static boolean isNewsPage(String s) {
        return s.contains("sina.cn");
    }

    private static boolean isNotLoginPage(String s) {
        return !s.contains("passport.sina.cn");
    }

    // 2.处理当前新闻页面
    private void processNews(Document doc, String link) {
        Elements articleTags = doc.select("article");
        for (Element articleTag : articleTags) {
            try {
                String title = articleTag.child(0).text();
                String content = doc.select("p").stream().map(Element::text).collect(Collectors.joining("\n"));
                dao.insertNewsIntoDB(link, title, content);
                processedNumberSession++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
