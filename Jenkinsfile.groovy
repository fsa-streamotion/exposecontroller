pipeline {
    agent {
        label "jenkins-go"
    }
    stages {
        stage('CI Build and Test') {
            when {
                branch 'PR-*'
            }
            environment {
                PR_VERSION = "$BRANCH_NAME-$BUILD_NUMBER"
                WORKSPACE = '$GOPATH/src/github.com/jenkins-x/exposecontroller'
                CHARTS_DIRECTORY = "$WORKSPACE/charts/exposecontroller"
            }
            steps {
                container('go') {
                    sh "git config --global credential.helper store"
                    sh "jx step git credentials"

                    prepareWorkspace()

                    // Run tests
                    runCommand command: 'make', args: ['out/exposecontroller-linux-amd64'], dir: WORKSPACE
                    runCommand command: 'make', args: ['test'], dir: WORKSPACE

                    // Build charts
                    runCommand command: 'helm', args: ['init', '--client-only'], dir: CHARTS_DIRECTORY
                    runCommand command: 'make', args: ['preview'], dir: CHARTS_DIRECTORY
                    runCommand command: 'make', args: ['print'], dir: CHARTS_DIRECTORY
                    runCommand command: 'helm', args: ['template', '.'], dir: CHARTS_DIRECTORY

                    script {
                        currentBuild.displayName = PR_VERSION
                        currentBuild.description = "${DOCKER_REGISTRY}/$ORG/$APP_NAME:$PR_VERSION"
                    }
                }
            }
        }

        stage('Build and Release') {
            environment {
                CHARTMUSEUM_CREDS = credentials('jenkins-x-chartmuseum')
                GH_CREDS = credentials('jx-pipeline-git-github-github')
                WORKSPACE = '$GOPATH/src/github.com/jenkins-x/exposecontroller'
                CHARTS_DIRECTORY = "$WORKSPACE/charts/exposecontroller"
            }
            /*when {
                branch 'master'
            }*/
            steps {
                container('go') {
                    sh "git config --global credential.helper store"
                    sh "jx step git credentials"

                    prepareWorkspace()

                    runCommand command: 'echo', args: ['$(jx-release-version)', '>', 'VERSION'], dir: WORKSPACE
                    runCommand command: 'jx', args: ['tag', '--version', '$(var VERSIOM)'], dir: WORKSPACE


                    /*dir ('/home/jenkins/go/src/github.com/jenkins-x/exposecontroller') {
                        git "https://github.com/jenkins-x/exposecontroller"

                        sh "echo \$(jx-release-version) > version/VERSION"
                        sh "git add version/VERSION"
                        sh "git commit -m 'release \$(cat version/VERSION)'"

                        sh "GITHUB_ACCESS_TOKEN=$GH_CREDS_PSW make release"
                    }
                    dir ('/home/jenkins/go/src/github.com/jenkins-x/exposecontroller/charts/exposecontroller') {
                        sh "helm init --client-only"
                        sh "make release"
                    }*/
                }
            }
        }
    }


}

private void prepareWorkspace() {
    sh "mkdir -p $WORKSPACE && cp -R ./ $WORKSPACE"
}

def runCommand(Map params) {
    def commands = []

    params?.dir?.with { commands << "cd $it" }

    def command = []
    params?.command?.with { command << it }
    params?.args?.with { command << it.join(' ') }
    commands << command.join(' ')

    sh script: commands.join(' && '), returnStdout: params?.returnStdout ?: false
}


/*
* stage('Build Master') {
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

          script {
            def buildVersion =  readFile "${env.WORKSPACE}/VERSION"
            currentBuild.description = "${DOCKER_REGISTRY}/exposecontroller:$buildVersion"
            currentBuild.displayName = "$buildVersion"
          }
        }
      }
    }

    stage('Push to Artifactory') {
        when {
          branch 'master'
        }
        steps {
          container('go') {
            // release the helm chart
            sh "cd \$GOPATH/src/github.com/jenkins-x/exposecontroller/charts/exposecontroller && make release && make print"
            }
          }
        }
      }*/
