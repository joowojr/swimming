#!/usr/bin/env bash
#
# 공간 배경 원본과 가벼운 썸네일 영상을 미디어 버킷에 업로드하고,
# city/place seed를 생성하거나 갱신한다.

set -euo pipefail

readonly VIDEO_KEY_PREFIX="cities/videos/places"
readonly THUMBNAIL_KEY_PREFIX="cities/thumbnails/places"
readonly CACHE_CONTROL="public,max-age=31536000,immutable"
readonly VIDEO_CONTENT_TYPE="video/mp4"
readonly THUMBNAIL_CONTENT_TYPE="video/mp4"

local_file=""
bucket=""
place_id=""
city_id=""
place_name=""
logical_name=""
city_name=""
country_code=""
timezone=""
work_dir=""

usage() {
  cat >&2 <<EOF
usage: $0 --file <video> --bucket <bucket> --place-id <id> --city-id <id> \\
  --place-name <name> --logical-name <name> --city-name <name> \\
  --country-code <code> --timezone <timezone>

example:
  $0 --file ~/videos/lisbon.mov --bucket swimming-media-production \\
    --place-id 1 --city-id 1 --place-name "Café da Garagem" \\
    --logical-name 1_lisbon_1 --city-name Lisbon --country-code PT \\
    --timezone Europe/Lisbon
EOF
  exit 2
}

fail() {
  echo "error: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "필요한 명령어를 찾을 수 없습니다: $1"
}

parse_arguments() {
  while [ $# -gt 0 ]; do
    case "$1" in
      --file|--bucket|--place-id|--city-id|--place-name|--logical-name|--city-name|--country-code|--timezone)
        [ $# -ge 2 ] || usage
        case "$1" in
          --file) local_file=$2 ;;
          --bucket) bucket=$2 ;;
          --place-id) place_id=$2 ;;
          --city-id) city_id=$2 ;;
          --place-name) place_name=$2 ;;
          --logical-name) logical_name=$2 ;;
          --city-name) city_name=$2 ;;
          --country-code) country_code=$2 ;;
          --timezone) timezone=$2 ;;
        esac
        shift 2
        ;;
      -h|--help) usage ;;
      *) usage ;;
    esac
  done
}

validate_inputs() {
  [ -n "$local_file" ] || usage
  [ -n "$bucket" ] || usage
  [ -n "$place_id" ] || usage
  [ -n "$city_id" ] || usage
  [ -n "$place_name" ] || usage
  [ -n "$logical_name" ] || usage
  [ -n "$city_name" ] || usage
  [ -n "$country_code" ] || usage
  [ -n "$timezone" ] || usage
  [ -f "$local_file" ] || fail "파일이 없습니다: $local_file"

  [[ "$place_id" =~ ^[1-9][0-9]*$ ]] || fail "place-id는 양의 정수여야 합니다: $place_id"
  [[ "$city_id" =~ ^[1-9][0-9]*$ ]] || fail "city-id는 양의 정수여야 합니다: $city_id"
  [[ "$logical_name" =~ ^[A-Za-z0-9_-]+$ ]] \
    || fail "logical-name에는 영문, 숫자, 밑줄, 하이픈만 사용할 수 있습니다: $logical_name"
  [[ "$place_name" != *$'\n'* && "$place_name" != *$'\r'* ]] \
    || fail "place-name에는 줄바꿈을 넣을 수 없습니다"
  [[ "$city_name" != *$'\n'* && "$city_name" != *$'\r'* ]] \
    || fail "city-name에는 줄바꿈을 넣을 수 없습니다"
  [[ "$country_code" =~ ^[A-Z]{2}$ ]] \
    || fail "country-code는 ISO 3166-1 alpha-2 대문자여야 합니다: $country_code"
  [[ "$timezone" =~ ^[A-Za-z_+-]+(/[A-Za-z0-9_+-]+)+$ ]] \
    || fail "timezone 형식이 올바르지 않습니다: $timezone"

  require_command aws
  require_command awk
  require_command ffmpeg
  require_command ffprobe
  require_command mktemp
  require_command openssl
}

validate_source_video() {
  local input=$1
  local codec pixel_format

  codec=$(probe_stream_value "$input" codec_name)
  pixel_format=$(probe_stream_value "$input" pix_fmt)
  [ "$codec" = "h264" ] || fail "원본 영상 코덱은 H.264여야 합니다: $codec"
  [ "$pixel_format" = "yuv420p" ] || fail "원본 영상 픽셀 형식은 yuv420p여야 합니다: $pixel_format"
}

