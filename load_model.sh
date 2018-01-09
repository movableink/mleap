#!/bin/bash
set -e
set -u

if [[ "${1:-""}" != "" ]]; then
    company="${1}"
else
    company="4894"
fi

model_s3_path="s3://movableink-data/notebooks/data-science/research/Freed/conversion_model_results/for_engineering/1.7/model_bundles/model_${company}_mleap.zip"
model_path="/tmp/model_${company}.zip"

aws s3 cp "${model_s3_path}" "${model_path}"

curl \
    -X PUT \
    -H "Content-Type: application/json" \
    -d '{"path": "'"${model_path}"'"}' \
    "http://localhost:65327/model_${company}"
