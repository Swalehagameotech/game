# Run full platform test suite (backend + frontend)
$ErrorActionPreference = "Stop"

Write-Host "==> Backend tests (Maven)"
Push-Location "$PSScriptRoot\backend"
try {
  if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd test
  } else {
    mvn test
  }
} finally {
  Pop-Location
}

Write-Host "==> Frontend tests (Vitest)"
Push-Location "$PSScriptRoot\frontend"
try {
  if (-not (Test-Path ".\node_modules")) {
    npm install
  }
  npm test
} finally {
  Pop-Location
}

Write-Host "All tests completed."
