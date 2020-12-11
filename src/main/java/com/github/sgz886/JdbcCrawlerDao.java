package com.github.sgz886;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcCrawlerDao  {
    private final Connection connection;

    public JdbcCrawlerDao(String jdbcUrl, String userName, String password) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl, userName, password);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String getNextLinkFromDB(String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        }
        return null;
    }

    public String getNextLinkThenDelete() throws SQLException {
        String link = getNextLinkFromDB("select LINK from LINKS_TO_BE_PROCESSED LIMIT 1");
        if (link != null) {
            insertOrDeleteFromDB("delete from LINKS_TO_BE_PROCESSED where LINK = ?", link);
        }
        return link;
    }

    public void insertOrDeleteFromDB(String sql, String href) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, href);
            statement.execute();
        }
    }

    public void insertNewsIntoDB(String URL, String title, String content) {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into news (URL,title,content,created_at, modified_at) values(?,?,?,now(),now())")) {
            statement.setString(1, URL);
            statement.setString(2, title);
            statement.setString(3, content);
            statement.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public boolean isInTodoTable(String sql, String link) throws SQLException {
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
}
