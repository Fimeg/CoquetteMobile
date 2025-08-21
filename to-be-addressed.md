# To Be Addressed - CoquetteMobile Development Notes

## Recent UI & UX Improvements (Session: August 20, 2025)

### 🎯 Critical Tool Integration Fixes

#### **Double Summarization Issue - FIXED**
- **Problem**: Tool chain was auto-summarizing content, then personality received pre-digested summaries instead of raw data
- **Root Cause**: `SummarizerTool` was automatically added to every tool chain (WebFetch → Extract → Summarize)
- **Solution**: Modified `ChatViewModel.handleToolExecution()` to skip auto-summarization and let personality handle raw content
- **Code Changes**: 
  - Modified tool context message to include raw content with clear instructions
  - Added break condition for `SummarizerTool` in followup chain
  - Enhanced context prompt: *"You have the raw content above. Please analyze, synthesize, and respond to the user's request using your personality and expertise. Do not just acknowledge that tools were run - actually process and present the information in a natural, helpful way."*

#### **Thinking Section UI Enhancement - IMPLEMENTED**
- **Problem**: Thinking section auto-expanded and couldn't be minimized during streaming
- **Solution**: 
  - Removed auto-expand behavior (`LaunchedEffect` that forced expansion)
  - Added preview text when collapsed (first 50 characters + "...")
  - Made thinking collapsible at any time, even during active streaming
- **Files Modified**: `ChatScreen.kt` - `ChatMessageBubble` component
- **UI Impact**: Better screen real estate management, user control over thinking visibility

### 🧠 SubconsciousReasoner & Complex Planning Enhancement

#### **Enhanced Task Detection - IMPLEMENTED**
- **Problem**: Complex task detection was too restrictive, missing web requests
- **Solution**: 
  - Added web-related keywords: "check", "look up", "find out", "get", "fetch", "latest", "news"
  - Added URL detection: "http", "www", ".com", ".org", "website", "site", "url"
  - Lowered word count threshold: 15+ words → 10+ words
- **Code**: `ChatViewModel.detectComplexTask()`

#### **SubconsciousReasoner Integration - ENHANCED**
- **New Features**: 
  - Added `planComplexTask()` method for sophisticated multi-step planning
  - Created `TaskPlan` and `TaskStep` data classes
  - Integrated with split-brain flow to use SubconsciousReasoner for complex requests
  - Added intelligent model selection for planning (prefers DeepSeek-R1 32B → Qwen3 30B → other DeepSeek models)
- **Planning Capabilities**:
  - Multi-source research tasks
  - Content comparison across sites  
  - Comprehensive analysis with dependency tracking
  - Automatic URL inference for research tasks

#### **Enhanced PlannerService Prompts - UPDATED**
- **Added sophisticated examples**:
  - Multi-source news comparison (TechCrunch + Ars Technica)
  - Comprehensive AI research across multiple sources
  - Strategic tool chaining for complex analysis
- **Enhanced rules**: Better guidance for multi-step operations and research tasks

### 🔧 Tool Execution & Processing State Improvements

#### **Specific Tool UI Feedback - IMPLEMENTED**
- **Problem**: Generic "Running tools..." message wasn't informative
- **Solution**: 
  - Changed `ProcessingState` from enum to sealed class
  - Added `data class Tools(val toolName: String)` for specific tool names
  - Updated UI to show "Running WebFetchTool...", "Running SummarizerTool...", etc.
- **Files**: `ChatViewModel.kt`, `ChatScreen.kt`

#### **SummarizerTool Model Configuration - IMPLEMENTED** 
- **Problem**: SummarizerTool hardcoded to use DeepSeek-R1 32B (slow)
- **Solution**: 
  - Updated to use `appPreferences.personalityModel` for summarization
  - Allows user to select faster 8B models for speed vs quality tradeoff
  - Added comprehensive logging for model selection and execution time
- **Files**: `SummarizerTool.kt`

#### **Extended Tool Timeout - IMPLEMENTED**
- **Problem**: 6-minute timeout caused failures with large models
- **Solution**: Increased `OkHttpClient` timeout to 10 minutes for tool operations
- **Files**: `OllamaService.kt`

### 📊 Comprehensive Logging System - IMPLEMENTED

#### **CoquetteLogger Implementation**
- **Features**:
  - Persistent file-based logging with daily rotation
  - 7-day automatic cleanup
  - Writes to both Android logcat AND files
  - Timestamped entries with log levels (DEBUG, INFO, WARNING, ERROR)
- **Location**: `/data/data/com.yourname.coquettemobile/files/logs/coquette-YYYY-MM-DD.log`
- **Settings Integration**: "Clear Debug Logs" option in Settings → Debug & Logs

