#!/bin/sh
set -eu

if [ -z "${AWS_LAMBDA_RUNTIME_API:-}" ]; then
    exec /usr/local/bin/aws-lambda-rie python -m awslambdaric "$@"
fi

exec python -m awslambdaric "$@"
