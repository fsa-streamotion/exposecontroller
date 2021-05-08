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
            when {
                branch 'master'
            }
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

                    // TODO: Bake this into the jenkins builder image!
                    sh 'pip install awscli'
                    
                    // Build image and push to ECR
                    sh '''
                        aws sts assume-role-with-web-identity \
                        --role-arn $AWS_ROLE_ARN \
                        --role-session-name ecraccess \
                        --web-identity-token file://\$AWS_WEB_IDENTITY_TOKEN_FILE \
                        --duration-seconds 900 > /tmp/ecr-access.txt
                    '''
                    
                    sh '''
                        set +x
                        export VERSION=$(cat $WORKSPACE/VERSION)
                        && export AWS_ACCESS_KEY_ID=\$(cat /tmp/ecr-access.txt | jq -r '.Credentials.AccessKeyId') \
                        && export AWS_SECRET_ACCESS_KEY=\$(cat /tmp/ecr-access.txt | jq -r '.Credentials.SecretAccessKey') \
                        && export AWS_SESSION_TOKEN=\$(cat /tmp/ecr-access.txt | jq -r '.Credentials.SessionToken') \
                        && set -x \
                        && skaffold version \
                        && skaffold build -f skaffold.yaml
                    '''
                    
                    runCommand command: 'export', args: ['VERSION=latest', '&&', 'skaffold', 'build', '-f', 'skaffold.yaml'], dir: WORKSPACE

                    script {
                        def buildVersion = runCommand command: 'cat', args: ["VERSION"], dir: WORKSPACE, returnStdout: true
                        currentBuild.description = "$DOCKER_REGISTRY/$ORG/$APP_NAME:$buildVersion"
                        currentBuild.displayName = "$buildVersion"
                    }
                    
                    runCommand command: 'jx', args: ['step', 'post', 'build', '--image', "$DOCKER_REGISTRY/$ORG/$APP_NAME:\$(cat $WORKSPACE/VERSION)"], dir: WORKSPACE
                }
            }
        }

        stage('Push to Artifactory') {
            when {
                branch 'master'
            }
            environment {
                WORKSPACE = "\$GOPATH/src/github.com/jenkins-x/$APP_NAME"
                CHARTS_DIRECTORY = "$WORKSPACE/charts/$APP_NAME"
            }
            steps {
                container('go') {
                    // Release helm charts
                    // runCommand command: 'jx', args: ['step', 'changelog', '--generate-yaml=false', '--version', "v\$(cat $WORKSPACE/VERSION)"], dir: CHARTS_DIRECTORY
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
