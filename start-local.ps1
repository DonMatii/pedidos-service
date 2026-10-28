<#
  Arranca pedidos-service en local con la API key administrativa cargada
  desde .env.local (gitignored, nunca se commitea).

  Uso:
    .\start-local.ps1             # arranca en esta terminal (Ctrl+C para parar)
    .\start-local.ps1 -Detached   # arranca desacoplado (log en run.log / run-err.log)

  Requisitos: .env.local con APP_ADMIN_API_KEY (ver .env.local.example).
  El valor debe ser el MISMO que VITE_ADMIN_API_KEY del frontend
  (pasteleria-my-dreams-react/.env.development). Sin esta clave, los
  endpoints administrativos responden 503 (fail-closed).

  La base (MySQL docker) y catalogo-service ya tienen default local en
  application.properties: solo descomenta esas lineas en .env.local si tu
  entorno difiere del default. Kafka se espera en localhost:9092
  (docker-compose.yml).
#>
param([switch]$Detached)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$envFile = Join-Path $root ".env.local"

if (-not (Test-Path $envFile)) {
  Write-Error "No existe .env.local. Crealo desde .env.local.example con APP_ADMIN_API_KEY."
  exit 1
}

# Carga .env.local en variables de entorno del PROCESO actual
Get-Content $envFile | ForEach-Object {
  $line = $_.Trim()
  if ($line -and -not $line.StartsWith("#")) {
    $idx = $line.IndexOf("=")
    if ($idx -gt 0) {
      $name  = $line.Substring(0, $idx).Trim()
      $value = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
      Set-Item -Path "Env:$name" -Value $value
    }
  }
}

# Verifica minima + que la clave no quede como placeholder
$faltan = @("APP_ADMIN_API_KEY") | Where-Object { -not (Get-Item "Env:$_" -ErrorAction SilentlyContinue) }
if ($faltan) { Write-Error "Faltan en .env.local: $($faltan -join ', ')"; exit 1 }
if ($env:APP_ADMIN_API_KEY -like "PEGA_AQUI*") {
  Write-Error "Todavia no pusiste la clave real en .env.local (APP_ADMIN_API_KEY)."
  exit 1
}

Write-Host "Cargado: APP_ADMIN_API_KEY=($($env:APP_ADMIN_API_KEY.Length) chars)"

if ($Detached) {
  # El hijo (cmd -> mvnw -> java) hereda las variables Env: del proceso actual
  Start-Process cmd -ArgumentList "/c", ".\mvnw.cmd spring-boot:run > run.log 2> run-err.log" -WorkingDirectory $root -WindowStyle Hidden
  Write-Host "Arrancado desacoplado. Logs: run.log / run-err.log"
} else {
  & (Join-Path $root "mvnw.cmd") spring-boot:run
}
