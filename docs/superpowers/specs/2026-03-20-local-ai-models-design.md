# Local AI Models Integration Design

## Overview

Add mobile-friendly, mobile-runnable local AI models (like Qwen 2.5, Llama 3.2) to the AI providers dropdown in the Configuration page using the Runanywhere Kotlin SDK. This implementation shall not affect the APK building process.

## Goals

1. Add new API provider type for Runanywhere local AI models
2. Integrate Runanywhere Kotlin SDK as a Gradle dependency
3. Create provider implementation following existing patterns (MNN, LLAMA_CPP)
4. Add model selection UI in Configuration page
5. Support model download and initialization

## Architecture

### Package Structure

```
app/src/main/java/com/ai/assistance/operit/
├── api/chat/llmprovider/
│   └── RunanywhereProvider.kt     # New - AI provider implementation
├── data/model/
│   └── ModelConfigData.kt         # Modify - Add RUNANYWHERE to ApiProviderType
└── ui/features/settings/sections/
    └── ModelApiSettingsSection.kt # Modify - Add RUNANYWHERE handling
```

### Dependencies (build.gradle.kts)

```kotlin
// Add to dependencies
implementation("ai.runanywhere:runanywhere-llamacpp-android:1.0.0")
implementation("ai.runanywhere:runanywhere-onnx-android:1.0.0")
```

## Implementation Details

### 1. ApiProviderType Enum Addition

Add new enum value:
```kotlin
RUNANYWHERE, // Runanywhere本地AI模型
```

### 2. RunanywhereProvider Implementation

Create `RunanywhereProvider.kt` following the pattern of existing providers:

- **Model Discovery**: Use `RunAnywhere.getModels()` to list available models
- **Model Download**: Use `RunAnywhere.downloadModel()` with progress tracking
- **Model Loading**: Use `RunAnywhere.loadLLMModel()` 
- **Text Generation**: Use `RunAnywhere.generateCompletion()` with streaming support
- **Resource Management**: Proper `unload()` when done

Supported models to display:
- Qwen 2.5 0.5B
- Llama 3.2 1B
- Mistral 7B Q4
- SmolLM2 360M

### 3. Configuration Parameters

Add to `ModelConfigData`:
```kotlin
val runanywhereModelSlug: String = "", // Model identifier
val runanywhereThreadCount: Int = 4,   // Inference threads
val runanywhereContextSize: Int = 4096  // Context window
```

### 4. UI Updates

In `ModelApiSettingsSection.kt`:
- Add `RUNANYWHERE` to provider dropdown
- Add model selection UI (dropdown with available models)
- Add thread count and context size settings
- Add download progress indicator

### 5. AIServiceFactory Integration

Add case in `AIServiceFactory.kt`:
```kotlin
ApiProviderType.RUNANYWHERE -> RunanywhereProvider(...)
```

## Data Flow

1. User selects "Runanywhere" from provider dropdown
2. App fetches available models from Runanywhere SDK
3. User selects desired model (e.g., "Qwen 2.5 0.5B")
4. If not downloaded, user initiates download with progress UI
5. User configures thread count and context size
6. On save, model is loaded and ready for inference
7. Chat uses RunanywhereProvider for text generation

## Error Handling

- Network errors during model download: Show retry option
- Insufficient storage: Display warning with required space
- Model load failure: Show error with suggested solutions
- Out of memory: Suggest reducing context size or thread count

## Testing

- Unit tests for RunanywhereProvider
- Instrumented tests for model download/load
- UI tests for Configuration screen

## Non-Functional Requirements

- **APK Build**: Must NOT require additional native compilation
- **Performance**: Target <3s for first token on Qwen 2.5 0.5B
- **Memory**: <2GB RAM for 0.5B models, <4GB for 1B models
- **Compatibility**: Android API 24+ (matching existing MNN/LLAMA_CPP)

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| SDK version instability | Pin to specific version, test thoroughly |
| Large download sizes | Show size before download, allow WiFi-only |
| Memory pressure | Default to smaller models, provide guidance |
| ABI compatibility | Support ARM64-v8a, document requirements |

## Timeline

- Design Approval: 1 day
- Implementation: 2-3 days
- Testing: 1 day
- Total: 4-5 days
