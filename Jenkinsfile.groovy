pipeline {
  agent {
      label "streamotion-maven"
  }

  environment {
    ORG = 'fsa-streamotion'
    APP_NAME = 'exposecontroller'
  }

  stages {
    stage('Push To ECR') {
      steps {
        container('maven') {

          // ensure we're not on a detached head
          sh "git config --global credential.helper store"
          sh "jx step git credentials"

          sh "echo \$(jx-release-version) > VERSION"
          sh "jx step tag --version \$(cat VERSION)"

          // Install Go
          sh "curl -O https://storage.googleapis.com/golang/go1.14.4.linux-amd64.tar.gz"
          sh "tar -xvf go1.14.4.linux-amd64.tar.gz"
          sh "chown -R root:root ./go"
          sh "mv go /usr/local"
          // sh "export GOPATH=/usr/local/go"
          // sh "export PATH=\$PATH:/usr/local/go/bin:\$GOPATH/bin"

          // Build binary
          sh "export GOPATH=/usr/local/go && git clone git://github.com/jenkins-x/exposecontroller.git /usr/local/go/src/github.com/jenkins-x/exposecontroller"
          sh "export GOPATH=/usr/local/go && export PATH=\$PATH:/usr/local/go/bin:/usr/local/go/bin && cd /usr/local/go/src/github.com/jenkins-x/exposecontroller && make"

          // Copy binary
          sh "mkdir out"
          sh "export GOPATH=/usr/local/go && cp /usr/local/go/src/github.com/jenkins-x/exposecontroller/out/exposecontroller-linux-amd64 ./out"

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
