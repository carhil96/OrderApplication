pipeline {
  agent any
  stages {
    stage('Build') {
      steps { sh 'mvn -B -DskipTests package' }
    }
    stage('Test') {
      steps { sh 'mvn test' }
    }
    stage('Image') {
      steps { sh 'docker build -t java-microservice-example:latest .' }
    }
  }
}
