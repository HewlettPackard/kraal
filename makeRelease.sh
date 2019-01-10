#!/bin/bash
# Copyright 2018-2019 Hewlett Packard Enterprise Development LP
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
# documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
# rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
# Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
# WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
# COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
# OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

set -ue

function incVersion() {
    local currentVersion="$1"
    local patchNumber=${currentVersion##*.}
    patchNumber=$(( $patchNumber + 1 ))
    local majorMinor=${currentVersion%.*}
    echo "$majorMinor.$patchNumber"
}

function workingCopyClean() {
    local output
    output=$(git status --porcelain) && [ -z "$output" ]
}

if ! workingCopyClean; then
    echo "Working copy not clean - cannot make release.  Commit, reset, or stash changes first."
    exit 1
fi

prevVersion=$(grep 'id("com.hpe.kraal") version' README.md | cut -d'"' -f 4)
currentVersion=$(grep 'kraal version' build.gradle.kts | cut -d'"' -f2)
nextVersion=${1-$(incVersion "$currentVersion")}

echo "prev = $prevVersion, current = $currentVersion, next = $nextVersion"

sed -i "s/$prevVersion/$currentVersion/g" README.md
git stage -u README.md
git commit -m "Update README for v$currentVersion"
git tag -a "v$currentVersion" -m "Release v$currentVersion"

sed -i "s/$currentVersion\(.*\)kraal version/${nextVersion}\1kraal version/g" build.gradle.kts example/build.gradle.kts maven-example/pom.xml
git stage -u .
git commit -m "Update version for $nextVersion development"
echo "Review the changes:"
echo
echo git show HEAD~ HEAD
echo
echo "And push the release to GitHub:"
echo
echo git push : "v$currentVersion"
