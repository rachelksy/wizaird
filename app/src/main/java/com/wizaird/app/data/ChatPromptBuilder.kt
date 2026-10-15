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
    sb.append("Goal: ${project.instructions}.\n\n")
    
    if (project.background.isNotBlank()) {
        sb.append("What the user already knows:\n${project.background}\n\n")
    }
    
    if (project.learningProgress.isNotBlank()) {
        sb.append("What has been addressed so far in this project (what angles were taken, what threads remain):\n${project.learningProgress}\n\n")
    }
    
    sb.append("Answer the question clearly and directly.\n")
    sb.append("- Be conversational but substantive\n")
    sb.append("- Connect new information to what the user already knows\n")
    sb.append("- Use examples when helpful\n")
    sb.append("- Focus on what the user is asking\n")
    sb.append("- Use the learning progress to understand what has already been covered — not to limit what you say, but to build logically from where things left off. Topics that appear in the progress may still be worth going further on if a different angle or deeper layer is relevant to the question\n\n")
    sb.append("Keep responses focused and digestible (typically 100-300 words).")
    
    val result = sb.toString()
    println("Built prompt length: ${result.length} chars")
    println("=== CHAT SYSTEM PROMPT BUILD COMPLETE ===")
    
    return result
}
