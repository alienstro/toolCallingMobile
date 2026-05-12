package com.lance.litertchat.prompt

object ChatPromptTemplate {
    fun format(systemPrompt: String?, userPrompt: String): String {
        val cleanedSystem = systemPrompt.orEmpty().trim()
        val cleanedUser = userPrompt.trim()

        return buildString {
            if (cleanedSystem.isNotBlank()) {
                append("<|im_start|>system\n")
                append(cleanedSystem)
                append("\n<|im_end|>\n")
            }
            append("<|im_start|>user\n")
            append(cleanedUser)
            append("\n<|im_end|>\n")
            append("<|im_start|>assistant")
        }
    }
}
