pipeline {

    agent any

    environment {
        JMETER_HOME = 'C:\\apache-jmeter-5.6.3\\apache-jmeter-5.6.3'
    }

    stages {

        stage('Verificar entorno') {
            steps {
                bat 'java -version'
                bat '"%JMETER_HOME%\\bin\\jmeter.bat" --version'
                powershell 'Write-Host "PowerShell OK"'
            }
        }

        stage('Detectar Thread Groups') {
            steps {
                powershell '''
                    .\\scripts\\prepare-thread-groups.ps1 `
                        -JmxFile "jmeter\\test.jmx" `
                        -OutputDir "temp"
                '''

                bat 'dir temp'
            }
        }

        stage('Ejecutar Thread Groups') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"

                    if (Test-Path "results") {
                        Remove-Item "results" -Recurse -Force
                    }

                    if (Test-Path "reports") {
                        Remove-Item "reports" -Recurse -Force
                    }

                    New-Item -ItemType Directory -Path "results" | Out-Null
                    New-Item -ItemType Directory -Path "reports" | Out-Null

                    $jmxFiles = Get-ChildItem "temp\\*.jmx"

                    foreach ($jmx in $jmxFiles) {

                        $name = $jmx.BaseName

                        Write-Host ""
                        Write-Host "======================================"
                        Write-Host " Ejecutando Thread Group: $name"
                        Write-Host "======================================"
                        Write-Host ""

                        $resultFile = "results\\$name.jtl"
                        $reportDir = "reports\\$name"

                        & "$env:JMETER_HOME\\bin\\jmeter.bat" `
                            -n `
                            -t $jmx.FullName `
                            -l $resultFile `
                            -e `
                            -o $reportDir

                        if ($LASTEXITCODE -ne 0) {
                            throw "JMeter fallo ejecutando $name"
                        }
                    }
                '''
            }
        }

        stage('Verificar reportes') {
            steps {
                powershell '''
                    Write-Host ""
                    Write-Host "REPORTES GENERADOS"
                    Write-Host "=================="

                    Get-ChildItem "reports" -Directory | ForEach-Object {

                        $index = Join-Path $_.FullName "index.html"

                        if (Test-Path $index) {
                            Write-Host "OK -> $($_.Name)"
                        }
                        else {
                            Write-Host "ERROR -> $($_.Name) no tiene index.html"
                        }
                    }
                '''
            }
        }
    }

    post {

        always {

            archiveArtifacts(
                artifacts: 'results/*.jtl, reports/**/*',
                allowEmptyArchive: true
            )

        }
    }
}