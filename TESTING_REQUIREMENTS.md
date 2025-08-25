# CoquetteMobile Unity Testing Requirements

## Prerequisites

### ADB Setup
```bash
# Install ADB (if not already installed)
sudo apt-get install android-tools-adb

# Enable USB Debugging on Android device:
# Settings → Developer Options → USB Debugging

# Connect device and verify
adb devices
```

## Build & Deploy Commands

### Clean Build & Deploy
```bash
# From project root (/home/memory/Desktop/Projects/AndroidAI/)
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Quick Redeploy (after code changes)
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing Commands

### Launch App
```bash
adb shell am start -n com.yourname.coquettemobile/.MainActivity
```

### Kill App (for fresh restart)
```bash
adb shell am force-stop com.yourname.coquettemobile
```

## Log Monitoring

### Real-time Unity Architecture Logs
```bash
# Clear old logs and monitor Unity flow
adb logcat -c && adb logcat | grep -E "(UnityFlow|UnifiedReasoningAgent|MobileToolsAgent)"
```

### Real-time Tool Execution Logs  
```bash
# Monitor specific tools
adb logcat | grep -E "(WebFetchTool|ExtractorTool|SummarizerTool|DeviceContextTool|NotificationTool)"
```

### Crash Detection
```bash
# Monitor for crashes/errors
adb logcat | grep -E "(FATAL|AndroidRuntime|Exception|Error|crashed)" 
```

### Full Debug Logs (when things go wrong)
```bash
# Capture all app logs for debugging
adb logcat -c && adb logcat | grep "com.yourname.coquettemobile"
```

## On-Device Log Files

### CoquetteLogger Files Location
- **Path**: `/data/data/com.yourname.coquettemobile/files/logs/`
- **Format**: `coquette-YYYY-MM-DD.log`
- **Retention**: 7 days auto-cleanup

### Access Device Logs
```bash
# Pull logs from device
adb shell run-as com.yourname.coquettemobile cat /data/data/com.yourname.coquettemobile/files/logs/coquette-$(date +%Y-%m-%d).log

# Pull all log files
adb shell run-as com.yourname.coquettemobile find /data/data/com.yourname.coquettemobile/files/logs/ -name "*.log" -exec cat {} \;
```

### Clear Device Logs
```bash
adb shell run-as com.yourname.coquettemobile rm -rf /data/data/com.yourname.coquettemobile/files/logs/*
```

## Database Management

### Clear App Data (fresh database)
```bash
adb shell pm clear com.yourname.coquettemobile
```

### Database Location
- **Path**: `/data/data/com.yourname.coquettemobile/databases/coquette_database`
- **Current Version**: 3 (Unity architecture)

## Ollama Server Connection

### Default Endpoints
- **Main Server**: `http://10.10.20.19:11434`
- **Tool Server**: `http://10.10.20.120:11434` (if configured)

### Test Server Connection
```bash
# Test main server
curl http://10.10.20.19:11434/api/tags

# Test model availability
curl http://10.10.20.19:11434/api/tags | grep "Jan-v1-4B"
```

## Unity Architecture Testing Checklist

### Basic Functionality
- [ ] App launches without crashing
- [ ] Default personality loads
- [ ] Ollama server connection established
- [ ] Models list populates

### Unity Flow Testing
- [ ] Send simple message: "Hello" → Direct response
- [ ] Send web request: "What's on Slashdot?" → Tools JSON generated
- [ ] Tool selection works: WebFetchTool, ExtractorTool selected
- [ ] Tool execution completes
- [ ] Final response generated with tool results

### Tool Chain Testing
- [ ] **WebFetchTool**: "Check https://example.com"
- [ ] **ExtractorTool**: Auto-chains after WebFetch
- [ ] **SummarizerTool**: "Summarize this text: [long text]"
- [ ] **DeviceContextTool**: "What's my battery level?"
- [ ] **NotificationTool**: "Send me a notification"

### Performance Testing
- [ ] Response time < 10 seconds for simple queries
- [ ] Tool execution < 30 seconds for web requests
- [ ] Memory usage stable (no leaks)
- [ ] Battery usage reasonable

## Key Log Patterns to Watch

### Successful Unity Flow
```
D UnityFlow: === UNITY SINGLE-BRAIN FLOW ===
D UnityFlow: User message: 'what's on slashdot?'
D UnifiedReasoningAgent: Processing: what's on slashdot?
D UnityFlow: Tools selected: [WebFetchTool, ExtractorTool]
D UnityFlow: WebFetchTool result: [HTML content]
D UnityFlow: ExtractorTool result: [Readable text]
```

### JSON Parsing Success
```
D UnifiedReasoningAgent: Parsing response: {...}
(Should NOT see "JSON parsing failed" or "Unknown response type")
```

### Tool Execution Success
```
D MobileToolsAgent: Executing tools for: what's on slashdot?
D WebFetchTool: Fetching: https://slashdot.org/
D ExtractorTool: Extracting text from HTML
```

## Troubleshooting Common Issues

### App Crashes on Launch
```bash
# Database migration issue - clear app data
adb shell pm clear com.yourname.coquettemobile
```

### Tools Not Executing
```bash
# Check JSON parsing in logs
adb logcat | grep "UnifiedReasoningAgent.*Parsing"
# Look for "Direct response (no tools)" when tools expected
```

### Ollama Connection Issues
```bash
# Verify server is accessible from device
adb shell ping 10.10.20.19
curl http://10.10.20.19:11434/api/tags
```

### Personality/Model Issues
```bash
# Check personality loading
adb logcat | grep "Personality.*loaded"
# Verify default personality exists in database
```

## Test Cases for Web Scraping

### Basic Web Requests
1. "What's on Hacker News?" → Should use WebFetchTool + ExtractorTool
2. "Check the weather on weather.com" → Tool chain execution  
3. "Summarize the latest news" → WebFetch → Extract → Summarize chain

### Expected JSON Structure
```json
{
  "type": "tools",
  "reasoning": "Need to fetch web content...",
  "tools": [
    {
      "name": "WebFetchTool",
      "args": {"url": "https://example.com"},
      "reasoning": "Fetch the webpage"
    },
    {
      "name": "ExtractorTool", 
      "args": {"html": ""},
      "reasoning": "Extract readable text"
    }
  ]
}
```

## Settings Page Testing (Future)

### Current Status
- Settings page exists but needs Unity integration
- Missing: Per-personality model selection
- Missing: User-controllable system prompts  
- Missing: Unity mode toggle

### Future Test Cases
- [ ] Enable/disable Unity mode
- [ ] Select Jan model per personality
- [ ] Edit system prompts
- [ ] Module management per personality

---

*This document should be updated as Unity architecture evolves and new features are added.*