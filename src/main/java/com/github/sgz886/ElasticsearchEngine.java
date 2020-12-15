package com.github.sgz886;

import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class ElasticsearchEngine {
    public static void main(String[] args) {
        while (true) {
            try {
                System.out.println("请输入关键字:");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
                String keyword = reader.readLine();

                search(keyword);


            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void search(String keyword) {
        try (RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(new HttpHost("localhost", 9200, "http")))) {
            SearchRequest request = new SearchRequest("news");
            request.source(new SearchSourceBuilder().query(new MultiMatchQueryBuilder(keyword, "title", "content")));
            SearchResponse result = client.search(request, RequestOptions.DEFAULT);
            result.getHits().forEach(hit -> System.out.println(hit.getSourceAsString()));


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
