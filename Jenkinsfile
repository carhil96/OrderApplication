pipeline {
  agent { label 'orderapp-build' }

  stages {
    stage('Build') {
      steps {
        sh 'mvn -B -DskipTests package'
      }
    }
  }
}
