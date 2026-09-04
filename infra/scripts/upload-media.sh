#!/usr/bin/env bash
#
# 배경 영상을 미디어 버킷에 올리고, DB(R__seed_places.sql)에 넣을 키를 출력한다.
#
# 사용법:
#   infra/scripts/upload-media.sh <로컬파일> <논리이름> [버킷]
#
# 예:
#   infra/scripts/upload-media.sh ~/videos/lisbon.mp4 1_lisbon_1
#   → cities/videos/places/1_lisbon_1.a1b2c3d4.mp4
#
# 버킷을 넘기지 않으면 infra/terraform 에서 media_bucket_name 출력을 읽는다.

set -euo pipefail

readonly KEY_PREFIX="cities/videos/places"
readonly CACHE_CONTROL="public,max-age=31536000,immutable"
readonly CONTENT_TYPE="video/mp4"

usage() {
  echo "usage: $0 <local-file> <logical-name> [bucket]" >&2
  echo "  example: $0 ~/videos/lisbon.mp4 1_lisbon_1" >&2
  exit 2
}

[ $# -ge 2 ] || usage

local_file=$1
logical_name=$2
bucket=${3:-}

[ -f "$local_file" ] || { echo "error: 파일이 없습니다: $local_file" >&2; exit 1; }

# 논리 이름에 확장자나 경로가 섞이면 키가 깨진다.
case "$logical_name" in
  *.*|*/*) echo "error: 논리 이름에는 확장자와 경로를 넣지 않습니다: $logical_name" >&2; exit 1 ;;
esac

if [ -z "$bucket" ]; then
  script_dir=$(cd "$(dirname "$0")" && pwd)
  bucket=$(terraform -chdir="$script_dir/../terraform" output -raw media_bucket_name)
fi

# 내용 해시. 파일이 같으면 키도 같아지므로 재업로드가 멱등해진다.
hash=$(openssl dgst -sha256 "$local_file" | awk '{print $NF}' | cut -c1-8)

db_key="$KEY_PREFIX/$logical_name.$hash.mp4"
# CloudFront 의 /media/* behavior 가 경로를 그대로 오리진에 넘기므로,
# 오브젝트는 media/ 아래에 있어야 /media/<db_key> 로 해석된다.
object_key="media/$db_key"

if aws s3api head-object --bucket "$bucket" --key "$object_key" >/dev/null 2>&1; then
  echo "skip: 같은 내용이 이미 올라가 있습니다"
else
  aws s3 cp "$local_file" "s3://$bucket/$object_key" \
    --content-type "$CONTENT_TYPE" \
    --cache-control "$CACHE_CONTROL"
fi

cat <<EOF

업로드 완료
  버킷 오브젝트 : s3://$bucket/$object_key
  Cache-Control : $CACHE_CONTROL

R__seed_places.sql 의 background_asset_key 에 넣을 값:

  $db_key

키를 바꾼 뒤에는 마이그레이션을 반영해야 새 영상이 보인다.
CloudFront 무효화는 필요 없다. 키가 달라져 캐시가 겹치지 않는다.
EOF
