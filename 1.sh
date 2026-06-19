#!/bin/bash
# 获取 GitHub 仓库所有跟踪文件的 raw 链接

REMOTE_URL=$(git config --get remote.origin.url)
REPO=$(echo "$REMOTE_URL" | sed -E 's|.*[:/]([^/]+/[^/]+)\.git|\1|')
BRANCH=$(git rev-parse --abbrev-ref HEAD)

git ls-files | while read -r file; do
    echo "https://raw.githubusercontent.com/$REPO/$BRANCH/$file"
done