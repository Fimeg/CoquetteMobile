# CoquetteMobile

A privacy-first Android AI assistant that runs entirely on your local infrastructure. Built to be better than Google Assistant because it's not farming your data.

## Features

- 🔒 **Complete Privacy**: All data stays on your device and local AI server
- 🧠 **Multi-Model Support**: Works with any Ollama-compatible models 
- 💭 **Real-time Thinking**: See AI reasoning process with `<think>` tag parsing
- 📱 **Mobile Tools**: Device context, battery, storage, network diagnostics
- 💬 **Conversation History**: AI-generated titles with persistent chat history
- 🎭 **Custom Personalities**: Create and manage your own AI personalities
- 🌊 **Streaming Responses**: Real-time response generation with toggle
- 🔄 **Background Persistence**: Maintains connections when app is backgrounded

## Prerequisites

- Rooted Android device (for installation via ADB)
- Local Ollama server running on your network
- Models: `deepseek-r1:32b`, `qwen3:8b`, `gemma3n:e4b` (or similar)

## Setup

1. **Configure your Ollama server URL**:
   Edit `app/src/main/java/com/yourname/coquettemobile/core/ai/OllamaService.kt`:
   ```kotlin
   private val baseUrl = "http://YOUR_SERVER_IP:11434"
   ```

2. **Build the APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on your device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Architecture

- **Room Database**: Local storage for conversations, messages, and personalities
- **Dagger Hilt**: Dependency injection
- **Jetpack Compose**: Modern Android UI with Material Design 3
- **Kotlin Coroutines**: Reactive programming with StateFlow
- **OkHttp**: Network layer with retry logic and connection persistence
- **Tool System**: Extensible mobile tools architecture

## Privacy

- All conversations and personalities stored locally in Room database
- No telemetry, analytics, or data collection
- Direct connection to your local AI infrastructure
- Background service maintains connection without data exposure

## Development

Built with Android Studio and Kotlin. Uses modern Android development practices with:
- Jetpack Compose for UI
- Material Design 3 theming
- Coroutines for async operations
- Hilt for dependency injection
- Room for local database

## License

Open source - build your own privacy-first AI assistant.