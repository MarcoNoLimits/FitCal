# Runs the accuracy benchmark for several OpenRouter models on the same 100 dishes, with the
# production prompt, and prints one comparison table at the end.
# Needs $env:OPENROUTER_API_KEY set in this PowerShell window first.
# Usage (from the repo root):  & .\tools\accuracy-bench\compare-models.ps1

$ErrorActionPreference = "Stop"
$deno = "$env:USERPROFILE\.deno\bin\deno.exe"
$models = @(
    "google/gemini-2.5-flash",        # what FitCal uses today
    "google/gemini-3.1-flash-lite",
    "openai/gpt-5-mini",
    "google/gemini-3-flash-preview"
)

if (-not $env:OPENROUTER_API_KEY) { Write-Error "Set OPENROUTER_API_KEY first."; exit 1 }

$started = Get-Date
foreach ($m in $models) {
    Write-Host "`n=== $m ===" -ForegroundColor Cyan
    & $deno run --allow-net --allow-env --allow-read --allow-write tools/accuracy-bench/bench.ts --n 100 --seed 1 --provider openrouter --model $m
}

Write-Host "`n=== Comparison (calories) ===" -ForegroundColor Cyan
& $deno run --allow-read tools/accuracy-bench/compare.ts --since $started.ToUniversalTime().ToString("o")
