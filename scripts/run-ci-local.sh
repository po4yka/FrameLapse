#!/bin/bash
# Run GitHub Actions workflows locally using act
# Usage: ./scripts/run-ci-local.sh [command]
#
# Prerequisites:
#   - Docker must be installed and running
#   - act must be installed: brew install act

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}Error: Docker is not running. Please start Docker Desktop.${NC}"
        exit 1
    fi
}

# Check if act is installed
check_act() {
    if ! command -v act &> /dev/null; then
        echo -e "${RED}Error: act is not installed.${NC}"
        echo -e "${YELLOW}Install with: brew install act${NC}"
        exit 1
    fi
}

# Print usage
usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  list          List all available jobs"
    echo "  lint          Run static analysis (spotless, detekt, lint)"
    echo "  build         Build Android debug APK"
    echo "  build-release Build Android release APK"
    echo "  test          Run unit tests"
    echo "  all           Run entire Android CI workflow"
    echo "  ios           Run iOS builds (native Gradle, not Docker)"
    echo "  dry-run       Show what would be executed without running"
    echo ""
    echo "Examples:"
    echo "  $0 lint       # Run static analysis checks"
    echo "  $0 test       # Run unit tests"
    echo "  $0 dry-run    # Preview what would run"
}

case "$1" in
    "list"|"-l")
        check_act
        echo -e "${GREEN}Available jobs:${NC}"
        act -l
        ;;
    "lint"|"static"|"static-analysis")
        check_docker
        check_act
        echo -e "${GREEN}Running static analysis...${NC}"
        act -j static-analysis
        ;;
    "build"|"build-debug")
        check_docker
        check_act
        echo -e "${GREEN}Building Android debug APK...${NC}"
        act -j build-android-debug
        ;;
    "build-release")
        check_docker
        check_act
        echo -e "${GREEN}Building Android release APK...${NC}"
        act -j build-android-release
        ;;
    "test")
        check_docker
        check_act
        echo -e "${GREEN}Running unit tests...${NC}"
        act -j test
        ;;
    "all"|"ci")
        check_docker
        check_act
        echo -e "${GREEN}Running entire Android CI workflow...${NC}"
        act -W .github/workflows/ci.yml
        ;;
    "ios")
        echo -e "${YELLOW}iOS workflows cannot run in Docker (requires macOS runner).${NC}"
        echo -e "${GREEN}Running iOS builds directly via Gradle...${NC}"
        echo ""
        echo "Building iOS Debug Framework..."
        ./gradlew linkDebugFrameworkIosSimulatorArm64
        echo ""
        echo "Building iOS Release Framework..."
        ./gradlew linkReleaseFrameworkIosSimulatorArm64
        echo ""
        echo "Running iOS tests..."
        ./gradlew :composeApp:iosSimulatorArm64Test
        echo -e "${GREEN}iOS builds complete!${NC}"
        ;;
    "dry-run"|"dry"|"-n")
        check_act
        echo -e "${YELLOW}Dry run - showing what would be executed:${NC}"
        act -n -W .github/workflows/ci.yml
        ;;
    "help"|"-h"|"--help")
        usage
        ;;
    *)
        if [ -n "$1" ]; then
            echo -e "${RED}Unknown command: $1${NC}"
            echo ""
        fi
        usage
        exit 1
        ;;
esac
