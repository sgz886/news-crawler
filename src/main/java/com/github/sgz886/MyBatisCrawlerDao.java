package com.github.sgz886;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MyBatisCrawlerDao implements CrawlerDao {
    SqlSessionFactory sqlSessionFactory;

    public MyBatisCrawlerDao() {
        try {
            String resource = "db/mybatis/config.xml";
            InputStream inputStream = Resources.getResourceAsStream(resource);
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized String getNextLinkThenDelete() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            String url = session.selectOne("com.github.sgz886.MyMapper.selectNextAvailableLink");
            if (url != null) {
                session.delete("com.github.sgz886.MyMapper.deleteLink", url);
            }
            return url;
        }
    }

    @Override
    public boolean isInFinishedTable(String link) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int count = session.selectOne("com.github.sgz886.MyMapper.checkLinkinFinishedTable", link);
            return count != 0;
        }
    }

    @Override
    public void insertToTodoTable(String link) {
        Map<String, Object> param = new HashMap<>();
        param.put("tableName", "news.links_to_be_processed");
        param.put("link", link);
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.insert("com.github.sgz886.MyMapper.insertLink", param);
        }
    }

    @Override
    public void insertToFinishedTable(String link) {
        Map<String, Object> param = new HashMap<>();
        param.put("tableName", "news.links_already_processed");
        param.put("link", link);
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.insert("com.github.sgz886.MyMapper.insertLink", param);
        }
    }

    @Override
    public boolean isInTodoTable(String link) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int count = session.selectOne("com.github.sgz886.MyMapper.checkLinkinTodoTable", link);
            return count != 0;
        }
    }

    @Override
    public void insertNewsIntoDB(News news) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.insert("com.github.sgz886.MyMapper.insertNews", news);
        }
    }
}
