package org.example.get_movie_data.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.get_movie_data.service.CastService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 投屏控制器
 * 
 * 提供投屏相关API接口，包括扫描局域网内可投屏设备等功能
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/cast")
@Tag(name = "投屏接口", description = "提供投屏设备扫描等功能")
public class CastController {
    
    @Autowired
    private CastService castService;
    
    /**
     * 扫描局域网内可投屏设备
     * 
     * @param timeout 扫描超时时间（秒）
     * @return 设备列表
     */
    @GetMapping("/devices")
    @Operation(summary = "扫描投屏设备", description = "扫描局域网内可投屏设备")
    @ApiResponse(responseCode = "200", description = "成功返回设备列表", 
                 content = @Content(mediaType = "application/json", 
                          schema = @Schema(implementation = CastDevice.class)))
    public ResponseEntity<List<CastDevice>> scanDevices(
            @Parameter(description = "扫描超时时间（秒）") 
            @RequestParam(defaultValue = "10") int timeout) {
        List<CastDevice> devices = castService.scanDevices(timeout);
        return ResponseEntity.ok(devices);
    }
    
    /**
     * 投屏设备信息类
     */
    public static class CastDevice {
        private String ip;
        private String name;
        private String type;
        private Map<String, Object> metadata;
        
        public CastDevice() {}
        
        public CastDevice(String ip, String name, String type) {
            this.ip = ip;
            this.name = name;
            this.type = type;
        }
        
        public String getIp() {
            return ip;
        }
        
        public void setIp(String ip) {
            this.ip = ip;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
}