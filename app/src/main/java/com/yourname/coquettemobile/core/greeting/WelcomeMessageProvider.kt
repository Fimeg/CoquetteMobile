package com.yourname.coquettemobile.core.greeting

import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WelcomeMessageProvider @Inject constructor() {
    
    private val morningGreetings = listOf(
        "Good morning! What's brewing in your mind today?",
        "Morning! Ready to tackle whatever's on your mind?",
        "Hey there! How can I help you start your day?",
        "Good morning! What would you like to explore today?",
        "Rise and shine! What's on your agenda?",
        "Morning! I'm here when you need me.",
        "Good morning! What's got your attention today?",
        "Hey! What's the first thing on your mind?",
        "Morning! Ready to dive into something interesting?",
        "Good morning! What can we figure out together?",
        "Hello! How's your morning going?",
        "Morning! What's sparking your curiosity today?"
    )
    
    private val afternoonGreetings = listOf(
        "Good afternoon! What's on your mind?",
        "Hey there! How's your day going?",
        "Afternoon! What can I help you with?",
        "Hello! What would you like to explore?",
        "Good afternoon! Ready for a chat?",
        "Hey! What's keeping you busy today?",
        "Afternoon! What's got your interest?",
        "Hello there! What's on your agenda?",
        "Good afternoon! How can I assist?",
        "Hey! What's sparking your curiosity?",
        "Afternoon! What would you like to know?",
        "Hello! What's rolling around in your head?"
    )
    
    private val eveningGreetings = listOf(
        "Good evening! How was your day?",
        "Evening! What's on your mind tonight?",
        "Hey there! Winding down or diving deep?",
        "Good evening! What can I help you with?",
        "Evening! Ready to explore something?",
        "Hello! How's your evening going?",
        "Good evening! What's got your attention?",
        "Hey! What would you like to chat about?",
        "Evening! What's sparking your interest?",
        "Good evening! What can we figure out?",
        "Hello there! What's on your mind tonight?",
        "Evening! Ready for a thoughtful conversation?"
    )
    
    private val lateNightGreetings = listOf(
        "Up late? What's on your mind?",
        "Hey night owl! What's keeping you up?",
        "Late night thoughts? I'm here to help.",
        "Hello there! What's got you thinking?",
        "Hey! What's brewing in your mind tonight?",
        "Late night curiosity? Let's explore it.",
        "Hello! What would you like to dive into?",
        "Hey there! What's sparking your interest?",
        "Up late thinking? What about?",
        "Hello night owl! What can I help with?",
        "Hey! What's rolling around in your head?",
        "Late night question? I'm all ears."
    )
    
    fun getWelcomeMessage(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val greetings = when (hour) {
            in 5..11 -> morningGreetings
            in 12..17 -> afternoonGreetings  
            in 18..22 -> eveningGreetings
            else -> lateNightGreetings // 23-4 (late night/early morning)
        }
        
        return greetings.random()
    }
    
    fun shouldShowWelcome(): Boolean {
        // Could add logic here for when to show welcome
        // For now, always show on empty chat
        return true
    }
}