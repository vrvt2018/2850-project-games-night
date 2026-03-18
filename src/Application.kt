package com.example

import io.ktor.server.application.*
import io.ktor.server.pebble.Pebble
import io.pebbletemplates.pebble.loader.ClasspathLoader

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureRouting()
    configureTemplates()
}

fun Application.configureTemplates() {
    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates" // templates stored in resources/templates
        })
    }
}