create_thumbnail_video() {
  local input=$1
  local output=$2

  echo "썸네일 영상을 변환합니다..."
  ffmpeg -hide_banner -loglevel error -y \
    -i "$input" \
    -map 0:v:0 \
    -vf "scale='min(640,iw)':'min(360,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,setsar=1,fps=15" \
    -c:v libx264 \
    -preset medium \
    -crf 30 \
    -pix_fmt yuv420p \
    -movflags +faststart \
    -map_metadata -1 \
    -map_chapters -1 \
    -an -sn -dn \
    "$output"
}

probe_stream_value() {
  local file=$1
  local entry=$2
  ffprobe -v error -select_streams v:0 \
    -show_entries "stream=$entry" -of default=noprint_wrappers=1:nokey=1 "$file"
}

validate_outputs() {
  local thumbnail_file=$1
  local codec pixel_format width height audio_streams frame_rate

  codec=$(probe_stream_value "$thumbnail_file" codec_name)
  pixel_format=$(probe_stream_value "$thumbnail_file" pix_fmt)
  width=$(probe_stream_value "$thumbnail_file" width)
  height=$(probe_stream_value "$thumbnail_file" height)
  frame_rate=$(probe_stream_value "$thumbnail_file" r_frame_rate)
  audio_streams=$(ffprobe -v error -select_streams a \
    -show_entries stream=index -of csv=p=0 "$thumbnail_file" | awk 'END { print NR }')

  [ "$codec" = "h264" ] || fail "썸네일 영상 코덱이 H.264가 아닙니다: $codec"
  [ "$pixel_format" = "yuv420p" ] || fail "썸네일 영상 픽셀 형식이 yuv420p가 아닙니다: $pixel_format"
  [ "$width" -le 640 ] && [ "$height" -le 360 ] \
    || fail "썸네일 영상 크기가 제한을 넘었습니다: ${width}x${height}"
  [ $((width % 2)) -eq 0 ] && [ $((height % 2)) -eq 0 ] \
    || fail "썸네일 영상의 가로·세로가 짝수가 아닙니다: ${width}x${height}"
  [ "$frame_rate" = "15/1" ] || fail "썸네일 영상 프레임률이 15fps가 아닙니다: $frame_rate"
  [ "$audio_streams" -eq 0 ] || fail "썸네일 영상에 오디오 스트림이 남아 있습니다"

  echo "변환 완료: thumbnail=${width}x${height}, fps=15"
}

content_hash() {
  openssl dgst -sha256 "$1" | awk '{print $NF}' | cut -c1-8
}

upload_if_missing() {
  local file=$1
  local object_key=$2
  local content_type=$3

  if aws s3api head-object --bucket "$bucket" --key "$object_key" >/dev/null 2>&1; then
    echo "skip: s3://$bucket/$object_key"
    return
  fi

  aws s3 cp "$file" "s3://$bucket/$object_key" \
    --content-type "$content_type" \
    --cache-control "$CACHE_CONTROL"
}

cleanup() {
  if [ -n "$work_dir" ] && [ -d "$work_dir" ]; then
    rm -rf -- "$work_dir"
  fi
}

validate_seed_target() {
  local seed_file=$1

  [ -f "$seed_file" ] || fail "place seed 파일을 찾을 수 없습니다: $seed_file"
  awk '
    /^INSERT INTO cities / { in_cities = 1 }
    in_cities && /^ON CONFLICT \(id\)/ { valid_cities = 1; in_cities = 0 }
    /^INSERT INTO places \(/ { in_places = 1 }
    in_places && /^\) VALUES$/ { in_values = 1 }
    in_values && /^ON CONFLICT \(id\)/ { valid = 1 }
    END { exit valid_cities && valid ? 0 : 1 }
  ' "$seed_file" || fail "seed의 관리 대상 cities/places VALUES 블록을 찾을 수 없습니다"
}

