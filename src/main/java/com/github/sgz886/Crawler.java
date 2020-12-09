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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Crawler {
    private static int processedNumberSession = 0;
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";

    @SuppressFBWarnings("DMI_CONSTANT_DB_PASSWORD")
    public static void main(String[] args) throws SQLException {
        String jdbcUrl = "jdbc:h2:file:" + System.getProperty("user.dir") + "/news";
        Connection connection = DriverManager.getConnection(jdbcUrl, USER_NAME, PASSWORD);
        // 待处理的链接池
        List<String> linkPool = loadUrlsFromDatabase(connection, "select LINK from LINKS_TO_BE_PROCESSED");

        int times = 0;
        while (times < 1000) {
            times++;
            if (linkPool.isEmpty()) {
                break;
            }
            // 从待处理池捞1个处理
            String link = linkPool.remove(0);
            insertOrDeleteFromDatabase(connection, "delete from LINKS_TO_BE_PROCESSED where LINK = ?", link);

            if (isAlreadyProcessed(connection, link)) {
                continue;
            }

            Document doc = httpGetandParseHtml(link);

            parseUrlsFromPageAndStoreToDo(connection, linkPool, doc);
            // TODO: 2020/12/8
            processNews(doc);

            insertOrDeleteFromDatabase(connection, "insert into LINKS_ALREADY_PROCESSED  (LINK) VALUES ( ? )", link);
        }

        System.out.println("待处理 = " + linkPool.size());
        System.out.println("本次已处理 = " + processedNumberSession);
        connection.close();
    }

    // 1.将所有发现的 新链接 存到待处理数据库
    private static void parseUrlsFromPageAndStoreToDo(Connection connection, List<String> linkPool, Document doc) throws SQLException {
        for (Element tag : doc.select("a")) {
            String href = tag.attr("href");
            if (isInterestingLink(href)) {
                if (isAlreadyProcessed(connection, href)) {
                    continue;
                }
                if (href.startsWith("//")) {
                    href = "https:" + href;
                }
                linkPool.add(href);
                insertOrDeleteFromDatabase(connection, "insert into LINKS_TO_BE_PROCESSED (LINK) values ( ? )", href);
                System.out.println("    1.添加:" + href + "进入 待处理");
            }
        }
    }

    // 3.收尾 从待处理数据库删除(可以提前做,跟listPool一起,但是如果异常关闭会丢失一条信息);添加到已处理数据库
    private static void insertOrDeleteFromDatabase(Connection connection, String sql, String href) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, href);
            statement.execute();
        }
    }

    private static boolean isAlreadyProcessed(Connection connection, String link) throws SQLException {
        ResultSet resultSet = null;
        try (PreparedStatement statement = connection.prepareStatement(
                "select LINK from LINKS_ALREADY_PROCESSED where LINK= ?")) {
            statement.setString(1, link);
            resultSet = statement.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (resultSet!=null) {
                resultSet.close();
            }
        }
    }

    private static List<String> loadUrlsFromDatabase(Connection connection, String sql) throws SQLException {
        List<String> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery();) {
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
        }
        return results;
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

    // 2.处理当前新闻页面
    private static void processNews(Document doc) {
        Elements articleTags = doc.select("article");
        for (Element articleTag : articleTags) {
            String text = articleTag.text();
            // TODO: 2020/12/7 放入数据库
            System.out.println("    2. 标题:" + text.substring(0, Math.min(text.length(), 30)));
        }
        processedNumberSession++;
    }

    // private static void addUrlsToPool(Element tag, List<String> linkPool, Set<String> processedLinks) {
    //     String href = tag.attr("href");
    //     if (isInterestingLink(href)) {
    //         if (href.startsWith("//")) {
    //             href = "https:" + href;
    //             System.out.println("        插入https前缀");
    //         }
    //         if (!processedLinks.contains(href)) {
    //             linkPool.add(href);
    //             System.out.println("    1.添加:" + href + "进入 待处理");
    //         }
    //     }
    // }
}
