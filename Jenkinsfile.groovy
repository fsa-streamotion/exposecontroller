pipeline {
  agent {
      label "jenkins-go"
  }

  environment {
    ORG = 'fsa-streamotion'
    APP_NAME = 'exposecontroller'
  }

  stages {
    stage('Build PR') {
      when {
          branch 'PR-*'
      }

      environment {
          PR_VERSION = "\$IMAGE_VERSION-SNAPSHOT-\$BRANCH_NAME-\$BUILD_NUMBER"
      }

      steps {
        container('go') {
            sh "git config --global credential.helper store"
            sh "jx step git credentials"

            // Make Test
            sh "git clone git://github.com/jenkins-x/exposecontroller.git \$GOPATH/src/github.com/jenkins-x/exposecontroller"
            sh "cd \$GOPATH/src/github.com/jenkins-x/exposecontroller && make test"

            // Copy binary
            sh "mkdir out"
            sh "cp \$GOPATH/src/github.com/jenkins-x/exposecontroller/out/exposecontroller-linux-amd64 ./out"
            sh "export VERSION=\$PR_VERSION && skaffold build -f skaffold.yaml"

            script {
                currentBuild.displayName = PR_VERSION
                currentBuild.description = "${DOCKER_REGISTRY}/$ORG/$APP_NAME:$PR_VERSION"
            }
          }
        }
      }
        
    stage('Push To ECR') {
      when {
            branch 'master'
          }
      steps {
        container('go') {

          sh "git config --global credential.helper store"
          sh "jx step git credentials"

          sh "echo \$(jx-release-version) > VERSION"
          sh "jx step tag --version \$(cat VERSION)"

          // Build binary
          sh "git clone git://github.com/jenkins-x/exposecontroller.git \$GOPATH/src/github.com/jenkins-x/exposecontroller"
          sh "cd \$GOPATH/src/github.com/jenkins-x/exposecontroller && make"
          
          // Copy binary
          sh "mkdir out"
          sh "cp \$GOPATH/src/github.com/jenkins-x/exposecontroller/out/exposecontroller-linux-amd64 ./out"

          // Build image
          sh "skaffold version"
          sh "export VERSION=`cat VERSION` && skaffold build -f skaffold.yaml"
          sh "export VERSION=latest && skaffold build -f skaffold.yaml"

          script {
            def buildVersion =  readFile "${env.WORKSPACE}/VERSION"
            currentBuild.description = "${DOCKER_REGISTRY}/exposecontroller:$buildVersion"
            currentBuild.displayName = "$buildVersion"
          }
        }
      }
    }
  }

  post {
    always {
      cleanWs()
    }
  }
}
