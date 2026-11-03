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
                    "%JMETER_HOME%\\bin\\jmeter.bat" -n ^
                    -t jmeter\\test.jmx ^
                    -l results.jtl
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
            archiveArtifacts artifacts: 'results.jtl',
                             allowEmptyArchive: true
        }
    }
}
