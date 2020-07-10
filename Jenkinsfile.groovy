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
          branch 'TESTPR-*'
      }

      environment {
          PR_VERSION = "$BRANCH_NAME-$BUILD_NUMBER"
      }

      steps {
        container('go') {
            sh "git config --global credential.helper store"
            sh "jx step git credentials"

            // Make Test
            sh "mkdir -p \$GOPATH/src/github.com/jenkins-x/exposecontroller"
            sh "cp -R ./ \$GOPATH/src/github.com/jenkins-x/exposecontroller"
            sh "cd \$GOPATH/src/github.com/jenkins-x/exposecontroller && make test"

            // Copy binary
            sh "mkdir out"
            sh "cp \$GOPATH/src/github.com/jenkins-x/exposecontroller/out/exposecontroller-linux-amd64 ./out"

            // Build Image and push to ECR
            sh "export VERSION=\$PR_VERSION && skaffold build -f skaffold.yaml"

            // Build Helm Chart
            sh "cd \$GOPATH/src/github.com/jenkins-x/exposecontroller/charts/exposecontroller"
            sh "jx step tag --version \$PR_VERSION"
            sh "jx step changelog --generate-yaml=false --version v\$PR_VERSION"
            sh "cd \$GOPATH/src/github.com/jenkins-x/exposecontroller/charts/exposecontroller && make preview && make print"

            script {
                currentBuild.displayName = PR_VERSION
                currentBuild.description = "${DOCKER_REGISTRY}/$ORG/$APP_NAME:$PR_VERSION"
            }
          }
        }
      }
        
    stage('Build Master') {
      when {
            branch 'PR-*'
          }
      steps {
        container('go') {
          sh "git config --global credential.helper store"
          sh "jx step git credentials"

          sh "echo \$(jx-release-version) > VERSION"
          sh "jx step tag --version \$(cat VERSION)"
          sh "jx step changelog --generate-yaml=false --version v\$(cat VERSION)"

          // Build binary
          sh "mkdir -p \$GOPATH/src/github.com/jenkins-x/exposecontroller"
          sh "cp -R ./ \$GOPATH/src/github.com/jenkins-x/exposecontroller"
          sh "cd \$GOPATH/src/github.com/jenkins-x/exposecontroller && make"
          
          // Copy binary
          sh "mkdir out"
          sh "cp \$GOPATH/src/github.com/jenkins-x/exposecontroller/out/exposecontroller-linux-amd64 ./out"

          // Build image and push to ECR
          sh "skaffold version"
          sh "export VERSION=`cat VERSION` && skaffold build -f skaffold.yaml"
          sh "export VERSION=latest && skaffold build -f skaffold.yaml"

          // Push to Artifactory
          sh "cd \$GOPATH/src/github.com/jenkins-x/exposecontroller/charts/exposecontroller && make release && make print"

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