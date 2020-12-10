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
import java.util.stream.Collectors;

public class Crawler {
    private static int processedNumberSession = 0;
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";

    @SuppressFBWarnings("DMI_CONSTANT_DB_PASSWORD")
    public static void main(String[] args) throws SQLException {
        String jdbcUrl = "jdbc:h2:file:" + System.getProperty("user.dir") + "/news";
        Connection connection = DriverManager.getConnection(jdbcUrl, USER_NAME, PASSWORD);

        String link;
        // 从待处理数据库中加载下一个链接,如果存在则删除
        while ((link = getNextLinkThenDelete(connection)) != null && processedNumberSession <= 3000) {
            if (isInDB(connection, "select LINK from LINKS_ALREADY_PROCESSED where LINK= ?", link)) {
                continue;
            }

            Document doc = httpGetandParseHtml(link);
            if (doc == null) {
                continue;
            }
            parseUrlsFromPageAndStoreToDo(connection, doc);
            processNews(connection, doc, link);

            insertOrDeleteFromDB(connection, "insert into LINKS_ALREADY_PROCESSED  (LINK) VALUES ( ? )", link);
        }

        System.out.println("待处理 = 暂没做"); // TODO: 2020/12/9
        System.out.println("本次已处理 = " + processedNumberSession);
        connection.close();
    }

    // 1.将所有发现的 新链接 存到待处理数据库
    private static void parseUrlsFromPageAndStoreToDo(Connection connection, Document doc) throws SQLException {
        for (Element tag : doc.select("a")) {
            String href = tag.attr("href").trim();
            if (isInterestingLink(href)) {
                if (isInDB(connection, "select LINK from LINKS_ALREADY_PROCESSED where LINK= ?", href) ||
                            isInDB(connection, "select LINK from LINKS_TO_BE_PROCESSED where LINK= ?", href)) {
                    continue;
                }
                if (href.startsWith("//")) {
                    href = "https:" + href;
                }
                insertOrDeleteFromDB(connection, "insert into LINKS_TO_BE_PROCESSED (LINK) values ( ? )", href);
                System.out.println("    1.添加:" + href + "进入待处理");
            }
        }
    }

    // 3.收尾 从待处理数据库删除(可以提前做,跟listPool一起,但是如果异常关闭会丢失一条信息);添加到已处理数据库
    private static void insertOrDeleteFromDB(Connection connection, String sql, String href) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, href);
            statement.execute();
        }
    }

    private static boolean isInDB(Connection connection, String sql, String link) throws SQLException {
        ResultSet resultSet = null;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link);
            resultSet = statement.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    private static String getNextLinkThenDelete(Connection connection) throws SQLException {
        String link = getALinkFromDB(connection, "select LINK from LINKS_TO_BE_PROCESSED LIMIT 1");
        if (link != null) {
            insertOrDeleteFromDB(connection, "delete from LINKS_TO_BE_PROCESSED where LINK = ?", link);
        }
        return link;
    }

    private static String getALinkFromDB(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        }
        return null;
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
        return !s.contains("callback");
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
    private static void processNews(Connection connection, Document doc, String link) {
        Elements articleTags = doc.select("article");
        for (Element articleTag : articleTags) {
            String title = articleTag.child(0).text();
            String content = doc.select("p").stream().map(Element::text).collect(Collectors.joining("\n"));
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into news (URL,title,content,created_at, modified_at) values(?,?,?,now(),now())")) {
                statement.setString(1, link);
                statement.setString(2, title);
                statement.setString(3, content);
                statement.execute();
                processedNumberSession++;
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

}
