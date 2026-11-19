param([string]$Mode = "server", [string]$HostName = "localhost", [int]$Port = 5000)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force build/classes | Out-Null
$sources = Get-ChildItem src/main/java,src/test/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 --release 17 -d build/classes $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
switch ($Mode) {
    "server" { & java -cp build/classes kr.ac.moyeotalk.server.ChatServer $Port }
    "client" { & java -cp build/classes kr.ac.moyeotalk.client.ChatClient $HostName $Port }
    { $_ -in "test", "test-local" } {
        & java -ea -cp build/classes kr.ac.moyeotalk.StarterTest codec
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & java -ea -cp build/classes kr.ac.moyeotalk.client.ConsoleInputTest
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & java -ea -cp build/classes kr.ac.moyeotalk.server.ReliabilityTest
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        if ($Mode -eq "test") { & java -ea -cp build/classes kr.ac.moyeotalk.StarterTest }
    }
    default { throw "Mode: server, client, test, test-local" }
}
exit $LASTEXITCODE
