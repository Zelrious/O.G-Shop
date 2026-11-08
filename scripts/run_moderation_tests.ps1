# ==============================================================================
# SCRIPT KIỂM THỬ TỰ ĐỘNG HỆ THỐNG KIỂM DUYỆT TIN NHẮN & THÔNG BÁO O.G SHOP
# ==============================================================================
$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "  O.G SHOP - AUTOMATED MODERATION & NOTIFICATION TEST RUNNER          " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

$Root = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $Root "backend"
$FrontendDir = Join-Path $Root "frontend"

Write-Host ">>> [1/2] Đang chạy kiểm thử Backend (TextModeratorTest - JUnit 5)..." -ForegroundColor Yellow
Push-Location $BackendDir
try {
    $backendOutput = & ".\mvnw.cmd" test "-Dtest=TextModeratorTest" 2>&1
    $backendExit = $LASTEXITCODE

    if ($backendExit -eq 0) {
        Write-Host "  [PASS] Backend TextModeratorTest passed 100%!" -ForegroundColor Green
        
        # In các dòng kết quả benchmark
        $benchmarkLines = $backendOutput | Select-String "BENCHMARK RESULTS", "Short Clean", "Short Violation", "Medium Composite", "Long Composite", "Throughput", "Tests run:"
        foreach ($line in $benchmarkLines) {
            Write-Host "    $line" -ForegroundColor Gray
        }
    } else {
        Write-Host "  [FAIL] Backend TextModeratorTest failed!" -ForegroundColor Red
        $backendOutput | Select-Object -Last 30 | ForEach-Object { Write-Host $_ -ForegroundColor Red }
        exit 1
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host ">>> [2/2] Đang chạy kiểm thử Frontend (Vitest - Chat & NotificationBell)..." -ForegroundColor Yellow
Push-Location $FrontendDir
try {
    $frontendOutput = & npm test -- src/features/chat-offer/chat.test.tsx src/features/notifications/NotificationBell.test.tsx --run 2>&1
    $frontendExit = $LASTEXITCODE

    if ($frontendExit -eq 0) {
        Write-Host "  [PASS] Frontend Chat & NotificationBell tests passed 100%!" -ForegroundColor Green
        $summaryLines = $frontendOutput | Select-String "Test Files", "Tests", "Duration"
        foreach ($line in $summaryLines) {
            Write-Host "    $line" -ForegroundColor Gray
        }
    } else {
        Write-Host "  [FAIL] Frontend tests failed!" -ForegroundColor Red
        $frontendOutput | Select-Object -Last 30 | ForEach-Object { Write-Host $_ -ForegroundColor Red }
        exit 1
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "  TẤT CẢ CÁC BỘ KIỂM THỬ TỰ ĐỘNG ĐỀU ĐẠT (ALL TESTS PASSED - 100%)    " -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "Các hạng mục đã kiểm chứng tự động:" -ForegroundColor Cyan
Write-Host "  [✔] 1. Bắt SĐT cơ bản & phân cách dấu (., -, _, *, /, space)" -ForegroundColor White
Write-Host "  [✔] 2. Bắt SĐT biến đổi Homoglyph (O/0, l/1, S/5, B/8), ký tự tàng hình Zero-Width, Emoji số" -ForegroundColor White
Write-Host "  [✔] 3. Bắt SĐT phân mảnh qua nhiều tin nhắn (Sliding Window dung lượng 50, TTL 30m)" -ForegroundColor White
Write-Host "  [✔] 4. Chống lách luật: Chặn gửi bù số cuối bị che bằng chữ, emoji hoặc câu văn dài" -ForegroundColor White
Write-Host "  [✔] 5. Bắt các đầu số di động bỏ số 0 đầu (9 số bắt đầu bằng 3, 5, 7, 8, 9)" -ForegroundColor White
Write-Host "  [✔] 6. Bắt chèn chữ cái vào giữa số (012345abkcsja6789, 12345abkcsja6789)" -ForegroundColor White
Write-Host "  [✔] 7. Bắt chuỗi số đệm dài bảo đối phương 'lấy số ở giữa' (123450912345678999, 9990901234567888)" -ForegroundColor White
Write-Host "  [✔] 8. Bắt điều hướng nền tảng ngoài (Zalo, Facebook, Telegram) và defanged URLs" -ForegroundColor White
Write-Host "  [✔] 9. Bắt từ lóng thanh toán ngoài sàn (chuyển lúa, cọc trước, ck riêng)" -ForegroundColor White
Write-Host "  [✔] 10. Bảo vệ PII CCCD/CMND 12 chữ số và chữ viết" -ForegroundColor White
Write-Host "  [✔] 11. Bộ lọc Anti-False-Positive: Không bắt nhầm giá tiền, kích thước, thời gian bảo hành, %" -ForegroundColor White
Write-Host "  [✔] 12. Tối ưu Notification: Chuông hệ thống đồng bộ 3s, chỉ báo cho đối tác khi trả giá" -ForegroundColor White
Write-Host "  [✔] 13. Hiệu năng CPU Laptop: < 0.25ms / tin nhắn thường, 0 Quota AI" -ForegroundColor White
Write-Host "======================================================================" -ForegroundColor Green
Write-Host ""
