pipeline {
  agent { label 'orderapp-build' }

  stages {
    stage('Build') {
      steps {
        sh 'mvn -B -DskipTests package'
      }
    }

    stage('Tests (excluding ClickHouse Testcontainers)') {
      environment {
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres-ci:5432/appdb'
        SPRING_DATASOURCE_USERNAME = 'app'
        SPRING_DATASOURCE_PASSWORD = 'ci_test_password_only'
      }
      steps {
        sh 'mvn -B "-Dtest=*,!ClickHouseWriterIntegrationTest" test'
      }
    }
  }
}
