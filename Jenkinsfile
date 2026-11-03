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
            }
        }

        stage('Ejecutar JMeter') {
            steps {
                bat '''
                    if exist results.jtl del /Q results.jtl
                    if exist jmeter-report rmdir /S /Q jmeter-report
        
                    "%JMETER_HOME%\\bin\\jmeter.bat" -n ^
                    -t jmeter\\test.jmx ^
                    -l results.jtl ^
                    -e ^
                    -o jmeter-report
                '''
            }
        }

        stage('Verificar resultado') {
            steps {
                bat 'dir results.jtl'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'results.jtl, jmeter-report/**/*',
                             allowEmptyArchive: true
        }
    }
}
