package com.yourname.coquettemobile.utils

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

data class ParsedResponse(
    val content: String,
    val thinkingSteps: List<String>
)

/**
 * Represents a node in the nested thinking structure.
 * @param content The text content of this thinking step.
 * @param children A list of child nodes for nested thoughts.
 */
data class ThinkingNode(
    val content: String,
    val children: List<ThinkingNode>
)

object ThinkingParser {
    
    /**
     * Original flat parser. Kept for compatibility if needed elsewhere.
     */
    fun parseThinkingTags(response: String): ParsedResponse {
        val thinkingRegex = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        // Extract thinking content
        val thinkingMatches = thinkingRegex.findAll(response)
        val thinkingSteps = thinkingMatches.map { it.groupValues[1].trim() }.toList()
        
        // More robust cleaning - remove all thinking tags and content
        var cleanContent = response
        
        // Remove all <think>...</think> blocks (handles multiple blocks)
        cleanContent = cleanContent.replace(thinkingRegex, "")
        
        // Remove any orphaned opening or closing think tags
        cleanContent = cleanContent.replace("<think>", "")
        cleanContent = cleanContent.replace("</think>", "")
        
        // Clean up whitespace
        cleanContent = cleanContent
            .trim()
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex("^\\s*\n+"), "") // Remove leading newlines
            .replace(Regex("\n+\\s*$"), "") // Remove trailing newlines
        
        // Enhanced validation - if clean content is suspiciously similar to original,
        // it might mean thinking tags weren't properly removed
        val removalRatio = (response.length - cleanContent.length).toDouble() / response.length
        val hasThinkingTags = response.contains("<think>") || response.contains("</think>")
        
        return ParsedResponse(
            content = if (cleanContent.isNotEmpty() && (!hasThinkingTags || removalRatio > 0.1)) {
                cleanContent
            } else {
                response // Fallback only if cleaning genuinely failed
            },
            thinkingSteps = thinkingSteps
        )
    }

    /**
     * Parses a string with potentially nested <think> tags into a tree of ThinkingNode objects.
     * This robustly handles nested and sibling thoughts.
     */
    fun parseNestedThinking(xmlString: String?): ThinkingNode? {
        if (xmlString.isNullOrBlank()) return null

        // Wrap the content in a root tag to ensure valid XML structure for the parser.
        val wrappedXml = "<root>$xmlString</root>"
        
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(wrappedXml))
            
            // Move to the root element
            parser.nextTag()
            parseChildren(parser, "root")
        } catch (e: Exception) {
            // If XML parsing fails (e.g., malformed tags), fall back to a simple node.
            ThinkingNode(xmlString.replace("<think>", "").replace("</think>", ""), emptyList())
        }
    }

    private fun parseChildren(parser: XmlPullParser, parentTag: String): ThinkingNode {
        val children = mutableListOf<ThinkingNode>()
        val contentBuilder = StringBuilder()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT && !(eventType == XmlPullParser.END_TAG && parser.name == parentTag)) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "think") {
                        // Add any preceding text as a content node
                        if (contentBuilder.toString().trim().isNotEmpty()) {
                            children.add(ThinkingNode(contentBuilder.toString().trim(), emptyList()))
                            contentBuilder.clear()
                        }
                        // Recursively parse the children of this <think> tag
                        children.add(parseChildren(parser, "think"))
                    }
                }
                XmlPullParser.TEXT -> {
                    contentBuilder.append(parser.text)
                }
            }
            eventType = parser.next()
        }
        
        // Add any remaining text content
        if (contentBuilder.toString().trim().isNotEmpty()) {
            children.add(ThinkingNode(contentBuilder.toString().trim(), emptyList()))
        }
        
        // For the root, we aggregate children's content. For <think> tags, we create a distinct node.
        return if (parentTag == "root") {
            ThinkingNode(children.joinToString("\n") { it.content }.trim(), children)
        } else {
            val aggregatedContent = children.joinToString("\n") { it.content }.trim()
            ThinkingNode(aggregatedContent, children.flatMap { it.children })
        }
    }
}