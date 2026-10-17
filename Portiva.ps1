param([Parameter(Position=0)][string]$Command='doctor',[Parameter(ValueFromRemainingArguments=$true)][string[]]$ExtraArgs)
$ErrorActionPreference='Stop'
$pythonPath=if ($env:PORTIVA_PYTHON) { $env:PORTIVA_PYTHON } else { 'python' }
& $pythonPath (Join-Path $PSScriptRoot 'portiva.py') $Command @ExtraArgs
exit $LASTEXITCODE
