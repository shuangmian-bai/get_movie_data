package org.example.get_movie_data.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 统一的HTML解析工具类
 * 
 * 封装Jsoup库，提供统一的HTML解析方法
 */
public class HtmlParserUtil {
    
    /**
     * 解析HTML字符串为Document对象
     * 
     * @param html HTML字符串
     * @return Document对象
     */
    public static Document parse(String html) {
        return Jsoup.parse(html);
    }
    
    /**
     * 选择元素
     * 
     * @param doc Document对象
     * @param cssSelector CSS选择器
     * @return 匹配的元素集合
     */
    public static Elements select(Document doc, String cssSelector) {
        return doc.select(cssSelector);
    }
    
    /**
     * 选择元素
     * 
     * @param element Element对象
     * @param cssSelector CSS选择器
     * @return 匹配的元素集合
     */
    public static Elements select(Element element, String cssSelector) {
        return element.select(cssSelector);
    }
    
    /**
     * 选择元素
     * 
     * @param html HTML字符串
     * @param cssSelector CSS选择器
     * @return 匹配的元素集合
     */
    public static Elements select(String html, String cssSelector) {
        return Jsoup.parse(html).select(cssSelector);
    }
}