package dev.quinton.goa.core.presentation

/**
 * Screen-state builders shared by the Android ViewModels and the hub web app (ADR-030 §1): each
 * number a screen shows is computed here once, so the phone and the browser can never disagree.
 * One package per feature (`players`, `catalog`, `timer`, `draft`, `trivia`, `rules`, `group`).
 */
internal object PresentationModule
