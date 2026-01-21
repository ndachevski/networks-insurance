# Download JUnit 4 and Hamcrest into lib/ so the IDE can resolve org.junit.
# Run from the project root: .\download-junit.ps1

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path lib | Out-Null

$junit = "https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar"
$hamcrest = "https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"

Write-Host "Downloading JUnit 4.13.2..."
Invoke-WebRequest -Uri $junit -OutFile "lib\junit-4.13.2.jar" -UseBasicParsing
Write-Host "Downloading Hamcrest 1.3..."
Invoke-WebRequest -Uri $hamcrest -OutFile "lib\hamcrest-core-1.3.jar" -UseBasicParsing
Write-Host "Done. lib\junit-4.13.2.jar and lib\hamcrest-core-1.3.jar are ready."
