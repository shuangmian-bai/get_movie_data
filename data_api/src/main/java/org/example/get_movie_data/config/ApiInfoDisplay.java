package org.example.get_movie_data.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

@Component
public class ApiInfoDisplay {
    
    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;
    
    /**
     * 应用启动完成后显示API接口信息
     */
    @EventListener(ApplicationReadyEvent.class)
    public void displayApiInfo() {
        System.out.println("======= API 接口信息 =======");
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo requestMappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            
            System.out.println("路径: " + requestMappingInfo.getPatternsCondition());
            System.out.println("方法: " + requestMappingInfo.getMethodsCondition());
            System.out.println("处理类: " + handlerMethod.getBeanType().getSimpleName());
            System.out.println("处理方法: " + handlerMethod.getMethod().getName());
            System.out.println("------------------------");
        }
        System.out.println("==========================");
        
        System.out.println("API文档地址:");
        System.out.println("Swagger UI: http://localhost:8080/swagger-ui.html");
        System.out.println("OpenAPI JSON: http://localhost:8080/v3/api-docs");
    }
}