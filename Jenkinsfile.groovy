pipeline {
    agent {
        label "jenkins-go"
    }
    environment {
        ORG = 'fsa-streamotion'
        APP_NAME = 'exposecontroller'
    }
    stages {
        stage('CI Build and Test') {
            when {
                branch 'PR-*'
            }
            environment {
                PR_VERSION = "$BRANCH_NAME-$BUILD_NUMBER"
                WORKSPACE = "\$GOPATH/src/github.com/jenkins-x/$APP_NAME"
                CHARTS_DIRECTORY = "$WORKSPACE/charts/$APP_NAME"
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
                WORKSPACE = "\$GOPATH/src/github.com/jenkins-x/$APP_NAME"
                CHARTS_DIRECTORY = "$WORKSPACE/charts/$APP_NAME"
            }
            /*when {
                branch 'master'
            }*/
            steps {
                container('go') {
                    sh "git config --global credential.helper store"
                    sh "jx step git credentials"

                    prepareWorkspace()

                    // Prepare version
                    runCommand command: 'echo', args: ['$(jx-release-version)', '>', 'VERSION'], dir: WORKSPACE
                    runCommand command: 'jx', args: ['step', 'tag', '--version', '$(cat VERSION)'], dir: WORKSPACE

                    // Build binary
                    runCommand command: 'make', args: ['out/exposecontroller-linux-amd64'], dir: WORKSPACE

                    // Build image and push to ECR
                    runCommand command: 'skaffold', args: ['version'], dir: WORKSPACE
                    runCommand command: 'export', args: ['VERSION=`cat VERSION`', '&&', 'skaffold', 'build', '-f', 'skaffold.yaml'], dir: WORKSPACE
                    runCommand command: 'export', args: ['VERSION=latest', '&&', 'skaffold', 'build', '-f', 'skaffold.yaml'], dir: WORKSPACE

                    script {
                        def buildVersion = readFile "${WORKSPACE}/VERSION"
                        currentBuild.description = "${DOCKER_REGISTRY}/exposecontroller:$buildVersion"
                        currentBuild.displayName = "$buildVersion"
                    }

                    runCommand command: 'jx', args: ['step', 'post', 'build', '--image', "$DOCKER_REGISTRY/$ORG/$APP_NAME:\$(cat VERSION)"]
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

        stage('Push to Artifactory') {
            /*when {
                branch 'master'
            }*/
            environment {
                WORKSPACE = "\$GOPATH/src/github.com/jenkins-x/$APP_NAME"
                CHARTS_DIRECTORY = "$WORKSPACE/charts/$APP_NAME"
            }
            steps {
                container('go') {
                    // Release helm charts
                    runCommand command: 'cp', args: ["VERSION", CHARTS_DIRECTORY], dir: WORKSPACE
                    runCommand command: 'jx', args: ['step', 'changelog', '--generate-yaml=false', '--version', "v\$(cat VERSION)"], dir: CHARTS_DIRECTORY
                    runCommand command: 'make', args: ['release'], dir: CHARTS_DIRECTORY
                    runCommand command: 'make', args: ['print'], dir: CHARTS_DIRECTORY
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
