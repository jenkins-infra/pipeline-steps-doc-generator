#!/usr/bin/env bash

# Test that the generated documentation is good enough for production use
# Tests are intentionally lightweight, focused on detecting known issues
#
# Script exits with non-zero exit code if any test fails

file=docFolder/allAscii/workflow-scm-step.adoc

echo "======== Check workflow scm step file size - $file"

if [ ! -f $file ]; then
        echo "$file not found, cannot test"
        exit 125 # Exit code 125 tells git bisect to skip this commit
fi

SCM_STEP_SIZE=$(stat -c%s $file)
if (( SCM_STEP_SIZE < 75000 )); then
        echo "$file size is ${SCM_STEP_SIZE}, expected at least 75000 bytes"
        exit 1
fi

exit 0
