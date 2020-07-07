CHART_REPO := https://artifactory.cluster.foxsports-gitops-prod.com.au/artifactory/helm/
CHART_REPO_KEY := helm-virtual
NAME := exposecontroller
NAMESPACE := ${NAME}-test
OS := $(shell uname)
RELEASE_FILENAME := $(NAME)-$(shell sed -n 's/^version: //p' Chart.yaml).tgz

print:
	helm template .

preview:
	helm dependency build
	helm lint

release: clean
	@test -z $(CHARTMUSEUM_CREDS) && printf "Environment variable CHARTMUSEUM_CREDS not found! \n" && exit 1 || exit 0
	helm dependency build
	helm lint
	helm init --client-only
	helm package .
	@echo Pushing Chart to Helm repo...
	@curl --fail -u $(CHARTMUSEUM_CREDS) -X PUT $(CHART_REPO)/$(RELEASE_FILENAME) -T $(RELEASE_FILENAME)
	@rm -rf ${NAME}*.tgz

clean:
	rm -rf charts
	rm -rf ${NAME}*.tgz
