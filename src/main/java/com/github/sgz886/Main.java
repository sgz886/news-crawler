package com.github.sgz886;

public class Main {
    public static void main(String[] args) {
        CrawlerDao dao = new MyBatisCrawlerDao();
        for (int i = 0; i < 8; i++) {
            new Crawler(dao, "线程" + (i + 1)).start();
        }
    }
}
