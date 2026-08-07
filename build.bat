@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

echo ========================================
echo   LuckPrefix 一键构建脚本
echo ========================================
echo.

echo [1/3] 清理旧的构建产物...
call .\gradlew.bat clean --no-daemon --console=plain
if errorlevel 1 (
    echo.
    echo [x] 清理失败
    exit /b 1
)
echo.

echo [2/3] 编译并打包...
call .\gradlew build --no-daemon --console=plain
if errorlevel 1 (
    echo.
    echo [x] 构建失败
    exit /b 1
)
echo.

echo [3/3] 构建产物信息:
echo ----------------------------------------
for %%f in (build\libs\*.jar) do (
    echo 文件: %%f
    echo 大小: %%~zf 字节
    echo 时间: %%~tf
)
echo ----------------------------------------
echo.
echo [v] 构建成功！jar 文件位于 build\libs 目录
echo.

endlocal
pause
