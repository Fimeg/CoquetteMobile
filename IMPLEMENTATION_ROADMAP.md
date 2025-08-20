# 🚀 CoquetteMobile Implementation Roadmap - Next Session Continuity

## **Current State Summary**
✅ **Split-brain architecture** with user-editable system prompts  
✅ **Personality integration fix** - combines system + database personalities  
✅ **Smart conversation management** - no blank accumulation  
✅ **Background service** for connection persistence  
✅ **Module registry foundation** with 4 modules defined  

⚠️ **CRITICAL: User has 11K personality file and needs module management ASAP**

## **Module Status - PARTIALLY IMPLEMENTED**
**Modules exist but NO UI to activate them:**
- ✅ **Therapy Module** - defined in ModuleRegistry.kt
- ✅ **Activist Module** - defined in ModuleRegistry.kt  
- ✅ **Story Module** - defined in ModuleRegistry.kt
- ✅ **Erotica Module** - defined in ModuleRegistry.kt
- ❌ **No UI to toggle modules on/off**
- ❌ **User stuck with large 11K personality file**

**ChatViewModel has methods but no UI:**
```kotlin
fun getActiveModules() = promptStateManager.activeModules
fun toggleModule(moduleName: String)  
fun getAvailableModules() = moduleRegistry.getAvailableModules()
```

## **IMMEDIATE PRIORITY (Start Here Next Session)**

### **1. Module Management UI - URGENT** 🔥
User needs to break down 11K personality into manageable modules

**CRITICAL: Personality-specific module availability**
- Ani: Therapy, Activist, Story, Erotica ✅
- Marvin (tech wizard/luddite): Therapy, Tech, Grumpy ✅ 
- Corporate Bot: Professional, Meeting, Analytics ✅
- **NO flirty modules for inappropriate personalities!**

**Implementation:**
```kotlin
// Personality entity needs allowedModules field
data class Personality(
    val allowedModules: List<String> = listOf("Therapy", "Activist", "Story")
)

// UI shows only allowed modules per personality
fun getAvailableModulesForPersonality(personalityId: String): List<String>
```

**Flow:**
1. Select personality → UI shows ONLY their allowed modules
2. Toggle personality-appropriate modules on/off
3. Show active modules as badges in chat header
4. Module availability changes with personality switch

### **2. WebFetchTool Implementation** 🌐  
Desktop Coquette patterns ready to adapt

**Files to create:**
- `WebFetchTool.kt` - HTTP fetching with privacy controls
- `ExtractorTool.kt` - HTML to readable text (using Jsoup)
- `SummarizerTool.kt` - Long content compression

**Safety patterns from desktop:**
- User approval for external requests
- Content length limits (100KB)
- Private IP blocking
- URL validation

### **3. DeviceContextTool Truncation Fix** 📱
Check ChatScreen message display - likely UI limits not tool limits

## **Desktop Coquette Resources Available**
**Key files analyzed:**
- `/tools/web-fetch.ts` - Complete HTTP fetching with safety
- `/agents/NewToolsAgent.ts` - Clean tool orchestration  
- `/WorkflowPatterns.ts` - 5-step workflow automation
- `/IntelligenceRouter.ts` - AI-driven tool routing

## **Implementation Decisions Needed**

### **Web Tool Permissions:**
- [ ] User approval for each fetch vs whitelist domains?
- [ ] Auto-chain fetch→extract→summarize or ask user?
- [ ] Content limits: 100KB like desktop or mobile-specific?

### **Module Strategy:**
- [ ] Move user's 11K personality into which modules?
- [ ] **Personality-specific module sets** (no Erotica for Marvin!)
- [ ] Auto-activate modules based on conversation context?
- [ ] Module timeout (deactivate after N turns)?
- [ ] **Database migration**: Add allowedModules to Personality table

## **Next Session Action Plan**

### **Phase 1: Module UI (30 min)**
1. Create ModuleManagementScreen.kt
2. Add module toggles to ChatScreen header
3. Test personality switching in real-time

### **Phase 2: WebFetch (60 min)**  
1. Adapt desktop WebFetchTool patterns
2. Add HTML extraction with Jsoup
3. Test "What's on Hacker News?" workflow

### **Phase 3: Tool Chaining (30 min)**
1. Enhanced PlannerService for multi-step tools
2. Progress indicators for tool chains
3. Test fetch→extract→summarize flow

## **Critical Files Modified**
- `ChatViewModel.kt` - Split-brain + personality integration
- `SystemPromptManager.kt` - User-editable prompts  
- `PromptStateManager.kt` - Module assembly
- `ModuleRegistry.kt` - 4 personality modules defined

## **User Pain Points to Address**
1. **11K personality file management** - needs module breakdown
2. **DeviceContextTool truncation** - UI display limits
3. **No web content access** - needs WebFetch implementation
4. **Module activation** - no UI currently exists

**GitHub Branch:** `split-brain-architecture`  
**Last Commit:** Fix personality integration and conversation management

---
*Ready to transform CoquetteMobile into desktop Coquette's mobile successor!* 🚀