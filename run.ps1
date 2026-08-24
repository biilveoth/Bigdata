param(
    [string]$HdfsPath = "/",
    [string]$DfsUrl = "http://171.226.11.41:30091"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Khong tim thay Java. Hay cai JDK 11 tro len va them java vao PATH."
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Khong tim thay Maven. Hay cai Maven 3.8 tro len va them mvn vao PATH."
}

$projectDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $projectDirectory
try {
    mvn --quiet package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build that bai (exit code $LASTEXITCODE)."
    }

    java -jar "target/hdfs-file-lister-1.0.0.jar" $DfsUrl $HdfsPath
    if ($LASTEXITCODE -ne 0) {
        throw "Chuong trinh ket thuc voi exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}