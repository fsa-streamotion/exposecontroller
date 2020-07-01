# Build the manager binary
FROM golang:alpine AS builder

RUN apk add git

WORKDIR $GOPATH/src/

# Copy the go source
COPY go.mod go.mod
COPY go.sum go.sum
RUN go mod download

COPY exposestrategy/ exposestrategy/
COPY controller/ controller/
COPY exposecontroller.go exposecontroller.go

# Debug
# RUN pwd
# RUN ls -ltr

# Build
RUN CGO_ENABLED=0 GOARCH=amd64 GOOS=linux go build -ldflags="-w -s" -o ./out/exposecontroller-linux-amd64

COPY ./out/exposecontroller-linux-amd64 /exposecontroller

ENTRYPOINT ["/exposecontroller", "--daemon"]