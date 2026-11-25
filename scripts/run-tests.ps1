# ShopStream - unit test runner
#
# Runs the JUnit/Mockito suites of the five business services and prints one
# summary line per module. No Docker is required: every test is a plain unit test.
#
# Usage (from the repository root):
#   .\scripts\run-tests.ps1
#
# Maven is looked up in this order: mvn on PATH, a local Apache Maven under
# %TEMP%, then the temporary install path used on this machine.

param(
    [string[]]$Modules = @("product-service", "order-service", "cart-service", "search-service", "notification-service")
)

$ErrorActionPreference = "Continue"
Set-Location (Split-Path -Parent $PSScriptRoot)

$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $candidate = Join-Path $env:TEMP "apache-maven-3.9.9\bin\mvn.cmd"
    if (Test-Path $candidate) { $mvn = $candidate }
}
if (-not $mvn) {
    Write-Host "Maven not found. Install it or unpack apache-maven to $env:TEMP."
    exit 1
}

# Maven needs a JDK. If JAVA_HOME is not set, look in the usual places.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $jdk = @(
        (Get-ChildItem "$env:USERPROFILE\.vscode\extensions\redhat.java-*\jre\*\bin\java.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1),
        (Get-ChildItem "C:\Program Files\Java\jdk*\bin\java.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1),
        (Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk*\bin\java.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1)
    ) | Where-Object { $_ } | Select-Object -First 1
    if ($jdk) {
        $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $jdk.FullName)
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        Write-Host "JAVA_HOME=$env:JAVA_HOME"
    } else {
        Write-Host "No JDK found. Install a JDK 21+ or set JAVA_HOME."
        exit 1
    }
}

# Mockito's Byte Buddy only supports up to Java 23; allow it on newer JDKs.
$extra = "-DargLine=-Dnet.bytebuddy.experimental=true"

foreach ($module in $Modules) {
    $output = & $mvn -f "$module\pom.xml" -B -ntp test $extra 2>&1
    $summary = ($output | Select-String -Pattern "Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)$" | Select-Object -Last 1).Matches
    $result = if ($output -match "BUILD SUCCESS") { "BUILD SUCCESS" } else { "BUILD FAILURE" }

    if ($summary) {
        "{0,-22} tests run: {1,-3} failures: {2}  errors: {3}  ->  {4}" -f `
            $module, $summary.Groups[1].Value, $summary.Groups[2].Value, $summary.Groups[3].Value, $result
    } else {
        "{0,-22} ->  {1}" -f $module, $result
    }
}
