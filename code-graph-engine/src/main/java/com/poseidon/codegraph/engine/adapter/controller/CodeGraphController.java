package com.poseidon.codegraph.engine.adapter.controller;

import com.poseidon.codegraph.engine.application.service.IncrementalUpdateService;
import com.poseidon.codegraph.engine.adapter.dto.ApiResponse;
import com.poseidon.codegraph.engine.adapter.dto.CreateFileNodesRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 代码图谱 Controller
 * 提供 REST API 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/code-graph")
public class CodeGraphController {
    
    private final IncrementalUpdateService incrementalUpdateService;
    
    @Autowired
    public CodeGraphController(IncrementalUpdateService incrementalUpdateService) {
        this.incrementalUpdateService = incrementalUpdateService;
    }
    
    /**
     * 更新文件的所有节点
     * 重新解析指定文件，更新所有代码节点和调用关系
     * 
     * @param request 创建文件节点请求（复用）
     * @return API 响应
     */
    @PutMapping("/files/nodes")
    public ApiResponse<Void> updateFileNodes(@RequestBody CreateFileNodesRequest request) {
        try {
            log.info("更新文件节点请求: projectName={}, absoluteFile={}, projectFile={}", 
                request.getProjectName(), request.getAbsoluteFilePath(), request.getProjectFilePath());
            
            // 参数校验
            if (request.getAbsoluteFilePath() == null || request.getAbsoluteFilePath().trim().isEmpty()) {
                return ApiResponse.error(400, "文件绝对路径不能为空");
            }
            if (request.getProjectFilePath() == null || request.getProjectFilePath().trim().isEmpty()) {
                return ApiResponse.error(400, "项目相对路径不能为空");
            }
            
            // 转换 classpath 和 sourcepath
            String[] classpathEntries = request.getClasspathEntries() != null 
                ? request.getClasspathEntries().toArray(new String[0])
                : new String[0];
            String[] sourcepathEntries = request.getSourcepathEntries() != null
                ? request.getSourcepathEntries().toArray(new String[0])
                : new String[0];
            
            // 调用服务处理文件修改
            incrementalUpdateService.handleFileModified(
                request.getProjectName(),
                request.getAbsoluteFilePath(),
                request.getProjectFilePath(),
                request.getGitRepoUrl(),
                request.getGitBranch(),
                classpathEntries,
                sourcepathEntries
            );
            
            log.info("文件节点更新成功: {}", request.getProjectFilePath());
            return ApiResponse.success("文件节点更新成功", null);
            
        } catch (Exception e) {
            log.error("更新文件节点失败: {}", request.getProjectFilePath(), e);
            return ApiResponse.error("更新文件节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除文件的所有节点
     * 删除指定文件相关的所有代码节点和调用关系
     * 
     * @param request 创建文件节点请求（复用）
     * @return API 响应
     */
    @DeleteMapping("/files/nodes")
    public ApiResponse<Void> deleteFileNodes(@RequestBody CreateFileNodesRequest request) {
        try {
            log.info("删除文件节点请求: projectName={}, projectFile={}", 
                request.getProjectName(), request.getProjectFilePath());
            
            // 删除操作其实只需要 projectFilePath，但为了接口统一，可能传了 absoluteFilePath
            if (request.getProjectFilePath() == null || request.getProjectFilePath().trim().isEmpty()) {
                return ApiResponse.error(400, "项目相对路径不能为空");
            }
            
            // 转换 classpath 和 sourcepath
            String[] classpathEntries = request.getClasspathEntries() != null 
                ? request.getClasspathEntries().toArray(new String[0])
                : new String[0];
            String[] sourcepathEntries = request.getSourcepathEntries() != null
                ? request.getSourcepathEntries().toArray(new String[0])
                : new String[0];
            
            // 调用服务处理文件删除
            incrementalUpdateService.handleFileDeleted(
                request.getProjectName(),
                request.getAbsoluteFilePath(), // 可能为 null，视情况而定
                request.getProjectFilePath(),
                request.getGitRepoUrl(),
                request.getGitBranch(),
                classpathEntries,
                sourcepathEntries
            );
            
            log.info("文件节点删除成功: {}", request.getProjectFilePath());
            return ApiResponse.success("文件节点删除成功", null);
            
        } catch (Exception e) {
            log.error("删除文件节点失败: {}", request.getProjectFilePath(), e);
            return ApiResponse.error("删除文件节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("服务运行正常", "OK");
    }
}