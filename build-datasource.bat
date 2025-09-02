@echo off
echo 开始构建自定义数据源实现JAR包...

REM 设置项目路径
set PROJECT_PATH=%~dp0

REM 创建临时目录用于构建
set BUILD_DIR=%PROJECT_PATH%datasource-build
mkdir "%BUILD_DIR%" 2>nul

REM 复制必要的源文件到构建目录
echo 复制源文件...
xcopy "%PROJECT_PATH%src\main\java" "%BUILD_DIR%\src\main\java\" /E /I /Q
xcopy "%PROJECT_PATH%src\main\resources" "%BUILD_DIR%\src\main\resources\" /E /I /Q

REM 复制构建配置文件
copy "%PROJECT_PATH%datasource-pom.xml" "%BUILD_DIR%\pom.xml" >nul

REM 进入构建目录
cd /d "%BUILD_DIR%"

REM 使用Maven构建JAR包
echo 正在使用Maven构建JAR包...
call mvn clean package

REM 检查构建是否成功
if %ERRORLEVEL% EQU 0 (
    echo 构建成功!
    
    REM 将生成的JAR文件复制到libs目录
    echo 复制JAR文件到libs目录...
    xcopy "target\*.jar" "..\libs\" /Y /Q
    
    echo 自定义数据源实现已成功构建并放入libs目录!
) else (
    echo 构建失败，请检查错误信息!
    pause
    exit /b 1
)

REM 清理临时构建目录
cd /d "%PROJECT_PATH%"
rmdir "%BUILD_DIR%" /S /Q

echo 构建脚本执行完成!
pause