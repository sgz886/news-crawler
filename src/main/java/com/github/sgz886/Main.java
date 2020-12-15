package com.github.sgz886;

public class Main {
    public static void main(String[] args) {
        CrawlerDao dao = new MyBatisCrawlerDao();
        int targetNumber = 600;
        for (int i = 0; i < 8; i++) {
            new Crawler(dao, targetNumber, "线程" + (i + 1)).start();
        }
    }
}
