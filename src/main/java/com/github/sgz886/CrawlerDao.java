package com.github.sgz886;

import java.sql.SQLException;

public interface CrawlerDao {

    String getNextLinkThenDelete() throws SQLException;

    boolean isInTodoTable(String link) throws SQLException;

    boolean isInFinishedTable(String link) throws SQLException;

    void insertNewsIntoDB(String URL, String title, String content);

    void insertToTodoTable(String href);

    void insertToFinishedTable(String link);
}
