package com.example.yuny;

import java.util.*;

public class OuterArrayParser {

    public static List<String> parseOuterArray(String input) {
        List<String> result = new ArrayList<>();
        if (input == null || input.length() <= 2) return result;

        // 去掉最外层的 [ 和 ]
        String inner = input.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        inner = inner.trim();

        int start = 0;
        int level = 0; // 嵌套层级：括号和方括号统一处理
        boolean inString = false; // 是否在字符串中
        char stringDelim = '"'; // 字符串分隔符（支持 " 和 '，但这里主要是 "）

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);

            // 处理字符串边界
            if (c == '"' || c == '\'') {
                if (!inString) {
                    inString = true;
                    stringDelim = c;
                } else if (inner.charAt(i - 1) != '\\' && stringDelim == c) { // 非转义的引号
                    inString = false;
                }
            }

            // 在字符串中时，跳过括号和逗号的解析
            if (inString) {
                continue;
            }

            if (c == '[' || c == '{') {
                level++;
            } else if (c == ']' || c == '}') {
                level--;
            } else if (c == ',' && level == 0) {
                // 只有在最外层的逗号才分割
                String item = inner.substring(start, i).trim();
                result.add(item);
                start = i + 1;
            }
        }

        // 添加最后一项
        if (start < inner.length()) {
            String item = inner.substring(start).trim();
            result.add(item);
        }

        return result;
    }
}