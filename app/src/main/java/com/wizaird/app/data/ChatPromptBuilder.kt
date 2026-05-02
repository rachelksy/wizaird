package com.wizaird.app.data

/**
 * Builds system prompts for chat conversations.
 */

/**
 * Build the system prompt for chat responses.
 * Includes project context to help the AI understand what the user is learning.
 */
fun buildChatSystemPrompt(project: Project): String {
    println("=== BUILDING CHAT SYSTEM PROMPT ===")
    println("Project name: ${project.name}")
    println("Project instructions: ${project.instructions}")
    println("Project background length: ${project.background.length}")
    println("Project learning progress length: ${project.learningProgress.length}")
    
    val sb = StringBuilder()
    
    sb.append("You are helping the user understand: ${project.name}.\n")
    sb.append("Their goal: ${project.instructions}.\n\n")
    
    if (project.background.isNotBlank()) {
        sb.append("What they already know:\n${project.background}\n\n")
    }
    
    if (project.learningProgress.isNotBlank()) {
        sb.append("Their learning progress:\n${project.learningProgress}\n\n")
    }
    
    sb.append("Your task: Answer their questions clearly and help deepen their understanding.\n")
    sb.append("- Be conversational but substantive\n")
    sb.append("- Connect new information to what they already know\n")
    sb.append("- Use examples when helpful\n")
    sb.append("- Focus on what the user is asking\n\n")
    sb.append("Keep responses focused and digestible (typically 100-300 words).")
    
    val result = sb.toString()
    println("Built prompt length: ${result.length} chars")
    println("=== CHAT SYSTEM PROMPT BUILD COMPLETE ===")
    
    return result
}