#### **Enhanced Tool Logging**
- **Updated Components**: `OllamaService`, `WebFetchTool`, `SummarizerTool`
- **Log Details**: 
  - Request/response bodies for debugging
  - Model selection reasoning
  - Tool execution timings
  - Error conditions with context
  - Tool chain progression with intermediate results

### 🔄 Split-Brain Architecture Enhancements

#### **Dual Server Model Loading - ENHANCED**
- **Problem**: Planner model dropdown showed wrong server models
- **Solution**: 
  - Fixed `loadAvailablePlannerModels()` to query tool server
  - Added proper fallback model names for tool server compatibility
  - Enhanced network security config for tool server cleartext communication
- **Network Security**: Added `10.10.20.120` (tool server) to cleartext permitted domains

#### **Complex Task Flow Integration**
- **New Flow**: 
  1. `detectComplexTask()` analyzes user request
  2. If complex + subconscious reasoning enabled → Use `SubconsciousReasoner.planComplexTask()`
  3. Convert `TaskPlan` to `PlannerDecision` for execution
  4. Execute tool chain with enhanced context for personality
  5. Skip auto-summarization, let personality handle raw content

## 🚨 Outstanding Issues & Technical Debt

### Performance & Model Management
- **Model Selection Intelligence**: Need smarter automatic model routing based on task complexity
- **Caching Strategy**: Tool results and model responses could benefit from intelligent caching
- **Memory Management**: Large model responses may impact mobile performance
- **Battery Optimization**: Tool chains with multiple models may drain battery quickly

### User Experience Refinements  
- **Progress Indicators**: Tool execution could show progress bars for long operations
- **Tool Result Preview**: Allow users to see tool results before personality processing
- **Conversation Context**: Better handling of tool context in conversation history
- **Error Recovery**: More graceful handling of tool failures with user-friendly messages

### Architecture Considerations
- **Tool Dependency Management**: Some tools depend on others; need better dependency resolution
- **Parallel Tool Execution**: Some operations could run in parallel for better performance
- **Tool Result Persistence**: Consider caching tool results for similar requests
- **Model Warm-up**: Pre-loading frequently used models to reduce latency

### Security & Privacy
- **URL Validation**: Enhanced validation for user-provided URLs in WebFetchTool
- **Content Filtering**: Better filtering of potentially harmful content from web sources
- **API Rate Limiting**: Implement rate limiting for external API calls
- **Data Sanitization**: Ensure all tool outputs are properly sanitized before personality processing

## 🎯 Next Priority Items

### Immediate (Next Session)
1. **Test Complex Task Flow**: Verify SubconsciousReasoner integration with web requests
2. **Verify Tool Chain Logging**: Ensure all tool executions are properly logged
3. **Performance Testing**: Test tool timeouts and model selection with various request types

### Short Term (Next Few Sessions)
1. **Tool Result Caching**: Implement intelligent caching for repeated requests
2. **Enhanced Error Handling**: Better recovery from tool failures
3. **Tool Execution Analytics**: Track success rates and performance metrics
4. **User Preference Learning**: Learn from user preferences for tool vs personality responses

### Medium Term (Future Development)
1. **Advanced Tool Composition**: Allow tools to call other tools dynamically
2. **Custom Tool Development**: Framework for users to create custom tools
3. **Tool Marketplace**: Shared tool ecosystem for CoquetteMobile users
4. **Cross-Session Learning**: Remember successful tool patterns across conversations

## 📝 Code Quality Notes

### Refactoring Opportunities
- **Tool Execution Logic**: `ChatViewModel.handleToolExecution()` is getting complex, consider extraction
- **Processing State Management**: Could benefit from state machine pattern
- **Prompt Template System**: Centralize and templatize all AI prompts
- **Configuration Management**: Better centralization of tool and model configurations

### Testing Considerations
- **Tool Chain Testing**: Need automated tests for various tool chain combinations
- **Model Response Testing**: Mock model responses for consistent testing
- **UI State Testing**: Test processing state transitions and UI updates
- **Integration Testing**: End-to-end testing of split-brain flow with tools

## 🔍 Monitoring & Metrics

### Key Metrics to Track
- **Tool Success Rates**: Track success/failure rates by tool type
- **Response Times**: Monitor tool execution and model inference times  
- **User Satisfaction**: Track user interactions with tool results vs personality responses
- **Resource Usage**: Monitor battery, memory, and network usage during tool operations
- **Model Performance**: Track which models work best for different task types

### Logging Enhancements Needed
- **Structured Logging**: Move to JSON-structured logs for better analysis
- **Performance Metrics**: Add timing data to all operations
- **User Journey Tracking**: Log complete request-to-response flows
- **Error Context**: Enhanced error logging with full context and recovery suggestions

---

*Last Updated: August 20, 2025*
*Session Focus: Tool Integration, UI Enhancements, SubconsciousReasoner Implementation*