#!/bin/sh

# Check if build.gradle.kts has been modified
if git diff --cached --name-only | grep -e 'build.gradle.kts'; then
  echo "build.gradle.kts has been modified, checking gradle.lockfile..."

  # Check if gradle.lockfile exists
  if [ ! -f gradle.lockfile ]; then
    echo "gradle.lockfile not found, generating..."
    ./gradlew dependencies --write-locks
  else
    echo "gradle.lockfile found, updating..."
    ./gradlew dependencies --write-locks
  fi

  # Add the updated or generated gradle.lockfile to the commit
  git add gradle.lockfile
else
  echo "build.gradle.kts has not been modified, skipping gradle.lockfile check."
fi