pipeline {
  agent { label 'orderapp-build' }

  stages {
    stage('Build') {
      steps {
        sh 'mvn -B -DskipTests package'
      }
    }

    stage('Tests (excluding integration tests)') {
      steps {
        sh 'mvn -B "-Dtest=*,!*IntegrationTest" test'
      }
    }
  }
}
