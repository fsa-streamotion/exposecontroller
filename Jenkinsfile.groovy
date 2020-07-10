pipeline {
    agent {
        label "jenkins-go"
    }
    stages {
        stage('CI Build and Test') {
            /*when {
                branch 'PR-*'
            }*/
            environment {
                WORKSPACE = '$GOPATH/src/github.com/jenkins-x/exposecontroller'
            }
            steps {

                container('go') {
                    sh "git config --global credential.helper store"
                    sh "jx step git credentials"

                    // Prepare workspace
                    sh "mkdir -p $WORKSPACE && cp -R ./ $WORKSPACE"

                    // Run tests
                    runCommand directory: WORKSPACE, command: 'pwd'
                    runCommand directory: WORKSPACE, command: 'ls', args: ['-l']
                }

                /*dir ('/home/jenkins/go/src/github.com/jenkins-x/exposecontroller') {
                    checkout scm
                    sh "pwd"
                    sh "ls -l"
                    sh "which make"
                }
                container('go') {
                    sh "cd /home/jenkins/go/src/github.com/jenkins-x/exposecontroller"
                    sh "pwd"
                    sh "ls -l"
                    *//*sh "make test"
                    sh "make"*//*
                }
                *//*dir ('/home/jenkins/go/src/github.com/jenkins-x/exposecontroller/charts/exposecontroller') {
                    sh "helm init --client-only"

                    sh "make build"
                    sh "helm template ."
                }*/
            }
        }

        stage('Build and Release') {
            environment {
                CHARTMUSEUM_CREDS = credentials('jenkins-x-chartmuseum')
                GH_CREDS = credentials('jx-pipeline-git-github-github')
            }
            when {
                branch 'master'
            }
            steps {
                dir ('/home/jenkins/go/src/github.com/jenkins-x/exposecontroller') {
                    git "https://github.com/jenkins-x/exposecontroller"

                    sh "echo \$(jx-release-version) > version/VERSION"
                    sh "git add version/VERSION"
                    sh "git commit -m 'release \$(cat version/VERSION)'"

                    sh "GITHUB_ACCESS_TOKEN=$GH_CREDS_PSW make release"
                }
                dir ('/home/jenkins/go/src/github.com/jenkins-x/exposecontroller/charts/exposecontroller') {
                    sh "helm init --client-only"
                    sh "make release"
                }
            }
        }
    }
}

def runCommand(Map params) {
    def commands = []

    params?.directory?.with { commands << "cd $it" }

    def command = []
    params?.command?.with { command << it }
    params?.args?.with { command << it.join(' ') }
    commands << command.join(' ')

    sh script: commands.join(' && '), returnStdout: true
}
