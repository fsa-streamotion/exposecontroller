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
          sh "export GOPATH=/usr/local/go"
          sh "export PATH=\$PATH:/usr/local/go/bin:/usr/local/go/bin"
          sh "whoami" // Debug for Jenkins weirdness

          // Build binary
          sh "pwd" // Debug for Jenkins weirdness
          sh "git clone git://github.com/jenkins-x/exposecontroller.git /usr/local/go/src/github.com/jenkins-x/exposecontroller"
          sh "pwd" // Debug for Jenkins weirdness
          sh "whoami" // Debug for Jenkins weirdness
          sh "cd /usr/local/go/src/github.com/jenkins-x/exposecontroller && make"
          sh "pwd" // Debug for Jenkins weirdness

          // Copy binary
          sh "pwd" // Debug for Jenkins weirdness
          sh "mkdir out"
          sh "pwd" // Debug for Jenkins weirdness
          sh "cp /usr/local/go/src/github.com/jenkins-x/exposecontroller/out/exposecontroller-linux-amd64 ./out"
          sh "pwd" // Debug for Jenkins weirdness

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
