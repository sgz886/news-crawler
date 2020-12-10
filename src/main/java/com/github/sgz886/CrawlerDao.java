package com.github.sgz886;

import java.sql.SQLException;

public interface CrawlerDao {
    String getNextLinkFromDB(String sql) throws SQLException;

    String getNextLinkThenDelete() throws SQLException;

    void insertOrDeleteFromDB(String sql, String href) throws SQLException;

    boolean isInDB(String sql, String link) throws SQLException;

    void insertNewsIntoDB(String URL, String title, String content);
}