upsert_seed_catalog() {
  local seed_file=$1
  local video_key=$2
  local thumbnail_key=$3
  local seed_tmp escaped_place_name escaped_city_name escaped_timezone city_row place_row

  [ -f "$seed_file" ] || fail "place seed 파일을 찾을 수 없습니다: $seed_file"
  escaped_place_name=${place_name//\'/\'\'}
  escaped_city_name=${city_name//\'/\'\'}
  escaped_timezone=${timezone//\'/\'\'}
  city_row="  ($city_id, '$escaped_city_name', '$country_code', '$escaped_timezone')"
  place_row="  ($place_id, $city_id, '$escaped_place_name', 'VIDEO', '$video_key', '$thumbnail_key')"
  seed_tmp=$(mktemp "${seed_file}.tmp.XXXXXX")
  cp -p "$seed_file" "$seed_tmp"

  if ! awk \
    -v city_id="$city_id" \
    -v city_row="$city_row" \
    -v place_id="$place_id" \
    -v place_row="$place_row" '
    BEGIN {
      in_cities = 0
      in_places_header = 0
      in_places = 0
      city_count = 0
      place_count = 0
      city_found = 0
      place_found = 0
    }
    /^INSERT INTO cities / {
      in_cities = 1
      print
      next
    }
    in_cities && /^ON CONFLICT \(id\)/ {
      if (!city_found) city_rows[++city_count] = city_row
      for (row_index = 1; row_index <= city_count; row_index++) {
        suffix = row_index < city_count ? "," : ""
        print city_rows[row_index] suffix
      }
      in_cities = 0
      cities_done = 1
      print
      next
    }
    in_cities {
      row = $0
      sub(/,[[:space:]]*$/, "", row)
      if (row ~ "^[[:space:]]*\\([[:space:]]*" city_id "[[:space:]]*,") {
        row = city_row
        city_found = 1
      }
      city_rows[++city_count] = row
      next
    }
    /^INSERT INTO places \(/ {
      in_places_header = 1
      print
      next
    }
    in_places_header && /^\) VALUES$/ {
      in_places = 1
      in_places_header = 0
      print
      next
    }
    in_places && /^ON CONFLICT \(id\)/ {
      if (!place_found) place_rows[++place_count] = place_row
      for (row_index = 1; row_index <= place_count; row_index++) {
        suffix = row_index < place_count ? "," : ""
        print place_rows[row_index] suffix
      }
      in_places = 0
      places_done = 1
      print
      next
    }
    in_places {
      row = $0
      sub(/,[[:space:]]*$/, "", row)
      if (row ~ "^[[:space:]]*\\([[:space:]]*" place_id "[[:space:]]*,") {
        row = place_row
        place_found = 1
      }
      place_rows[++place_count] = row
      next
    }
    { print }
    END {
      if (!cities_done || !places_done) exit 3
    }
  ' "$seed_file" > "$seed_tmp"; then
    rm -f "$seed_tmp"
    fail "city/place seed 갱신에 실패했습니다"
  fi

  mv "$seed_tmp" "$seed_file"
}

main() {
  parse_arguments "$@"
  validate_inputs

  local script_dir repository_root seed_file thumbnail_file
  local video_hash thumbnail_hash video_key thumbnail_key video_object_key thumbnail_object_key
  script_dir=$(cd "$(dirname "$0")" && pwd)
  repository_root=$(cd "$script_dir/../.." && pwd)
  seed_file="$repository_root/backend/src/main/resources/db/migration/R__seed_places.sql"
  validate_seed_target "$seed_file"
  work_dir=$(mktemp -d "${TMPDIR:-/tmp}/swimming-media.XXXXXX")
  trap cleanup EXIT

  thumbnail_file="$work_dir/thumbnail.mp4"

  validate_source_video "$local_file"
  create_thumbnail_video "$local_file" "$thumbnail_file"
  validate_outputs "$thumbnail_file"

  video_hash=$(content_hash "$local_file")
  thumbnail_hash=$(content_hash "$thumbnail_file")
  video_key="$VIDEO_KEY_PREFIX/$logical_name.$video_hash.mp4"
  thumbnail_key="$THUMBNAIL_KEY_PREFIX/$logical_name.$thumbnail_hash.mp4"
  video_object_key="media/$video_key"
  thumbnail_object_key="media/$thumbnail_key"

  upload_if_missing "$local_file" "$video_object_key" "$VIDEO_CONTENT_TYPE"
  upload_if_missing "$thumbnail_file" "$thumbnail_object_key" "$THUMBNAIL_CONTENT_TYPE"
  upsert_seed_catalog "$seed_file" "$video_key" "$thumbnail_key"

  cat <<EOF

업로드 및 seed 갱신 완료
  배경 영상 : s3://$bucket/$video_object_key
  썸네일   : s3://$bucket/$thumbnail_object_key
  seed     : $seed_file

places.background_asset_key = $video_key
places.thumbnail_asset_key  = $thumbnail_key

CloudFront 무효화는 필요 없습니다. 변환 결과의 내용 해시가 키에 포함됩니다.
EOF
}

main "$@"
