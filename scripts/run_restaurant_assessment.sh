#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
assessment_classes=$(mktemp -d)
trap 'rm -rf "$assessment_classes"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -d "$assessment_classes" \
  src/main/java/com/hafiz/disputeresolver/assessment/ServiceNotReceivedAssessment.java \
  experiments/restaurant-assessment-v1/RestaurantAssessmentExperiment.java
java -cp "$assessment_classes" RestaurantAssessmentExperiment
