pipeline {
    agent any

    stages {

        stage('Verificar entorno') {
            steps {
                bat 'java -version'
                bat 'jmeter --version'
            }
        }

        stage('Ejecutar JMeter') {
            steps {
                bat '''
                    jmeter -n ^
                    -t jmeter/test.jmx ^
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
