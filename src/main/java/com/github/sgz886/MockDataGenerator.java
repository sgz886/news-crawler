package com.github.sgz886;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Random;

public class MockDataGenerator {
    public static void main(String[] args) {
        SqlSessionFactory sqlSessionFactory;
        try {
            String resource = "db/mybatis/config.xml";
            InputStream inputStream = Resources.getResourceAsStream(resource);
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mockData(sqlSessionFactory, 1000000);
    }

    private static void mockData(SqlSessionFactory sqlSessionFactory, int howMany) {
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            // List<News> seedNews = session.selectList("com.github.sgz886.MockMapper.selectNews");
            List<News> seedNews = session.selectList("com.github.sgz886.MockMapper.selectNews", 2000);
            Random random = new Random();

            int count = howMany - seedNews.size();
            try {
                while (count-- > 0) {
                    int index = random.nextInt(seedNews.size());
                    News randomNews = new News();
                    BeanUtils.copyProperties(randomNews, seedNews.get(index));
                    Instant fakeInstant = randomNews.getCreatedAt();
                    fakeInstant = fakeInstant.minusSeconds(3600L * 24 * random.nextInt(20 * 365));
                    randomNews.setCreatedAt(fakeInstant);
                    randomNews.setModifiedAt(fakeInstant);
                    session.insert("com.github.sgz886.MockMapper.insertNews", randomNews);

                    if (count % 2000 == 0) {
                        System.out.println("Left: " + count);
                        session.flushStatements();
                        session.commit();
                    }
                }
                session.commit();
            } catch (Exception e) {
                session.rollback();
                throw new RuntimeException(e);
            }
        }
    }
}
