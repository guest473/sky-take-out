package com.sky.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

//Http工具类
@Slf4j
public class HttpClientUtil {

    private static final int HTTP_OK = 200;

    //发送GET方式请求
    public static String doGet(String url, Map<String, String> paramMap) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            URIBuilder builder = new URIBuilder(url);
            if (paramMap != null) {
                for (String key : paramMap.keySet()) {
                    builder.addParameter(key, paramMap.get(key));
                }
            }
            URI uri = builder.build();
            return execute(httpClient, new HttpGet(uri), url);
        } catch (Exception e) {
            //不吞异常：调用方需要知道请求本身失败了，而不是拿到空串后继续解析、在别处抛无意义的NPE
            throw new IllegalStateException("请求失败：" + url, e);
        } finally {
            closeQuietly(httpClient);
        }
    }

    //执行请求并读取响应体
    private static String execute(CloseableHttpClient httpClient, HttpRequestBase request, String url)
            throws IOException {
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(request);
            String body = response.getEntity() == null
                    ? ""
                    : EntityUtils.toString(response.getEntity(), "UTF-8");
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HTTP_OK) {
                log.warn("请求{}返回状态码{}，响应体：{}", url, statusCode, body);
                return "";
            }
            return body;
        } finally {
            //response 可能为 null（请求本身就抛了异常），必须判空：
            //否则 finally 里的 NPE 会顶掉原始异常，真实失败原因就丢了
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    log.warn("关闭响应失败：{}", url, e);
                }
            }
        }
    }

    //关闭HttpClient，避免连接资源泄漏
    private static void closeQuietly(CloseableHttpClient httpClient) {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.warn("关闭HttpClient失败", e);
        }
    }

}
