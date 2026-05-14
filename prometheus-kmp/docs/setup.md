# Environment Setup

## Prerequisites

- **JDK 17**
- **Android SDK** (API 36)
- **Git**

## Quick Start

### 1. Clone
```bash
git clone <repo-url>
cd Prometheus
```

### 2. JDK 17

**Linux (Ubuntu/Debian):**
```bash
sudo apt install openjdk-17-jdk
# Default path: /usr/lib/jvm/java-17-openjdk-amd64
```

**macOS:**
```bash
brew install openjdk@17
# Or download from https://adoptium.net
```

### 3. Android SDK

Set `ANDROID_HOME` in your shell config (`~/.bashrc` / `~/.zshrc`):
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH
```

Or use Android Studio → SDK Manager to install API 36.

### 4. Build
```bash
cd prometheus-kmp
./gradlew :androidApp:assembleDebug
```

## Platform Notes

### Linux
JDK default path in `gradle.properties` points to `/usr/lib/jvm/java-17-openjdk-amd64`.  
If your JDK is elsewhere, set `JAVA_HOME` or override in `~/.gradle/gradle.properties`:
```properties
org.gradle.java.home=/path/to/your/jdk-17
```

### macOS
The default path in `gradle.properties` will not exist on macOS.  
Override in `~/.gradle/gradle.properties`:
```properties
org.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
```

### iOS (Xcode)
Open `prometheus-app/prometheus-app.xcodeproj` in Xcode.  
Requires macOS + Xcode 16+.
